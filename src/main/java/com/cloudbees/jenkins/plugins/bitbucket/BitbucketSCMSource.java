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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBranch;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.BuildChooserSetting;
import hudson.plugins.git.extensions.impl.SubmoduleOption;
import hudson.plugins.git.util.BuildChooser;
import hudson.plugins.git.util.DefaultBuildChooser;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.plugins.mercurial.MercurialSCM.RevisionType;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.AbstractGitSCMSource.SpecificRevisionBuildChooser;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.api.SCMSourceOwner;

/**
 * SCM source implementation for Bitbucket.
 * 
 * It provides a way to discover/retrieve branches and pull requests through the Bitbuclet REST API
 * which is much faster than the plain Git SCM source implementation.
 */
public class BitbucketSCMSource extends SCMSource {

    /**
     * Credentials used to access the Bitbucket REST API.
     */
    private String credentialsId;

    /**
     * Credentials used to clone the repository/repositories.
     */
    private String checkoutCredentialsId;

    /**
     * Repository owner.
     * Used to build the repository URL.
     */
    private final String repoOwner;

    /**
     * Repository name.
     * Used to build the repository URL.
     */
    private final String repository;

    /**
     * Ant match expression that indicates what branches to include in the retrieve process.
     */
    private String includes = "*";

    /**
     * Ant match expression that indicates what branches to exclude in the retrieve process.
     */
    private String excludes = "";

    /**
     * If true, a webhook will be auto-registered in the repository managed by this source.
     */
    private boolean autoRegisterHook = false;

    /**
     * Bitbucket Server URL.
     * An specific HTTP client is used if this field is not null.
     */
    private String bitbucketServerUrl;

    /**
     * Port used by Bitbucket Server for SSH clone.
     * -1 by default (for Bitbucket Cloud).
     */
    private int sshPort = -1;

    /**
     * Repository type.
     */
    private RepositoryType repositoryType;

    /**
     * Bitbucket API client connector.
     */
    private transient BitbucketApiConnector bitbucketConnector;

    private static final Logger LOGGER = Logger.getLogger(BitbucketSCMSource.class.getName());

    @DataBoundConstructor
    public BitbucketSCMSource(String id, String repoOwner, String repository) {
        super(id);
        this.repoOwner = repoOwner;
        this.repository = repository;
    }

