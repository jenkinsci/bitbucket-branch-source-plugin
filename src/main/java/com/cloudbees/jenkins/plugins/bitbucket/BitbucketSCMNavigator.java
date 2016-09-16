/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.jenkins.plugins.bitbucket;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.CheckForNull;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketTeam;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.UserRoleInRepository;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMNavigatorDescriptor;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.SCMSourceOwner;

public class BitbucketSCMNavigator extends SCMNavigator {

    private final String repoOwner;
    private final String credentialsId;
    private final String checkoutCredentialsId;
    private String pattern = ".*";
    private boolean autoRegisterHooks = false;
    private String bitbucketServerUrl;
    private int sshPort = -1;

    /**
     * Bitbucket API client connector.
     */
    private transient BitbucketApiConnector bitbucketConnector;

    @DataBoundConstructor 
    public BitbucketSCMNavigator(String repoOwner, String credentialsId, String checkoutCredentialsId) {
        this.repoOwner = repoOwner;
        this.credentialsId = Util.fixEmpty(credentialsId);
        this.checkoutCredentialsId = checkoutCredentialsId;
    }

    @DataBoundSetter 
    public void setPattern(String pattern) {
        Pattern.compile(pattern);
        this.pattern = pattern;
    }

    @DataBoundSetter
    public void setAutoRegisterHooks(boolean autoRegisterHooks) {
        this.autoRegisterHooks = autoRegisterHooks;
    }

    public String getRepoOwner() {
        return repoOwner;
    }

    @CheckForNull
    public String getCredentialsId() {
        return credentialsId;
    }

    @CheckForNull
    public String getCheckoutCredentialsId() {
        return checkoutCredentialsId;
    }

    public String getPattern() {
        return pattern;
    }

    public boolean isAutoRegisterHooks() {
        return autoRegisterHooks;
    }

    public int getSshPort() {
        return sshPort;
    }

    @DataBoundSetter
    public void setSshPort(int sshPort) {
        this.sshPort = sshPort;
    }

    @DataBoundSetter
    public void setBitbucketServerUrl(String url) {
        this.bitbucketServerUrl = Util.fixEmpty(url);
        if (this.bitbucketServerUrl != null) {
            // Remove a possible trailing slash
            this.bitbucketServerUrl = this.bitbucketServerUrl.replaceAll("/$", "");
        }
    }

    @CheckForNull
    public String getBitbucketServerUrl() {
        return bitbucketServerUrl;
    }

    public void setBitbucketConnector(@NonNull BitbucketApiConnector bitbucketConnector) {
        this.bitbucketConnector = bitbucketConnector;
    }

    /*package*/ BitbucketApiConnector getBitbucketConnector() {
        if (bitbucketConnector == null) {
            bitbucketConnector = new BitbucketApiConnector(bitbucketServerUrl);
        }
        return bitbucketConnector;
    }

    @Override
    public void visitSources(SCMSourceObserver observer) throws IOException, InterruptedException {
        TaskListener listener = observer.getListener();

        if (StringUtils.isBlank(repoOwner)) {
            listener.getLogger().format("Must specify a repository owner%n");
            return;
        }
        StandardUsernamePasswordCredentials credentials = getBitbucketConnector().lookupCredentials(observer.getContext(),
                credentialsId, StandardUsernamePasswordCredentials.class);

        if (credentials == null) {
            listener.getLogger().format("Connecting to %s with no credentials, anonymous access%n", bitbucketServerUrl == null ? "https://bitbucket.org" : bitbucketServerUrl);
        } else {
            listener.getLogger().format("Connecting to %s using %s%n", bitbucketServerUrl == null ? "https://bitbucket.org" : bitbucketServerUrl, CredentialsNameProvider.name(credentials));
        }
        List<? extends BitbucketRepository> repositories;
        BitbucketApi bitbucket = getBitbucketConnector().create(repoOwner, credentials);
        BitbucketTeam team = bitbucket.getTeam();
        if (team != null) {
            // Navigate repositories of the team
            listener.getLogger().format("Looking up repositories of team %s%n", repoOwner);
            repositories = bitbucket.getRepositories();
        } else {
            // Navigate the repositories of the repoOwner as a user
            listener.getLogger().format("Looking up repositories of user %s%n", repoOwner);
            repositories = bitbucket.getRepositories(UserRoleInRepository.OWNER);
        }
        for (BitbucketRepository repo : repositories) {
            add(listener, observer, repo);
        }
    }

    private void add(TaskListener listener, SCMSourceObserver observer, BitbucketRepository repo) throws InterruptedException {
        String name = repo.getRepositoryName();
        if (!Pattern.compile(pattern).matcher(name).matches()) {
            listener.getLogger().format("Ignoring %s%n", name);
            return;
        }
        listener.getLogger().format("Proposing %s%n", name);
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        SCMSourceObserver.ProjectObserver projectObserver = observer.observe(name);
        BitbucketSCMSource scmSource = new BitbucketSCMSource(null, repoOwner, name);
        scmSource.setBitbucketConnector(getBitbucketConnector());
        scmSource.setCredentialsId(credentialsId);
        scmSource.setCheckoutCredentialsId(checkoutCredentialsId);
        scmSource.setAutoRegisterHook(isAutoRegisterHooks());
        scmSource.setBitbucketServerUrl(bitbucketServerUrl);
        scmSource.setSshPort(sshPort);
        projectObserver.addSource(scmSource);
        projectObserver.complete();
    }

    @Extension 
    public static class DescriptorImpl extends SCMNavigatorDescriptor {

        public static final String ANONYMOUS = BitbucketSCMSource.DescriptorImpl.ANONYMOUS;
        public static final String SAME = BitbucketSCMSource.DescriptorImpl.SAME;

        @Override
        public String getDisplayName() {
            return Messages.BitbucketSCMNavigator_DisplayName();
        }

        @Override
        public String getDescription() {
            return Messages.BitbucketSCMNavigator_Description();
        }

        @Override
        public String getIconFilePathPattern() {
            return "plugin/cloudbees-bitbucket-branch-source/images/:size/bitbucket-scmnavigator.png";
        }

        @Override
        public SCMNavigator newInstance(String name) {
            return new BitbucketSCMNavigator(name, "", BitbucketSCMSource.DescriptorImpl.SAME);
        }

        public FormValidation doCheckCredentialsId(@QueryParameter String value) {
            if (!value.isEmpty()) {
                return FormValidation.ok();
            } else {
                return FormValidation.warning("Credentials are required for build notifications");
            }
        }

        public FormValidation doCheckBitbucketServerUrl(@QueryParameter String bitbucketServerUrl) {
            return BitbucketSCMSource.DescriptorImpl.doCheckBitbucketServerUrl(bitbucketServerUrl);
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath SCMSourceOwner context, @QueryParameter String bitbucketServerUrl) {
            StandardListBoxModel result = new StandardListBoxModel();
            result.withEmptySelection();
            new BitbucketApiConnector(bitbucketServerUrl).fillCredentials(result, context);
            return result;
        }

        public ListBoxModel doFillCheckoutCredentialsIdItems(@AncestorInPath SCMSourceOwner context, @QueryParameter String bitbucketServerUrl) {
            StandardListBoxModel result = new StandardListBoxModel();
            result.add("- same as scan credentials -", BitbucketSCMSource.DescriptorImpl.SAME);
            result.add("- anonymous -", BitbucketSCMSource.DescriptorImpl.ANONYMOUS);
            new BitbucketApiConnector(bitbucketServerUrl).fillCheckoutCredentials(result, context);
            return result;
        }

    }
}
