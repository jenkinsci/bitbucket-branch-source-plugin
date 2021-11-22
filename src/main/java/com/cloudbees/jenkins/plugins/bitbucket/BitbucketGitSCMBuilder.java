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
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryProtocol;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.AbstractBitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketServerEndpoint;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.browser.BitbucketWeb;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.MergeWithGitSCMExtension;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.mixin.TagSCMHead;
import org.apache.commons.lang.StringUtils;

/**
 * A {@link GitSCMBuilder} specialized for bitbucket.
 *
 * @since 2.2.0
 */
public class BitbucketGitSCMBuilder extends GitSCMBuilder<BitbucketGitSCMBuilder> {

    /**
     * The {@link BitbucketSCMSource} who's {@link BitbucketSCMSource#getOwner()} can be used as the context for
     * resolving credentials.
     */
    @NonNull
    private final BitbucketSCMSource scmSource;

    /**
     * The clone links for cloning the source repository and origin pull requests (but links will need tweaks for
     * fork pull requests)
     */
    @NonNull
    private List<BitbucketHref> cloneLinks = Collections.emptyList();

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
        withoutRefSpecs();
        if (head instanceof PullRequestSCMHead) {
            if (scmSource.buildBitbucketClient() instanceof BitbucketCloudApiClient) {
                // TODO fix once Bitbucket Cloud has a fix for https://bitbucket.org/site/master/issues/5814
                String branchName = ((PullRequestSCMHead) head).getBranchName();
                withRefSpec("+refs/heads/" + branchName + ":refs/remotes/@{remote}/" + head.getName());
            } else {
                String pullId = ((PullRequestSCMHead) head).getId();
                withRefSpec("+refs/pull-requests/" + pullId + "/from:refs/remotes/@{remote}/" + head.getName());
            }
        } else if (head instanceof TagSCMHead ){
            withRefSpec("+refs/tags/" + head.getName() + ":refs/tags/" + head.getName());
        } else {
            withRefSpec("+refs/heads/" + head.getName() + ":refs/remotes/@{remote}/" + head.getName());
        }
        this.scmSource = scmSource;
        AbstractBitbucketEndpoint endpoint =
                BitbucketEndpointConfiguration.get().findEndpoint(scmSource.getServerUrl());
        if (endpoint == null) {
            endpoint = new BitbucketServerEndpoint(null, scmSource.getServerUrl(), false, null);
        }
        withBrowser(new BitbucketWeb(endpoint.getRepositoryUrl(
                scmSource.getRepoOwner(),
                scmSource.getRepository()
        )));

        // Test for protocol
        withCredentials(credentialsId, null);

    }

    /**
     * Provides the clone links from the {@link BitbucketRepository} to allow inference of ports for different protocols.
     *
     * @param cloneLinks the clone links.
     * @return {@code this} for method chaining.
     */
    public BitbucketGitSCMBuilder withCloneLinks(List<BitbucketHref> cloneLinks) {
        this.cloneLinks = new ArrayList<>(Util.fixNull(cloneLinks));
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
     * Returns the clone links (possibly empty).
     *
     * @return the clone links (possibly empty).
     */
    @NonNull
    public List<BitbucketHref> cloneLinks() {
        return Collections.unmodifiableList(cloneLinks);
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
            StandardCredentials credentials = BitbucketCredentials.lookupCredentials(
                scmSource.getServerUrl(),
                scmSource.getOwner(),
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
        SCMHead h = head();
        String repoOwner;
        String repository;
        BitbucketApi bitbucket = scmSource().buildBitbucketClient();
        if (h instanceof PullRequestSCMHead && bitbucket instanceof BitbucketCloudApiClient) {
            // TODO fix once Bitbucket Cloud has a fix for https://bitbucket.org/site/master/issues/5814
            repoOwner = ((PullRequestSCMHead) h).getRepoOwner();
            repository = ((PullRequestSCMHead) h).getRepository();
        } else {
            // head instanceof BranchSCMHead
            repoOwner = scmSource.getRepoOwner();
            repository = scmSource.getRepository();
        }

        String cloneLink = null;
        for (BitbucketHref link : cloneLinks()) {
            if (protocol.getType().equals(link.getName())) {
                cloneLink = link.getHref();
                break;
            }
        }
        withRemote(bitbucket.getRepositoryUri(
                protocol,
                cloneLink,
                repoOwner,
                repository));
        AbstractBitbucketEndpoint endpoint =
                BitbucketEndpointConfiguration.get().findEndpoint(scmSource.getServerUrl());
        if (endpoint == null) {
            endpoint = new BitbucketServerEndpoint(null, scmSource.getServerUrl(), false, null);
        }
        withBrowser(new BitbucketWeb(
                endpoint.getRepositoryUrl(
                        repoOwner,
                        repository
                )));

        // now, if we have to build a merge commit, let's ensure we build the merge commit!
        SCMRevision r = revision();
        if (h instanceof PullRequestSCMHead) {
            PullRequestSCMHead head = (PullRequestSCMHead) h;
            if (head.getCheckoutStrategy() == ChangeRequestCheckoutStrategy.MERGE) {
                String name = head.getTarget().getName();
                String localName = head.getBranchName().equals(name) ? "upstream-" + name : name;

                String remoteName = remoteName().equals("upstream") ? "upstream-upstream" : "upstream";
                withAdditionalRemote(remoteName,
                        bitbucket.getRepositoryUri(
                                protocol,
                                cloneLink,
                                scmSource().getRepoOwner(),
                                scmSource().getRepository()),
                        "+refs/heads/" + name + ":refs/remotes/@{remote}/" + localName);
                if ((r instanceof PullRequestSCMRevision)
                        && ((PullRequestSCMRevision) r).getTarget() instanceof AbstractGitSCMSource.SCMRevisionImpl) {
                    withExtension(new MergeWithGitSCMExtension("remotes/" + remoteName + "/" + localName,
                            ((AbstractGitSCMSource.SCMRevisionImpl) ((PullRequestSCMRevision) r).getTarget())
                                    .getHash()));
                } else {
                    withExtension(new MergeWithGitSCMExtension("remotes/" + remoteName + "/" + localName, null));
                }
            }
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public GitSCM build() {
        withBitbucketRemote();
        SCMHead h = head();
        SCMRevision r = revision();
        try {
            if (h instanceof PullRequestSCMHead) {
                withHead(new SCMHead(((PullRequestSCMHead) h).getBranchName()));
                if (r instanceof PullRequestSCMRevision) {
                    withRevision(((PullRequestSCMRevision) r).getPull());
                }
            }
            return super.build();
        } finally {
            withHead(h);
            withRevision(r);
        }
    }
}
