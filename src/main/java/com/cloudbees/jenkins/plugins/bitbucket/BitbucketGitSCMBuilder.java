/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryProtocol;
import com.cloudbees.jenkins.plugins.bitbucket.api.PullRequestBranchType;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpointProvider;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketServerEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.extension.FallbackToOtherRepositoryGitSCMExtension;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketApiUtils;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketCredentialsUtils;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.SCMUtils;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.browser.BitbucketServer;
import hudson.plugins.git.browser.BitbucketWeb;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.MergeWithGitSCMExtension;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.mixin.TagSCMHead;
import org.apache.commons.lang3.StringUtils;

/**
 * A {@link GitSCMBuilder} specialized for bitbucket.
 *
 * @since 2.2.0
 */
public class BitbucketGitSCMBuilder extends GitSCMBuilder<BitbucketGitSCMBuilder> {
    private static final Logger logger = Logger.getLogger(BitbucketGitSCMBuilder.class.getName());

    /**
     * The {@link BitbucketSCMSource} who's {@link BitbucketSCMSource#getOwner()} can be used as the context for
     * resolving credentials.
     */
    @NonNull
    private final BitbucketSCMSource scmSource;

    /**
     * The clone links for primary repository
     */
    @NonNull
    private List<BitbucketHref> primaryCloneLinks = List.of();

    /**
     * The clone links for mirror repository if it's configured
     */
    @NonNull
    private List<BitbucketHref> mirrorCloneLinks = List.of();

    /**
     * The {@link BitbucketRepositoryProtocol} that should be used.
     * Enables support for blank SSH credentials.
     */
    @NonNull
    private BitbucketRepositoryProtocol protocol = BitbucketRepositoryProtocol.HTTP;

    /**
     * Constructor.
     *
     * @param scmSource     the {@link BitbucketSCMSource}.
     * @param head          the {@link SCMHead}
     * @param revision      the (optional) {@link SCMRevision}
     * @param credentialsId The {@link IdCredentials#getId()} of the {@link Credentials} to use when connecting to
     *                      the {@link #remote()} or {@code null} to let the git client choose between providing its own
     *                      credentials or connecting anonymously.
     */
    public BitbucketGitSCMBuilder(@NonNull BitbucketSCMSource scmSource, @NonNull SCMHead head,
                                  @CheckForNull SCMRevision revision, @CheckForNull String credentialsId) {
        // we provide a dummy repository URL to the super constructor and then fix is afterwards once we have
        // the clone links
        super(head, revision, /*dummy value*/scmSource.getServerUrl(), credentialsId);
        this.scmSource = scmSource;

        String serverURL = scmSource.getServerUrl();
        BitbucketEndpoint endpoint = BitbucketEndpointProvider.lookupEndpoint(serverURL)
                .orElse(BitbucketApiUtils.isCloud(serverURL) ? new BitbucketCloudEndpoint() : new BitbucketServerEndpoint("tmp", serverURL));

        String repositoryURL = endpoint.getRepositoryURL(scmSource.getRepoOwner(), scmSource.getRepository());
        if (BitbucketApiUtils.isCloud(endpoint.getServerURL())) {
            withBrowser(new BitbucketWeb(repositoryURL));
        } else {
            withBrowser(new BitbucketServer(repositoryURL));
        }

        // Test for protocol
        withCredentials(credentialsId, null);

    }

    /**
     * Provides the clone links from the {@link BitbucketRepository} to allow inference of ports for different protocols.
     *
     * @param primaryCloneLinks the clone links for primary repository.
     * @param mirrorCloneLinks the clone links for mirror repository if it's configured.
     * @return {@code this} for method chaining.
     */
    public BitbucketGitSCMBuilder withCloneLinks(
        @CheckForNull List<BitbucketHref> primaryCloneLinks,
        @CheckForNull List<BitbucketHref> mirrorCloneLinks
    ) {
        if (primaryCloneLinks == null) {
            throw new IllegalArgumentException("Primary clone links shouldn't be null");
        }
        this.primaryCloneLinks = primaryCloneLinks;
        this.mirrorCloneLinks = Util.fixNull(mirrorCloneLinks);
        return withBitbucketRemote();
    }

    /**
     * Returns the {@link BitbucketSCMSource} that this request is against (primarily to allow resolving credentials
     * against {@link SCMSource#getOwner()}).
     *
     * @return the {@link BitbucketSCMSource} that this request is against
     */
    @NonNull
    public BitbucketSCMSource scmSource() {
        return scmSource;
    }

