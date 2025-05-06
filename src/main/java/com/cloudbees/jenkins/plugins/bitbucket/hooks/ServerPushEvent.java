/*
 * The MIT License
 *
 * Copyright (c) 2016-2018, Yieldlab AG
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
package com.cloudbees.jenkins.plugins.bitbucket.hooks;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSourceContext;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketTagSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.BranchSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMRevision;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketCredentials;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerAPIClient;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.branch.BitbucketServerCommit;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.pullrequest.BitbucketServerPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketServerRepository;
import com.cloudbees.jenkins.plugins.bitbucket.server.events.NativeServerChange;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.google.common.base.Ascii;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.apache.commons.lang3.StringUtils;

import static java.util.Objects.requireNonNull;

final class ServerPushEvent extends AbstractNativeServerSCMHeadEvent<Collection<NativeServerChange>> implements HasPullRequests {

    private static final class CacheKey {
        @NonNull
        private final String refId;
        @CheckForNull
        private final String credentialsId;

        CacheKey(BitbucketSCMSource src, NativeServerChange change) {
            this.refId = requireNonNull(change.getRefId());
            this.credentialsId = src.getCredentialsId();
        }

        @Override
        public int hashCode() {
            return Objects.hash(credentialsId, refId);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (obj instanceof CacheKey cacheKey) {
                return Objects.equals(credentialsId, cacheKey.credentialsId) && Objects.equals(refId, cacheKey.refId);
            }

            return false;
        }
    }

    // event logs with the name of the processor
    private static final Logger LOGGER = Logger.getLogger(NativeServerPushHookProcessor.class.getName());

    private final BitbucketServerRepository repository;
    private final BitbucketServerCommit refCommit;
    private final Map<CacheKey, Map<String, BitbucketServerPullRequest>> cachedPullRequests = new HashMap<>();
    private final String mirrorId;

    ServerPushEvent(String serverURL,
                    Type type,
                    Collection<NativeServerChange> payload,
                    String origin,
                    BitbucketServerRepository repository,
                    @CheckForNull BitbucketServerCommit headCommit,
                    String mirrorId) {
        super(serverURL, type, payload, origin);
        this.repository = repository;
        this.mirrorId = mirrorId;
        this.refCommit = headCommit;
    }

    @Override
    protected BitbucketServerRepository getRepository() {
        return repository;
    }

    @Override
    protected Map<SCMHead, SCMRevision> heads(BitbucketSCMSource source) {
        final Map<SCMHead, SCMRevision> result = new HashMap<>();
        if (!eventMatchesRepo(source)) {
            return result;
        }

        addBranchesAndTags(source, result);
        addPullRequests(source, result);
        return result;
    }

    private void addBranchesAndTags(BitbucketSCMSource src, Map<SCMHead, SCMRevision> result) {
        for (final NativeServerChange change : getPayload()) {
            String refType = change.getRef().getType();

            if ("BRANCH".equals(refType)) {
                final BranchSCMHead head = new BranchSCMHead(change.getRef().getDisplayId());
                final SCMRevision revision = getType() == SCMEvent.Type.REMOVED ? null
                        : new AbstractGitSCMSource.SCMRevisionImpl(head, change.getToHash());
                result.put(head, revision);
            } else if ("TAG".equals(refType)) {
                String tagName = change.getRef().getDisplayId();
                long tagTimestamp = 0L;
                try (BitbucketApi client = getClient(src)) {
//                    BitbucketBranch tag = client.getTag(tagName); // requires two API call and does not return the tag timestamp
                    String tagHash;
                    switch (change.getType()) {
                    case "ADD": {
                        tagHash = change.getToHash();
                        break;
                    }
                    case "DELETE": {
                        tagHash = change.getFromHash();
                        break;
                    }
                    default:
                        throw new UnsupportedOperationException("Tag event of type " + change.getType()
                            + " is not supported.\nPlease fill an issue at https://issues.jenkins.io to the bitbucket-branch-source-plugin component.");
                    }
                    if (refCommit != null) {
                        // the annotated tag hash it's an alias of a real commit and it's the refCommit (new head commit)
                        // it's not needed check if refCommit and tagCommit are equals
                        tagTimestamp = Optional.ofNullable(refCommit.getCommitterDate())
                                .map(Date::getTime)
                                .orElse(0L);
                    } else {
                        BitbucketCommit tag = client.resolveCommit(tagHash);
                        if (tag != null) {
                            tagTimestamp = Optional.ofNullable(tag.getCommitterDate())
                                    .map(Date::getTime)
                                    .orElse(0L);
                        }
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Fail to retrive the timestamp for tag event {0}", tagName);
                }
                SCMHead head = new BitbucketTagSCMHead(tagName, tagTimestamp);
                final SCMRevision revision = getType() == SCMEvent.Type.REMOVED ? null
                        : new AbstractGitSCMSource.SCMRevisionImpl(head, change.getToHash());
                result.put(head, revision);
            } else {
                LOGGER.log(Level.INFO, "Received event for unknown ref type {0} of ref {1}",
                        new Object[] { change.getRef().getType(), change.getRef().getDisplayId() });
            }
        }
    }

    protected BitbucketApi getClient(BitbucketSCMSource src) {
        String serverURL = src.getServerUrl();

        StandardCredentials credentials = BitbucketCredentials.lookupCredentials(
            serverURL,
            src.getOwner(),
            src.getCredentialsId(),
            StandardCredentials.class
        );
        BitbucketAuthenticator authenticator = AuthenticationTokens.convert(BitbucketAuthenticator.authenticationContext(serverURL), credentials);
        return BitbucketApiFactory.newInstance(serverURL, authenticator, src.getRepoOwner(), null, src.getRepository());
    }

    private void addPullRequests(BitbucketSCMSource src, Map<SCMHead, SCMRevision> result) {
        if (getType() != SCMEvent.Type.UPDATED) {
            return; // adds/deletes won't be handled here
        }

        final BitbucketSCMSourceContext ctx = contextOf(src);
        if (!ctx.wantPRs()) {
            // doesn't want PRs, let the push event handle origin branches
            return;
        }

        final String sourceOwnerName = src.getRepoOwner();
        final String sourceRepoName = src.getRepository();
        final BitbucketServerRepository eventRepo = repository;
        final SCMHeadOrigin headOrigin = src.originOf(eventRepo.getOwnerName(), eventRepo.getRepositoryName());
        final Set<ChangeRequestCheckoutStrategy> strategies = headOrigin == SCMHeadOrigin.DEFAULT
            ? ctx.originPRStrategies() : ctx.forkPRStrategies();

        for (final NativeServerChange change : getPayload()) {
            if (!"BRANCH".equals(change.getRef().getType())) {
                LOGGER.log(Level.INFO, "Received event for unknown ref type {0} of ref {1}",
                    new Object[] { change.getRef().getType(), change.getRef().getDisplayId() });
                continue;
            }

            // iterate over all PRs in which this change is involved
            for (final BitbucketServerPullRequest pullRequest : getPullRequests(src, change).values()) {
                final BitbucketServerRepository targetRepo = pullRequest.getDestination().getRepository();
                // check if the target of the PR is actually this source
                if (!StringUtils.equalsIgnoreCase(sourceOwnerName, targetRepo.getOwnerName())
                    || !sourceRepoName.equalsIgnoreCase(targetRepo.getRepositoryName())) {
                    continue;
                }

                for (final ChangeRequestCheckoutStrategy strategy : strategies) {
                    if (strategy != ChangeRequestCheckoutStrategy.MERGE && !change.getRefId().equals(pullRequest.getSource().getRefId())) {
                        continue; // Skip non-merge builds if the changed ref is not the source of the PR.
                    }

                    final String originalBranchName = pullRequest.getSource().getBranch().getName();
                    final String branchName = String.format("PR-%s%s", pullRequest.getId(),
                        strategies.size() > 1 ? "-" + Ascii.toLowerCase(strategy.name()) : "");

                    final BitbucketServerRepository pullRequestRepository = pullRequest.getSource().getRepository();
                    final PullRequestSCMHead head = new PullRequestSCMHead(
                        branchName,
                        pullRequestRepository.getOwnerName(),
                        pullRequestRepository.getRepositoryName(),
                        originalBranchName,
                        pullRequest,
                        headOrigin,
                        strategy
                    );

                    final String targetHash = pullRequest.getDestination().getCommit().getHash();
                    final String pullHash = pullRequest.getSource().getCommit().getHash();

                    result.put(head,
                        new PullRequestSCMRevision(head,
                            new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(), targetHash),
                            new AbstractGitSCMSource.SCMRevisionImpl(head, pullHash)));
                }
            }
        }
    }

    private Map<String, BitbucketServerPullRequest> getPullRequests(BitbucketSCMSource src, NativeServerChange change) {

        Map<String, BitbucketServerPullRequest> pullRequests;
        final CacheKey cacheKey = new CacheKey(src, change);
        synchronized (cachedPullRequests) {
            pullRequests = cachedPullRequests.get(cacheKey);
            if (pullRequests == null) {
                cachedPullRequests.put(cacheKey, pullRequests = loadPullRequests(src, change));
            }
        }

        return pullRequests;
    }

    private Map<String, BitbucketServerPullRequest> loadPullRequests(BitbucketSCMSource src, NativeServerChange change) {
        final BitbucketServerRepository eventRepo = repository;
        final Map<String, BitbucketServerPullRequest> pullRequests = new HashMap<>();

        try (BitbucketServerAPIClient api = (BitbucketServerAPIClient) src
                .buildBitbucketClient(eventRepo.getOwnerName(), eventRepo.getRepositoryName())) {
            try {
                for (final BitbucketServerPullRequest pullRequest : api.getOutgoingOpenPullRequests(change.getRefId())) {
                    pullRequests.put(pullRequest.getId(), pullRequest);
                }
            } catch (final FileNotFoundException e) {
                throw e;
            } catch (IOException | RuntimeException e) {
                LOGGER.log(Level.WARNING, "Failed to retrieve outgoing Pull Requests from Bitbucket", e);
            }

            try {
                for (final BitbucketServerPullRequest pullRequest : api.getIncomingOpenPullRequests(change.getRefId())) {
                    pullRequests.put(pullRequest.getId(), pullRequest);
                }
            } catch (final FileNotFoundException e) {
                throw e;
            } catch (IOException | RuntimeException e) {
                LOGGER.log(Level.WARNING, "Failed to retrieve incoming Pull Requests from Bitbucket", e);
            }
        } catch (FileNotFoundException e) {
            LOGGER.log(Level.INFO, "No such Repository on Bitbucket: {0}", e.getMessage());
        } catch (IOException e1) {
            LOGGER.log(Level.INFO, "Comunication fail with server", e1);
        }

        return pullRequests;
    }

    @Override
    public Collection<BitbucketPullRequest> getPullRequests(BitbucketSCMSource src) throws InterruptedException {
        List<BitbucketPullRequest> prs = new ArrayList<>();
        for (final NativeServerChange change : getPayload()) {
            Map<String, BitbucketServerPullRequest> prsForChange = getPullRequests(src, change);
            prs.addAll(prsForChange.values());
        }

        return prs;
    }

    @Override
    protected boolean eventMatchesRepo(BitbucketSCMSource source) {
        return StringUtils.equalsIgnoreCase(source.getMirrorId(), this.mirrorId) && super.eventMatchesRepo(source);
    }

}
