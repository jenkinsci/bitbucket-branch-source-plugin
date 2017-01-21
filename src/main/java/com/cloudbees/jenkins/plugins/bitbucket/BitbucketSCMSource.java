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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBranch;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRequestException;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Util;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.TaskListener;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.BuildChooserSetting;
import hudson.plugins.git.util.BuildChooser;
import hudson.plugins.git.util.DefaultBuildChooser;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.plugins.mercurial.MercurialSCM.RevisionType;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.AbstractGitSCMSource.SpecificRevisionBuildChooser;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.api.SCMSourceEvent;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.api.metadata.PrimaryInstanceMetadataAction;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.UncategorizedSCMHeadCategory;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.Constants;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

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
     * If true, branches are included when scanning in the retrieve process.
     */
    private boolean scanBranches = true;
    
    /**
     * If true, pull requests are included when scanning in the retrieve process.
     */
    private boolean scanPullRequests = true;
    
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

    /**
     * The cache of pull request titles for each open PR.
     */
    @CheckForNull
    private transient /*effectively final*/ Map<String, String> pullRequestTitleCache;

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
    
    @DataBoundSetter
    public void setScanBranches(boolean scanBranches) {
        this.scanBranches = scanBranches;
    }
    
    public boolean getScanBranches() {
        return scanBranches;
    }
       
    @DataBoundSetter
    public void setScanPullRequests(boolean scanPullRequests) {
        this.scanPullRequests = scanPullRequests;
    }
    
    public boolean getScanPullRequests() {
        return scanPullRequests;
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

    private String bitbucketUrl() {
        return StringUtils.defaultIfBlank(bitbucketServerUrl, "https://bitbucket.org");
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

    public BitbucketApi buildBitbucketClient(PullRequestSCMHead head) {
        return getBitbucketConnector().create(head.getRepoOwner(), head.getRepository(), getScanCredentials());
    }

    @Override
    protected void retrieve(@CheckForNull SCMSourceCriteria criteria, @NonNull SCMHeadObserver observer,
                            @CheckForNull SCMHeadEvent<?> event, @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        StandardUsernamePasswordCredentials scanCredentials = getScanCredentials();
        if (scanCredentials == null) {
            listener.getLogger().format("Connecting to %s with no credentials, anonymous access%n", bitbucketUrl());
        } else {
            listener.getLogger().format("Connecting to %s using %s%n", bitbucketUrl(), CredentialsNameProvider.name(scanCredentials));
        }

        // Search branches
        if (getScanBranches()) {
            retrieveBranches(criteria, observer, listener);
        }
        // Search pull requests
        if (getScanPullRequests()) {
            retrievePullRequests(criteria, observer, listener);
        }
    }

    private void retrievePullRequests(SCMSourceCriteria criteria, SCMHeadObserver observer, final TaskListener listener)
            throws IOException, InterruptedException {
        String fullName = repoOwner + "/" + repository;
        listener.getLogger().println("Looking up " + fullName + " for pull requests");

        final BitbucketApi bitbucket = getBitbucketConnector().create(repoOwner, repository, getScanCredentials());
        if (bitbucket.isPrivate()) {
            List<? extends BitbucketPullRequest> pulls = bitbucket.getPullRequests();
            Set<String> livePRs = new HashSet<>();
            for (final BitbucketPullRequest pull : pulls) {
                checkInterrupt();
                listener.getLogger().println(
                        "Checking PR from " + pull.getSource().getRepository().getFullName() + " and branch "
                                + pull.getSource().getBranch().getName());

                // Resolve full hash. See https://bitbucket.org/site/master/issues/11415/pull-request-api-should-return-full-commit

                String hash = null;
                try {
                    hash = bitbucket.resolveSourceFullHash(pull);
                } catch (BitbucketRequestException e) {
                    if (e.getHttpCode() == 403) {
                        listener.getLogger().println(
                                "Do not have permission to view PR from " + pull.getSource().getRepository().getFullName() + " and branch "
                                        + pull.getSource().getBranch().getName());
                    } else {
                        e.printStackTrace(
                                listener.error("Cannot resolve hash: [%s]%n", pull.getSource().getCommit().getHash()));
                    }
                    continue;
                }
                if (hash != null) {
                    getPullRequestTitleCache().put(pull.getId(), StringUtils.defaultString(pull.getTitle()));
                    livePRs.add(pull.getId());
                    observe(criteria, observer, listener,
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
            getPullRequestTitleCache().keySet().retainAll(livePRs);
        } else {
            listener.getLogger().format("Skipping pull requests for public repositories%n");
        }
    }

    private void retrieveBranches(SCMSourceCriteria criteria, @NonNull final SCMHeadObserver observer,
                                  @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        String fullName = repoOwner + "/" + repository;
        listener.getLogger().println("Looking up " + fullName + " for branches");

        final BitbucketApi bitbucket = getBitbucketConnector().create(repoOwner, repository, getScanCredentials());
        List<? extends BitbucketBranch> branches = bitbucket.getBranches();
        for (BitbucketBranch branch : branches) {
            checkInterrupt();
            listener.getLogger().println("Checking branch " + branch.getName() + " from " + fullName);
            observe(criteria, observer, listener, repoOwner, repository, branch.getName(),
                    branch.getRawNode(), null);
        }
    }

    private void observe(SCMSourceCriteria criteria, SCMHeadObserver observer, final TaskListener listener,
                         final String owner, final String repositoryName,
                         final String branchName, final String hash, BitbucketPullRequest pr) throws IOException {
        if (isExcluded(branchName)) {
            return;
        }
        final BitbucketApi bitbucket = getBitbucketConnector().create(owner, repositoryName, getScanCredentials());
        SCMSourceCriteria branchCriteria = criteria;
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
        SCMHead head = pr != null
                ? new PullRequestSCMHead(owner, repositoryName, branchName, pr)
                : new BranchSCMHead(branchName);
        if (getRepositoryType() == RepositoryType.MERCURIAL) {
            revision = new MercurialRevision(head, hash);
        } else {
            revision = new AbstractGitSCMSource.SCMRevisionImpl(head, hash);
        }
        observer.observe(head, revision);
    }



    @Override
    protected SCMRevision retrieve(SCMHead head, TaskListener listener) throws IOException, InterruptedException {
        BitbucketApi bitbucket = head instanceof PullRequestSCMHead
                ? getBitbucketConnector().create(
                        ((PullRequestSCMHead) head).getRepoOwner(),
                        ((PullRequestSCMHead) head).getRepository(),
                        getScanCredentials()
                )
                : getBitbucketConnector().create(repoOwner, repository, getScanCredentials());
        String branchName = head instanceof PullRequestSCMHead ? ((PullRequestSCMHead) head).getBranchName() : head.getName();
        List<? extends BitbucketBranch> branches = bitbucket.getBranches();
        for (BitbucketBranch b : branches) {
            if (branchName.equals(b.getName())) {
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
        LOGGER.log(Level.WARNING, "No branch found in {0}/{1} with name [{2}]", head instanceof PullRequestSCMHead
                ? new Object[]{
                ((PullRequestSCMHead) head).getRepoOwner(),
                ((PullRequestSCMHead) head).getRepository(),
                ((PullRequestSCMHead) head).getBranchName()}
                : new Object[]{repoOwner, repository, head.getName()});
        return null;
    }

    @Override
    public SCM build(SCMHead head, SCMRevision revision) {
        if (head instanceof PullRequestSCMHead) {
            PullRequestSCMHead h = (PullRequestSCMHead) head;
            if (getRepositoryType() == RepositoryType.MERCURIAL) {
                MercurialSCM scm = new MercurialSCM(getRemote(h.getRepoOwner(), h.getRepository()));
                // If no revision specified the branch name will be used as revision
                scm.setRevision(revision instanceof MercurialRevision
                        ? ((MercurialRevision) revision).getHash()
                        : h.getBranchName()
                );
                scm.setRevisionType(RevisionType.BRANCH);
                scm.setCredentialsId(getCheckoutEffectiveCredentials());
                return scm;
            } else {
                // Defaults to Git
                BuildChooser buildChooser = revision instanceof AbstractGitSCMSource.SCMRevisionImpl
                        ? new SpecificRevisionBuildChooser((AbstractGitSCMSource.SCMRevisionImpl) revision)
                        : new DefaultBuildChooser();
                return new GitSCM(getGitRemoteConfigs(h),
                        Collections.singletonList(new BranchSpec(h.getBranchName())),
                        false, Collections.<SubmoduleConfig>emptyList(),
                        null, null, Collections.<GitSCMExtension>singletonList(new BuildChooserSetting(buildChooser)));
            }
        }
        if (head instanceof BranchSCMHead) {
            if (getRepositoryType() == RepositoryType.MERCURIAL) {
                MercurialSCM scm = new MercurialSCM(getRemote(repoOwner, repository));
                // If no revision specified the branch name will be used as revision
                scm.setRevision(revision instanceof MercurialRevision
                        ? ((MercurialRevision) revision).getHash()
                        : head.getName()
                );
                scm.setRevisionType(RevisionType.BRANCH);
                scm.setCredentialsId(getCheckoutEffectiveCredentials());
                return scm;
            } else {
                // Defaults to Git
                BuildChooser buildChooser = revision instanceof AbstractGitSCMSource.SCMRevisionImpl
                        ? new SpecificRevisionBuildChooser((AbstractGitSCMSource.SCMRevisionImpl) revision)
                        : new DefaultBuildChooser();
                return new GitSCM(getGitRemoteConfigs((BranchSCMHead)head),
                        Collections.singletonList(new BranchSpec(head.getName())),
                        false, Collections.<SubmoduleConfig>emptyList(),
                        null, null, Collections.<GitSCMExtension>singletonList(new BuildChooserSetting(buildChooser)));
            }
        }
        throw new IllegalArgumentException("Either PullRequestSCMHead or BranchSCMHead required as parameter");
    }

    protected List<UserRemoteConfig> getGitRemoteConfigs(BranchSCMHead head) {
        List<UserRemoteConfig> result = new ArrayList<UserRemoteConfig>();
        String remote = getRemote(repoOwner, repository);
        result.add(new UserRemoteConfig(remote, getRemoteName(), "+refs/heads/" + head.getName(), getCheckoutEffectiveCredentials()));
        return result;
    }

    protected List<UserRemoteConfig> getGitRemoteConfigs(PullRequestSCMHead head) {
        List<UserRemoteConfig> result = new ArrayList<UserRemoteConfig>();
        String remote = getRemote(head.getRepoOwner(), head.getRepository());
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

    @NonNull
    @Override
    protected List<Action> retrieveActions(@CheckForNull SCMSourceEvent event,
                                           @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        // TODO when we have support for trusted events, use the details from event if event was from trusted source
        List<Action> result = new ArrayList<>();
        final BitbucketApi bitbucket = getBitbucketConnector().create(repoOwner, repository, getScanCredentials());
        BitbucketRepository r = bitbucket.getRepository();
        if (r != null) {
            result.add(new BitbucketRepoMetadataAction(r));
        }
        String defaultBranch = bitbucket.getDefaultBranch();
        if (StringUtils.isNotBlank(defaultBranch)) {
            result.add(new BitbucketDefaultBranch(repoOwner, repository, defaultBranch));
        }
        String serverUrl = StringUtils.removeEnd(bitbucketUrl(), "/");
        if (StringUtils.isNotEmpty(bitbucketServerUrl)) {
            result.add(new BitbucketLink("icon-bitbucket-repo",
                    serverUrl + "/projects/" + repoOwner + "/repos/" + repository));
            result.add(new ObjectMetadataAction(r == null ? null : r.getFullName(), null,
                    serverUrl + "/projects/" + repoOwner + "/repos/" + repository));
        } else {
            result.add(new BitbucketLink("icon-bitbucket-repo", serverUrl + "/" + repoOwner + "/" + repository));
            result.add(new ObjectMetadataAction(r == null ? null : r.getFullName(), null,
                    serverUrl + "/" + repoOwner + "/" + repository));
        }
        return result;
    }

    @NonNull
    @Override
    protected List<Action> retrieveActions(@NonNull SCMHead head,
                                           @CheckForNull SCMHeadEvent event,
                                           @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        // TODO when we have support for trusted events, use the details from event if event was from trusted source
        List<Action> result = new ArrayList<>();
        String serverUrl = StringUtils.removeEnd(bitbucketUrl(), "/");
        if (StringUtils.isNotEmpty(bitbucketServerUrl)) {
            String branchUrl;
            String title;
            if (head instanceof PullRequestSCMHead) {
                PullRequestSCMHead pr = (PullRequestSCMHead) head;
                branchUrl = "projects/" + repoOwner + "/repos/" + repository + "/pull-requests/"+pr.getId()+"/overview";
                title = getPullRequestTitleCache().get(pr.getId());
            } else {
                branchUrl = "projects/" + repoOwner + "/repos/" + repository + "/compare/commits?sourceBranch=" +
                        URLEncoder.encode(Constants.R_HEADS + head.getName(), "UTF-8");
                title = null;
            }
            result.add(new BitbucketLink("icon-bitbucket-branch", serverUrl + "/" + branchUrl));
            result.add(new ObjectMetadataAction(title, null, serverUrl+"/"+branchUrl));
        } else {
            String branchUrl;
            String title;
            if (head instanceof PullRequestSCMHead) {
                PullRequestSCMHead pr = (PullRequestSCMHead) head;
                branchUrl = repoOwner + "/" + repository + "/pull-requests/" + pr.getId();
                title = getPullRequestTitleCache().get(pr.getId());
            } else {
                branchUrl = repoOwner + "/" + repository + "/branch/" + head.getName();
                title = null;
            }
            result.add(new BitbucketLink("icon-bitbucket-branch", serverUrl + "/" + branchUrl));
            result.add(new ObjectMetadataAction(title, null, serverUrl + "/" + branchUrl));
        }
        SCMSourceOwner owner = getOwner();
        if (owner instanceof Actionable) {
            for (BitbucketDefaultBranch p : ((Actionable) owner).getActions(BitbucketDefaultBranch.class)) {
                if (StringUtils.equals(getRepoOwner(), p.getRepoOwner())
                        && StringUtils.equals(repository, p.getRepository())
                        && StringUtils.equals(p.getDefaultBranch(), head.getName())) {
                    result.add(new PrimaryInstanceMetadataAction());
                    break;
                }
            }
        }
        return result;
    }

    @NonNull
    private synchronized Map<String, String> getPullRequestTitleCache() {
        if (pullRequestTitleCache == null) {
            pullRequestTitleCache = new ConcurrentHashMap<>();
        }
        return pullRequestTitleCache;
    }

    @Extension
    public static class DescriptorImpl extends SCMSourceDescriptor {

        public static final String ANONYMOUS = "ANONYMOUS";
        public static final String SAME = "SAME";

        @Override
        public String getDisplayName() {
            return "Bitbucket";
        }

        public FormValidation doCheckCredentialsId(@QueryParameter String value,
                                                   @QueryParameter String bitbucketServerUrl) {
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
            result.includeEmptyValue();
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

        @NonNull
        @Override
        protected SCMHeadCategory[] createCategories() {
            return new SCMHeadCategory[]{
                    new UncategorizedSCMHeadCategory(Messages._BitbucketSCMSource_UncategorizedSCMHeadCategory_DisplayName()),
                    new ChangeRequestSCMHeadCategory(Messages._BitbucketSCMSource_ChangeRequestSCMHeadCategory_DisplayName())
                    // TODO add support for tags and maybe feature branch identification
            };
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