    /**
     * Configures the {@link IdCredentials#getId()} of the {@link Credentials} to use when connecting to the
     * {@link #remote()}
     *
     * @param credentialsId the {@link IdCredentials#getId()} of the {@link Credentials} to use when connecting to
     *                      the {@link #remote()} or {@code null} to let the git client choose between providing its own
     *                      credentials or connecting anonymously.
     * @param protocol the {@link BitbucketRepositoryProtocol} of the {@link Credentials} to use or {@code null}
     *                 to detect the protocol based on the credentialsId. Defaults to HTTP if credentials are
     *                 {@code null}.  Enables support for blank SSH credentials.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public BitbucketGitSCMBuilder withCredentials(String credentialsId, BitbucketRepositoryProtocol protocol) {
        if (StringUtils.isNotBlank(credentialsId)) {
            StandardCredentials credentials = BitbucketCredentialsUtils.lookupCredentials(
                scmSource.getOwner(),
                scmSource.getServerUrl(),
                credentialsId,
                StandardCredentials.class
            );

            if (protocol == null) {
                protocol = credentials instanceof SSHUserPrivateKey
                    ? BitbucketRepositoryProtocol.SSH
                    : BitbucketRepositoryProtocol.HTTP;
            }
        } else if (protocol == null) {
            // If we set credentials to empty reset the type to HTTP.
            // To set the build to use empty SSH credentials, call withProtocol after setting credentials
            protocol = BitbucketRepositoryProtocol.HTTP;
        }

        this.protocol = protocol;
        return withCredentials(credentialsId);
    }

    /**
     * Updates the {@link GitSCMBuilder#withRemote(String)} based on the current {@link #head()} and
     * {@link #revision()}.
     * Will be called automatically by {@link #build()} but exposed in case the correct remote is required after
     * changing the {@link #withCredentials(String)}.
     *
     * @return {@code this} for method chaining.
     */
    @NonNull
    public BitbucketGitSCMBuilder withBitbucketRemote() {
        SCMHead head = head();
        String headName = head.getName();
        if (head instanceof PullRequestSCMHead prHead) {
            withPullRequestRemote(prHead, headName);
        } else if (head instanceof TagSCMHead) {
            withTagRemote(headName);
        } else {
            withBranchRemote(headName);
        }
        return this;
    }

    private void withPullRequestRemote(PullRequestSCMHead head, String headName) { // NOSONAR
        String scmSourceRepoOwner = scmSource.getRepoOwner();
        String scmSourceRepository = scmSource.getRepository();
        String pullRequestRepoOwner = head.getRepoOwner();
        String pullRequestRepository = head.getRepository();
        boolean prFromTargetRepository = StringUtils.equalsIgnoreCase(pullRequestRepoOwner, scmSourceRepoOwner)
            && pullRequestRepository.equalsIgnoreCase(scmSourceRepository);
        SCMRevision revision = revision();
        ChangeRequestCheckoutStrategy checkoutStrategy = head.getCheckoutStrategy();
        // PullRequestSCMHead should be refactored to add references to target and source commit hashes.
        // So revision should not be used here. There is a hack to use revision to get hashes.
        boolean cloneFromMirror = prFromTargetRepository
            && !mirrorCloneLinks.isEmpty()
            && revision instanceof PullRequestSCMRevision;
        String targetBranch = head.getTarget().getName();
        String branchName = head.getBranchName();
        boolean scmCloud = BitbucketApiUtils.isCloud(scmSource.getServerUrl());
        if (prFromTargetRepository) {
            if (head.getBranchType() == PullRequestBranchType.TAG) {
                withRefSpec("+refs/tags/" + branchName + ":refs/remotes/@{remote}/" + branchName); // NOSONAR
            } else {
                withRefSpec("+refs/heads/" + branchName + ":refs/remotes/@{remote}/" + branchName); // NOSONAR
            }
            if (cloneFromMirror) {
                PullRequestSCMRevision prRevision = (PullRequestSCMRevision) revision;
                String primaryRemoteName = remoteName().equals("primary") ? "primary-primary" : "primary";
                String cloneLink = getCloneLink(primaryCloneLinks);
                List<BranchWithHash> branchWithHashes;
                if (checkoutStrategy == ChangeRequestCheckoutStrategy.MERGE) {
                    branchWithHashes = List.of(
                        new BranchWithHash(branchName, SCMUtils.getHash(prRevision.getPull())),
                        new BranchWithHash(targetBranch, SCMUtils.getHash(prRevision))
                    );
                } else {
                    branchWithHashes = List.of(
                        new BranchWithHash(branchName, SCMUtils.getHash(prRevision.getPull()))
                    );
                }
                withExtension(new FallbackToOtherRepositoryGitSCMExtension(cloneLink, primaryRemoteName, branchWithHashes));
                withMirrorRemote();
            } else {
                withPrimaryRemote();
            }
        } else {
            if (scmCloud) {
                withRefSpec("+refs/heads/" + branchName + ":refs/remotes/@{remote}/" + headName);
                String cloneLink = getCloudRepositoryUri(pullRequestRepoOwner, pullRequestRepository);
                withRemote(cloneLink);
            } else {
                String pullId = head.getId();
                withRefSpec("+refs/pull-requests/" + pullId + "/from:refs/remotes/@{remote}/" + headName);
                withPrimaryRemote();
            }
        }
        if (head.getCheckoutStrategy() == ChangeRequestCheckoutStrategy.MERGE) {
            String hash = revision instanceof PullRequestSCMRevision prRevision ? SCMUtils.getHash(prRevision) : null;
            String refSpec = "+refs/heads/" + targetBranch + ":refs/remotes/@{remote}/" + targetBranch;
            if (!prFromTargetRepository && scmCloud) {
                String upstreamRemoteName = remoteName().equals("upstream") ? "upstream-upstream" : "upstream";
                withAdditionalRemote(upstreamRemoteName, getCloneLink(primaryCloneLinks), refSpec);
                withExtension(new MergeWithGitSCMExtension("remotes/" + upstreamRemoteName + "/" + targetBranch, hash));
            } else {
                withRefSpec(refSpec);
                withExtension(new MergeWithGitSCMExtension("remotes/" + remoteName() + "/" + targetBranch, hash));
            }
        }
    }