    @CheckForNull
    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = Util.fixEmpty(credentialsId);
    }

    @CheckForNull
    public String getCheckoutCredentialsId() {
        return checkoutCredentialsId;
    }

    @DataBoundSetter
    public void setCheckoutCredentialsId(String checkoutCredentialsId) {
        this.checkoutCredentialsId = checkoutCredentialsId;
    }

    public String getIncludes() {
        return includes;
    }

    @DataBoundSetter
    public void setIncludes(@NonNull String includes) {
        Pattern.compile(getPattern(includes));
        this.includes = includes;
    }

    public String getExcludes() {
        return excludes;
    }

    @DataBoundSetter
    public void setExcludes(@NonNull String excludes) {
        Pattern.compile(getPattern(excludes));
        this.excludes = excludes;
    }

    public String getRepoOwner() {
        return repoOwner;
    }

    public String getRepository() {
        return repository;
    }

    @DataBoundSetter
    public void setAutoRegisterHook(boolean autoRegisterHook) {
        this.autoRegisterHook = autoRegisterHook;
    }

    public boolean isAutoRegisterHook() {
        return autoRegisterHook;
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

    private BitbucketApiConnector getBitbucketConnector() {
        if (bitbucketConnector == null) {
            bitbucketConnector = new BitbucketApiConnector(bitbucketServerUrl);
        }
        return bitbucketConnector;
    }

    public String getRemote(@NonNull String repoOwner, @NonNull String repository) {
        return getUriResolver().getRepositoryUri(repoOwner, repository, getRepositoryType());
    }

    public RepositoryType getRepositoryType() {
        if (repositoryType == null) {
            BitbucketRepository r = getBitbucketConnector().create(repoOwner, repository, getScanCredentials()).getRepository();
            if (r == null) {
                throw new AssertionError("Not found repository: " + repoOwner + "/" + repository);
            }
            repositoryType = RepositoryType.fromString(r.getScm());
        }
        return repositoryType;
    }

    public BitbucketApi buildBitbucketClient() {
        return getBitbucketConnector().create(repoOwner, repository, getScanCredentials());
    }

    @Override
    protected void retrieve(SCMHeadObserver observer, final TaskListener listener) throws IOException,
            InterruptedException {

        StandardUsernamePasswordCredentials scanCredentials = getScanCredentials();
        if (scanCredentials == null) {
            listener.getLogger().format("Connecting to %s with no credentials, anonymous access%n", bitbucketServerUrl == null ? "https://bitbucket.org" : bitbucketServerUrl);
        } else {
            listener.getLogger().format("Connecting to %s using %s%n", bitbucketServerUrl == null ? "https://bitbucket.org" : bitbucketServerUrl, CredentialsNameProvider.name(scanCredentials));
        }

        // Search branches
        retrieveBranches(observer, listener);
        // Search pull requests
        retrievePullRequests(observer, listener);
    }

    private void retrievePullRequests(SCMHeadObserver observer, final TaskListener listener) throws IOException {
        String fullName = repoOwner + "/" + repository;
        listener.getLogger().println("Looking up " + fullName + " for pull requests");

        final BitbucketApi bitbucket = getBitbucketConnector().create(repoOwner, repository, getScanCredentials());
        if (bitbucket.isPrivate()) {
            List<? extends BitbucketPullRequest> pulls = bitbucket.getPullRequests();
            for (final BitbucketPullRequest pull : pulls) {
                listener.getLogger().println(
                        "Checking PR from " + pull.getSource().getRepository().getFullName() + " and branch "
                                + pull.getSource().getBranch().getName());

                // Resolve full hash. See https://bitbucket.org/site/master/issues/11415/pull-request-api-should-return-full-commit
                String hash = bitbucket.resolveSourceFullHash(pull);
                if (hash != null) {
                    observe(observer, listener,
                            pull.getSource().getRepository().getOwnerName(),
                            pull.getSource().getRepository().getRepositoryName(),
                            pull.getSource().getBranch().getName(),
                            hash,
                            pull);
                } else {
                    listener.getLogger().format("Can not resolve hash: [%s]%n", pull.getSource().getCommit().getHash());
                }
                if (!observer.isObserving()) {
                    return;
                }
            }
        } else {
            listener.getLogger().format("Skipping pull requests for public repositories%n");
        }
    }

    private void retrieveBranches(@NonNull final SCMHeadObserver observer, @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        String fullName = repoOwner + "/" + repository;
        listener.getLogger().println("Looking up " + fullName + " for branches");

        final BitbucketApi bitbucket = getBitbucketConnector().create(repoOwner, repository, getScanCredentials());
        List<? extends BitbucketBranch> branches = bitbucket.getBranches();
        for (BitbucketBranch branch : branches) {
            listener.getLogger().println("Checking branch " + branch.getName() + " from " + fullName);
            observe(observer, listener, repoOwner, repository, branch.getName(),
                    branch.getRawNode(), null);
        }
    }

    private void observe(SCMHeadObserver observer, final TaskListener listener,
            final String owner, final String repositoryName, 
            final String branchName, final String hash, BitbucketPullRequest pr) throws IOException {
        if (isExcluded(branchName)) {
            return;
        }
        final BitbucketApi bitbucket = getBitbucketConnector().create(owner, repositoryName, getScanCredentials());
        SCMSourceCriteria branchCriteria = getCriteria();
        if (branchCriteria != null) {
            SCMSourceCriteria.Probe probe = new SCMSourceCriteria.Probe() {

                @Override
                public String name() {
                    return branchName;
                }

                @Override
                public long lastModified() {
                    BitbucketCommit commit = bitbucket.resolveCommit(hash);
                    if (commit == null) {
                        listener.getLogger().format("Can not resolve commit by hash [%s] on repository %s/%s%n",
                                hash, bitbucket.getOwner(), bitbucket.getRepositoryName());
                        return 0;
                    }
                    return commit.getDateMillis();
                }

                @Override
                public boolean exists(@NonNull String path) throws IOException {
                    return bitbucket.checkPathExists(branchName, path);
                }
            };
            if (branchCriteria.isHead(probe, listener)) {
                listener.getLogger().println("Met criteria");
            } else {
                listener.getLogger().println("Does not meet criteria");
                return;
            }
        }
        SCMRevision revision;
        SCMHeadWithOwnerAndRepo head = new SCMHeadWithOwnerAndRepo(owner, repositoryName, branchName, pr);
        if (getRepositoryType() == RepositoryType.MERCURIAL) {
            revision = new MercurialRevision(head, hash);
        } else {
            revision = new AbstractGitSCMSource.SCMRevisionImpl(head, hash);
        }
        observer.observe(head, revision);
    }

    @Override
    protected SCMRevision retrieve(SCMHead head, TaskListener listener) throws IOException, InterruptedException {
        SCMHeadWithOwnerAndRepo bbHead = (SCMHeadWithOwnerAndRepo) head;
        BitbucketApi bitbucket = getBitbucketConnector().create(bbHead.getRepoOwner(), bbHead.getRepoName(), getScanCredentials());
        List<? extends BitbucketBranch> branches = bitbucket.getBranches();
        for (BitbucketBranch b : branches) {
            if (b.getName().equals(bbHead.getBranchName())) {
                if (b.getRawNode() == null) {
                    if (getBitbucketServerUrl() == null) {
                        listener.getLogger().format("Cannot resolve the hash of the revision in branch %s", b.getName());
                    } else {
                        listener.getLogger().format("Cannot resolve the hash of the revision in branch %s. Perhaps you are using Bitbucket Server previous to 4.x", b.getName());
                    }
                    return null;
                }
                if (getRepositoryType() == RepositoryType.MERCURIAL) {
                    return new MercurialRevision(head, b.getRawNode());
                } else {
                    return new AbstractGitSCMSource.SCMRevisionImpl(head, b.getRawNode());
                }
            }
        }
        LOGGER.warning("No branch found in " + bbHead.getRepoOwner() + "/" + bbHead.getRepoName() + " with name [" + bbHead.getBranchName() + "]");
        return null;
    }

    @Override
    public SCM build(SCMHead head, SCMRevision revision) {
        if (head instanceof SCMHeadWithOwnerAndRepo) { // Defensive, it must be always true
            SCMHeadWithOwnerAndRepo h = (SCMHeadWithOwnerAndRepo) head;
            if (getRepositoryType() == RepositoryType.MERCURIAL) {
                MercurialSCM scm = new MercurialSCM(getRemote(h.getRepoOwner(), h.getRepoName()));
                // If no revision specified the branch name will be used as revision
                scm.setRevision(revision instanceof MercurialRevision ? ((MercurialRevision) revision).getHash() : h.getBranchName());
                scm.setRevisionType(RevisionType.BRANCH);
                scm.setCredentialsId(getCheckoutEffectiveCredentials());
                return scm;
            } else {
                // Defaults to Git
                List<GitSCMExtension> extensions = new LinkedList<GitSCMExtension>();

                BuildChooser buildChooser = revision instanceof AbstractGitSCMSource.SCMRevisionImpl ? new SpecificRevisionBuildChooser(
                        (AbstractGitSCMSource.SCMRevisionImpl) revision) : new DefaultBuildChooser();

                extensions.add(new BuildChooserSetting(buildChooser));
                extensions.add(new SubmoduleOption(false, true, false, "", 10000, true));

                return new GitSCM(
                        getGitRemoteConfigs(h),
                        Collections.singletonList(new BranchSpec(h.getBranchName())),
                        false, Collections.<SubmoduleConfig>emptyList(),
                        null, null, extensions);
            }
        }
        throw new IllegalArgumentException("An SCMHeadWithOwnerAndRepo required as parameter");
    }

    protected List<UserRemoteConfig> getGitRemoteConfigs(SCMHeadWithOwnerAndRepo head) {
        List<UserRemoteConfig> result = new ArrayList<UserRemoteConfig>();
        String remote = getRemote(head.getRepoOwner(), head.getRepoName());
        result.add(new UserRemoteConfig(remote, getRemoteName(), "+refs/heads/" + head.getBranchName(), getCheckoutEffectiveCredentials()));
        return result;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @CheckForNull
    /* package */ StandardUsernamePasswordCredentials getScanCredentials() {
        return getBitbucketConnector().lookupCredentials(getOwner(), credentialsId, StandardUsernamePasswordCredentials.class);
    }

    private StandardCredentials getCheckoutCredentials() {
        return getBitbucketConnector().lookupCredentials(getOwner(), getCheckoutEffectiveCredentials(), StandardCredentials.class);
    }

    public String getRemoteName() {
      return "origin";
    }

    /**
     * Returns true if the branchName isn't matched by includes or is matched by excludes.
     * 
     * @param branchName
     * @return true if branchName is excluded or is not included
     */
    private boolean isExcluded(String branchName) {
        return !Pattern.matches(getPattern(getIncludes()), branchName)
                || Pattern.matches(getPattern(getExcludes()), branchName);
    }

    /**
     * Returns the pattern corresponding to the branches containing wildcards. 
     * 
     * @param branches space separated list of expressions. 
     *        For example "*" which would match all branches and branch* would match branch1, branch2, etc.
     * @return pattern corresponding to the branches containing wildcards (ready to be used by {@link Pattern})
     */
    private String getPattern(String branches) {
        StringBuilder quotedBranches = new StringBuilder();
        for (String wildcard : branches.split(" ")) {
            StringBuilder quotedBranch = new StringBuilder();
            for (String branch : wildcard.split("\\*")) {
                if (wildcard.startsWith("*") || quotedBranches.length() > 0) {
                    quotedBranch.append(".*");
                }
                quotedBranch.append(Pattern.quote(branch));
            }
            if (wildcard.endsWith("*")) {
                quotedBranch.append(".*");
            }
            if (quotedBranches.length() > 0) {
                quotedBranches.append("|");
            }
            quotedBranches.append(quotedBranch);
        }
        return quotedBranches.toString();
    }

    private RepositoryUriResolver getUriResolver() {
        try {
            if (StringUtils.isBlank(checkoutCredentialsId)) {
                return new HttpsRepositoryUriResolver(bitbucketServerUrl);
            } else {
                if (getCheckoutCredentials() instanceof SSHUserPrivateKey) {
                    return new SshRepositoryUriResolver(bitbucketServerUrl, sshPort);
                } else {
                    // Defaults to HTTPS
                    return new HttpsRepositoryUriResolver(bitbucketServerUrl);
                }
            }
        } catch (MalformedURLException e) {
            LOGGER.log(Level.SEVERE, "Bitbucket URL is not valid", e);
            // The URL is validatd before, so this should never happen
            throw new IllegalStateException(e);
        }
    }

    private String getCheckoutEffectiveCredentials() {
        if (DescriptorImpl.ANONYMOUS.equals(checkoutCredentialsId)) {
            return null;
        } else if (DescriptorImpl.SAME.equals(checkoutCredentialsId)) {
            return credentialsId;
        } else {
            return checkoutCredentialsId;
        }
    }

    @Extension
    public static class DescriptorImpl extends SCMSourceDescriptor {

        public static final String ANONYMOUS = "ANONYMOUS";
        public static final String SAME = "SAME";

        @Override
        public String getDisplayName() {
            return "Bitbucket";
        }

        public FormValidation doCheckCredentialsId(@QueryParameter String value) {
            if (!value.isEmpty()) {
                return FormValidation.ok();
            } else {
                return FormValidation.warning("Credentials are required for notifications");
            }
        }

        public static FormValidation doCheckBitbucketServerUrl(@QueryParameter String bitbucketServerUrl) {
            String url = Util.fixEmpty(bitbucketServerUrl);
            if (url == null) {
                return FormValidation.ok();
            }
            try {
                new URL(bitbucketServerUrl);
            } catch (MalformedURLException e) {
                return FormValidation.error("Invalid URL: " +  e.getMessage());
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath SCMSourceOwner context, @QueryParameter String bitbucketServerUrl) {
            StandardListBoxModel result = new StandardListBoxModel();
            result.withEmptySelection();
            new BitbucketApiConnector(bitbucketServerUrl).fillCredentials(result, context);
            return result;
        }

        public ListBoxModel doFillCheckoutCredentialsIdItems(@AncestorInPath SCMSourceOwner context, @QueryParameter String bitbucketServerUrl) {
            StandardListBoxModel result = new StandardListBoxModel();
            result.add("- same as scan credentials -", SAME);
            result.add("- anonymous -", ANONYMOUS);
            new BitbucketApiConnector(bitbucketServerUrl).fillCheckoutCredentials(result, context);
            return result;
        }

    }

    public static class MercurialRevision extends SCMRevision {

        private static final long serialVersionUID = 1L;

        private String hash;

        public MercurialRevision(SCMHead head, String hash) {
            super(head);
            this.hash = hash;
        }

        public String getHash() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            MercurialRevision that = (MercurialRevision) o;

            return StringUtils.equals(hash, that.hash) && getHead().equals(that.getHead());

        }

        @Override
        public int hashCode() {
            return hash != null ? hash.hashCode() : 0;
        }
    }

}
