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
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryType;
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
import hudson.plugins.mercurial.MercurialSCM;
import hudson.plugins.mercurial.MercurialSCMBuilder;
import hudson.plugins.mercurial.MercurialSCMSource;
import hudson.plugins.mercurial.browser.BitBucket;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.apache.commons.lang.StringUtils;

/**
 * A {@link MercurialSCMBuilder} specialized for bitbucket.
 *
 * @since 2.2.0
 */
public class BitbucketHgSCMBuilder extends MercurialSCMBuilder<BitbucketHgSCMBuilder> {
    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(BitbucketHgSCMBuilder.class.getName());
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
     * Constructor.
     *
     * @param scmSource     the {@link BitbucketSCMSource}.
     * @param head          the {@link SCMHead}
     * @param revision      the (optional) {@link SCMRevision}
     * @param credentialsId The {@link IdCredentials#getId()} of the {@link Credentials} to use when connecting to
     *                      the {@link #source()} or {@code null} to let the hg client choose between providing its own
     *                      credentials or connecting anonymously.
     */
    public BitbucketHgSCMBuilder(@NonNull BitbucketSCMSource scmSource, @NonNull SCMHead head,
                                 @CheckForNull SCMRevision revision, String credentialsId) {
        super(head, revision, /*dummy value*/scmSource.getServerUrl(), credentialsId);
        this.scmSource = scmSource;
        AbstractBitbucketEndpoint endpoint =
                BitbucketEndpointConfiguration.get().findEndpoint(scmSource.getServerUrl());
        if (endpoint == null) {
            endpoint = new BitbucketServerEndpoint(null, scmSource.getServerUrl(), false, null);
        }
        try {
            withBrowser(new BitBucket(endpoint.getRepositoryUrl(
                    scmSource.getRepoOwner(),
                    scmSource.getRepository()
            )));
        } catch (MalformedURLException e) {
            // ignore, we are providing a well formed URL and if we are not then we shouldn't apply a browser
        }
    }

    /**
     * Provides the clone links from the {@link BitbucketRepository} to allow inference of ports for different protocols.
     *
     * @param cloneLinks the clone links.
     * @return {@code this} for method chaining.
     */
    public BitbucketHgSCMBuilder withCloneLinks(List<BitbucketHref> cloneLinks) {
        this.cloneLinks = new ArrayList<>(Util.fixNull(cloneLinks));
        return withBitbucketSource();
    }

    /**
     * Returns the {@link BitbucketSCMSource} that this request is against (primarily to allow resolving credentials
     * against {@link SCMSource#getOwner()}.
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
     * Updates the {@link MercurialSCMBuilder#withSource(String)} based on the current {@link #head()} and
     * {@link #revision()}.
     * Will be called automatically by {@link #build()} but exposed in case the correct remote is required after
     * changing the {@link #withCredentialsId(String)}.
     *
     * @return {@code this} for method chaining.
     */
    @NonNull
    public BitbucketHgSCMBuilder withBitbucketSource() {
        // Apply clone links and credentials
        StandardCredentials credentials = StringUtils.isBlank(credentialsId())
                ? null
                : BitbucketCredentials.lookupCredentials(
                        scmSource().getServerUrl(),
                        scmSource().getOwner(),
                        credentialsId(),
                        StandardCredentials.class
                );
        BitbucketRepositoryProtocol protocol = credentials instanceof SSHUserPrivateKey
                ? BitbucketRepositoryProtocol.SSH
                : BitbucketRepositoryProtocol.HTTP;
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
        withSource(bitbucket.getRepositoryUri(
                BitbucketRepositoryType.MERCURIAL,
                protocol,
                cloneLink,
                repoOwner,
                repository));
        AbstractBitbucketEndpoint endpoint =
                BitbucketEndpointConfiguration.get().findEndpoint(scmSource.getServerUrl());
        if (endpoint == null) {
            endpoint = new BitbucketServerEndpoint(null, scmSource.getServerUrl(), false, null);
        }
        try {
            withBrowser(new BitBucket(endpoint.getRepositoryUrl(
                    repoOwner,
                    repository
            )));
        } catch (MalformedURLException e) {
            // ignore, we are providing a well formed URL and if we are not then we shouldn't apply a browser
        }

        // now, if we have to build a merge commit, let's ensure we build the merge commit!
        if (h instanceof PullRequestSCMHead) {
            PullRequestSCMHead head = (PullRequestSCMHead) h;
            if (head.getCheckoutStrategy() == ChangeRequestCheckoutStrategy.MERGE) {
                LOGGER.log(Level.WARNING, "Building MERGE commits of PRs of Mercurial based repositories on "
                        + "BitBucket Cloud is not currently supported, falling back to HEAD commit");
                // TODO decorate with something that handles merge commits // FIXME file a Jenkins JIRA
            }
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public MercurialSCM build() {
        withBitbucketSource();
        SCMHead h = head();
        SCMRevision r = revision();
        try {
            BitbucketSCMSource.MercurialRevision rev;
            if (h instanceof PullRequestSCMHead) {
                withHead(new SCMHead(((PullRequestSCMHead) h).getBranchName()));
                if (r instanceof PullRequestSCMRevision) {
                    rev = ((PullRequestSCMRevision<BitbucketSCMSource.MercurialRevision>) r).getPull();
                } else if (r instanceof BitbucketSCMSource.MercurialRevision) {
                    rev = (BitbucketSCMSource.MercurialRevision) r;
                } else {
                    rev = null;
                }
            } else {
                rev = r instanceof BitbucketSCMSource.MercurialRevision
                        ? (BitbucketSCMSource.MercurialRevision) r : null;
            }
            if (rev != null) {
                withRevision(new MercurialSCMSource.MercurialRevision(head(), rev.getHash()));
            }

            return super.build();
        } finally {
            withHead(h);
            withRevision(r);
        }
    }

}