    @NonNull
    private String getCloudRepositoryUri(@NonNull String owner, @NonNull String repository) {
        switch (protocol) {
            case HTTP:
                return "https://bitbucket.org/" + owner + "/" + repository + ".git";
            case SSH:
                return "ssh://git@bitbucket.org/" + owner + "/" + repository + ".git";
            default:
                throw new IllegalArgumentException("Unsupported repository protocol: " + protocol);
        }
    }

    private void withTagRemote(String headName) {
        withRefSpec("+refs/tags/" + headName + ":refs/tags/" + headName);
        if (mirrorCloneLinks.isEmpty()) {
            withPrimaryRemote();
        } else {
            withMirrorRemote();
        }
    }

    private void withBranchRemote(String headName) {
        withRefSpec("+refs/heads/" + headName + ":refs/remotes/@{remote}/" + headName);
        if (mirrorCloneLinks.isEmpty()) {
            withPrimaryRemote();
        } else {
            withMirrorRemote();
        }
    }

    private void withPrimaryRemote() {
        String cloneLink = getCloneLink(primaryCloneLinks);
        withRemote(cloneLink);
    }

    private void withMirrorRemote() {
        String cloneLink = getCloneLink(mirrorCloneLinks);
        withRemote(cloneLink);
    }

    private String getCloneLink(List<BitbucketHref> cloneLinks) {
        return cloneLinks.stream()
            .filter(link -> protocol.matches(link.getName()))
            .findAny()
            .orElseThrow(() -> new IllegalStateException("Can't find clone link for protocol " + protocol + ". Did you disabled the protocol on Bitbucket Data Center configuration?"))
            .getHref();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public GitSCM build() {
        withBitbucketRemote();
        SCMHead head = head();
        SCMRevision rev = revision();
        try {
            if (head instanceof PullRequestSCMHead prHead) {
                withHead(new SCMHead(prHead.getBranchName()));
                if (rev instanceof PullRequestSCMRevision prRev) {
                    SCMRevision revision = resolvePullRequestRevision(prHead, prRev);
                    withRevision(revision);
                }
            }
            return super.build();
        } finally {
            withHead(head);
            withRevision(rev);
        }
    }

    @NonNull
    private SCMRevision resolvePullRequestRevision(PullRequestSCMHead prHead, PullRequestSCMRevision prRev) {
        SCMRevision revision = prRev.getPull();
        String hash = SCMUtils.getHash(revision);
        if (hash != null && StringUtils.length(hash) != 40) { // JENKINS-75555
            try (BitbucketApi client = scmSource.buildBitbucketClient(prHead)) {
                BitbucketCommit commit = client.resolveCommit(hash);
                if (commit != null) {
                    revision = new AbstractGitSCMSource.SCMRevisionImpl(prHead, commit.getHash());
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Can not retrieve commit for hash " + hash, e);
                throw new RuntimeException(e);
            }
        }
        return revision;
    }
}
