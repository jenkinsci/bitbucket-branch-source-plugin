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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import hudson.console.HyperlinkNote;
import hudson.model.Action;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.annotation.CheckForNull;

import jenkins.scm.api.SCMNavigatorEvent;
import jenkins.scm.api.SCMNavigatorOwner;
import jenkins.scm.api.SCMSourceCategory;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.impl.UncategorizedSCMSourceCategory;
import org.apache.commons.lang.StringUtils;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
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
        if (StringUtils.equals(this.bitbucketServerUrl, url)) {
            return;
        }
        this.bitbucketServerUrl = Util.fixEmpty(url);
        if (this.bitbucketServerUrl != null) {
            // Remove a possible trailing slash
            this.bitbucketServerUrl = this.bitbucketServerUrl.replaceAll("/$", "");
        }
        resetId();
    }

    @CheckForNull
    public String getBitbucketServerUrl() {
        return bitbucketServerUrl;
    }

    @NonNull
    @Override
    protected String id() {
        return bitbucketUrl() + "::" + repoOwner;
    }

    @Override
    public void visitSources(SCMSourceObserver observer) throws IOException, InterruptedException {
        TaskListener listener = observer.getListener();

        if (StringUtils.isBlank(repoOwner)) {
            listener.getLogger().format("Must specify a repository owner%n");
            return;
        }
        StandardUsernamePasswordCredentials credentials = BitbucketCredentials.lookupCredentials(
                bitbucketServerUrl,
                observer.getContext(),
                credentialsId,
                StandardUsernamePasswordCredentials.class
        );

        if (credentials == null) {
            listener.getLogger().format("Connecting to %s with no credentials, anonymous access%n", bitbucketUrl());
        } else {
            listener.getLogger().format("Connecting to %s using %s%n", bitbucketUrl(), CredentialsNameProvider.name(credentials));
        }
        List<? extends BitbucketRepository> repositories;
        BitbucketApi bitbucket = BitbucketApiFactory.newInstance(bitbucketServerUrl, credentials, repoOwner, null);
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
            checkInterrupt();
            add(listener, observer, repo);
        }
    }

    private void add(TaskListener listener, SCMSourceObserver observer, BitbucketRepository repo)
            throws InterruptedException, IOException {
        String name = repo.getRepositoryName();
        if (!Pattern.compile(pattern).matcher(name).matches()) {
            listener.getLogger().format("Ignoring %s%n", name);
            return;
        }
        listener.getLogger().format("Proposing %s%n", name);
        checkInterrupt();
        SCMSourceObserver.ProjectObserver projectObserver = observer.observe(name);
        BitbucketSCMSource scmSource = new BitbucketSCMSource(
                getId() + "::" + name,
                repoOwner,
                name
        );
        scmSource.setCredentialsId(credentialsId);
        scmSource.setCheckoutCredentialsId(checkoutCredentialsId);
        scmSource.setAutoRegisterHook(isAutoRegisterHooks());
        scmSource.setBitbucketServerUrl(bitbucketServerUrl);
        scmSource.setSshPort(sshPort);
        projectObserver.addSource(scmSource);
        projectObserver.complete();
    }

    private String bitbucketUrl() {
        return StringUtils.defaultIfBlank(bitbucketServerUrl, "https://bitbucket.org");
    }

    @NonNull
    @Override
    public List<Action> retrieveActions(@NonNull SCMNavigatorOwner owner,
                                        @CheckForNull SCMNavigatorEvent event,
                                        @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        // TODO when we have support for trusted events, use the details from event if event was from trusted source
        listener.getLogger().printf("Looking up team details of %s...%n", getRepoOwner());
        List<Action> result = new ArrayList<>();
        StandardUsernamePasswordCredentials credentials = BitbucketCredentials.lookupCredentials(
                bitbucketServerUrl,
                owner,
                credentialsId,
                StandardUsernamePasswordCredentials.class
        );

        String serverUrl = StringUtils.removeEnd(bitbucketUrl(), "/");
        if (credentials == null) {
            listener.getLogger().format("Connecting to %s with no credentials, anonymous access%n",
                    serverUrl);
        } else {
            listener.getLogger().format("Connecting to %s using %s%n",
                    serverUrl,
                    CredentialsNameProvider.name(credentials));
        }
        BitbucketApi bitbucket = BitbucketApiFactory.newInstance(bitbucketServerUrl, credentials, repoOwner, null);
        BitbucketTeam team = bitbucket.getTeam();
        if (team != null) {
            String teamUrl =
                    StringUtils.defaultIfBlank(getLink(team.getLinks(), "html"), serverUrl + "/" + team.getName());
            String teamDisplayName = StringUtils.defaultIfBlank(team.getDisplayName(), team.getName());
            result.add(new ObjectMetadataAction(
                    teamDisplayName,
                    null,
                    teamUrl
            ));
            result.add(new BitbucketTeamMetadataAction(getLink(team.getLinks(), "avatar")));
            result.add(new BitbucketLink("icon-bitbucket-logo", teamUrl));
            listener.getLogger().printf("Team: %s%n", HyperlinkNote.encodeTo(teamUrl, teamDisplayName));
        } else {
            String teamUrl = serverUrl + "/" + repoOwner;
            result.add(new ObjectMetadataAction(
                    repoOwner,
                    null,
                    teamUrl
            ));
            result.add(new BitbucketLink("icon-bitbucket-logo", teamUrl));
            listener.getLogger().println("Could not resolve team details");
        }
        return result;
    }

    private static String getLink(Map<String, BitbucketHref> links, String name) {
        if (links == null) {
            return null;
        }
        BitbucketHref href = links.get(name);
        return href == null ? null : href.getHref();
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
        public String getIconClassName() {
            return "icon-bitbucket-scmnavigator";
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
            return BitbucketCredentials.fillCredentials(bitbucketServerUrl, context, result);
        }

        public ListBoxModel doFillCheckoutCredentialsIdItems(@AncestorInPath SCMSourceOwner context, @QueryParameter String bitbucketServerUrl) {
            StandardListBoxModel result = new StandardListBoxModel();
            result.add("- same as scan credentials -", BitbucketSCMSource.DescriptorImpl.SAME);
            result.add("- anonymous -", BitbucketSCMSource.DescriptorImpl.ANONYMOUS);
            return BitbucketCredentials.fillCheckoutCredentials(bitbucketServerUrl, context, result);
        }

        @NonNull
        @Override
        protected SCMSourceCategory[] createCategories() {
            return new SCMSourceCategory[]{
                    new UncategorizedSCMSourceCategory(Messages._BitbucketSCMNavigator_UncategorizedSCMSourceCategory_DisplayName())
            };
        }

        static {
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-scm-navigator icon-sm",
                            "plugin/cloudbees-bitbucket-branch-source/images/16x16/bitbucket-scmnavigator.png",
                            Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-scm-navigator icon-md",
                            "plugin/cloudbees-bitbucket-branch-source/images/24x24/bitbucket-scmnavigator.png",
                            Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-scm-navigator icon-lg",
                            "plugin/cloudbees-bitbucket-branch-source/images/32x32/bitbucket-scmnavigator.png",
                            Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-scm-navigator icon-xlg",
                            "plugin/cloudbees-bitbucket-branch-source/images/48x48/bitbucket-scmnavigator.png",
                            Icon.ICON_XLARGE_STYLE));

            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-logo icon-sm",
                            "plugin/cloudbees-bitbucket-branch-source/images/16x16/bitbucket-logo.png",
                            Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-logo icon-md",
                            "plugin/cloudbees-bitbucket-branch-source/images/24x24/bitbucket-logo.png",
                            Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-logo icon-lg",
                            "plugin/cloudbees-bitbucket-branch-source/images/32x32/bitbucket-logo.png",
                            Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-logo icon-xlg",
                            "plugin/cloudbees-bitbucket-branch-source/images/48x48/bitbucket-logo.png",
                            Icon.ICON_XLARGE_STYLE));

            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-repo icon-sm",
                            "plugin/cloudbees-bitbucket-branch-source/images/16x16/bitbucket-repository.png",
                            Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-repo icon-md",
                            "plugin/cloudbees-bitbucket-branch-source/images/24x24/bitbucket-repository.png",
                            Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-repo icon-lg",
                            "plugin/cloudbees-bitbucket-branch-source/images/32x32/bitbucket-repository.png",
                            Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-repo icon-xlg",
                            "plugin/cloudbees-bitbucket-branch-source/images/48x48/bitbucket-repository.png",
                            Icon.ICON_XLARGE_STYLE));

            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-repo-git icon-sm",
                            "plugin/cloudbees-bitbucket-branch-source/images/16x16/bitbucket-repository-git.png",
                            Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-repo-git icon-md",
                            "plugin/cloudbees-bitbucket-branch-source/images/24x24/bitbucket-repository-git.png",
                            Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-repo-git icon-lg",
                            "plugin/cloudbees-bitbucket-branch-source/images/32x32/bitbucket-repository-git.png",
                            Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-repo-git icon-xlg",
                            "plugin/cloudbees-bitbucket-branch-source/images/48x48/bitbucket-repository-git.png",
                            Icon.ICON_XLARGE_STYLE));

            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-repo-hg icon-sm",
                            "plugin/cloudbees-bitbucket-branch-source/images/16x16/bitbucket-repository-hg.png",
                            Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-repo-hg icon-md",
                            "plugin/cloudbees-bitbucket-branch-source/images/24x24/bitbucket-repository-hg.png",
                            Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-repo-hg icon-lg",
                            "plugin/cloudbees-bitbucket-branch-source/images/32x32/bitbucket-repository-hg.png",
                            Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-repo-hg icon-xlg",
                            "plugin/cloudbees-bitbucket-branch-source/images/48x48/bitbucket-repository-hg.png",
                            Icon.ICON_XLARGE_STYLE));

            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-branch icon-sm",
                            "plugin/cloudbees-bitbucket-branch-source/images/16x16/bitbucket-branch.png",
                            Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-branch icon-md",
                            "plugin/cloudbees-bitbucket-branch-source/images/24x24/bitbucket-branch.png",
                            Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-branch icon-lg",
                            "plugin/cloudbees-bitbucket-branch-source/images/32x32/bitbucket-branch.png",
                            Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-branch icon-xlg",
                            "plugin/cloudbees-bitbucket-branch-sourcee/images/48x48/bitbucket-branch.png",
                            Icon.ICON_XLARGE_STYLE));
        }
    }
}
