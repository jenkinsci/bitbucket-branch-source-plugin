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
import com.cloudbees.jenkins.plugins.bitbucket.JsonParser;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMRevision;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerAPIClient;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.pullrequest.BitbucketServerPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketServerRepository;
import com.cloudbees.jenkins.plugins.bitbucket.server.events.NativeServerRefsChangedEvent;
import com.google.common.base.Ascii;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;

import static java.util.Objects.requireNonNull;

public class NativeServerPushHookProcessor extends HookProcessor {

    private static final Logger LOGGER = Logger.getLogger(NativeServerPushHookProcessor.class.getName());

    @Override
    public void process(HookEventType hookEvent, String payload, BitbucketType instanceType, String origin) {
        return; // without a server URL, the event wouldn't match anything
    }

    @Override
    public void process(HookEventType hookEvent, String payload, BitbucketType instanceType, String origin,
        String serverUrl) {
        if (payload == null) {
            return;
        }

        final NativeServerRefsChangedEvent refsChangedEvent;
        try {
            refsChangedEvent = JsonParser.toJava(payload, NativeServerRefsChangedEvent.class);
        } catch (final IOException e) {
            LOGGER.log(Level.SEVERE, "Can not read hook payload", e);
            return;
        }

        final String owner = refsChangedEvent.getRepository().getOwnerName();
        final String repository = refsChangedEvent.getRepository().getRepositoryName();
        if (refsChangedEvent.getChanges().isEmpty()) {
            LOGGER.log(Level.INFO, "Received hook from Bitbucket. Processing push event on {0}/{1}",
                new Object[] { owner, repository });
            scmSourceReIndex(owner, repository);
            return;
        }

        final Multimap<SCMEvent.Type, NativeServerRefsChangedEvent.Change> events = HashMultimap.create();
        for (final NativeServerRefsChangedEvent.Change change : refsChangedEvent.getChanges()) {
            final String type = change.getType();
            if ("UPDATE".equals(type)) {
                events.put(SCMEvent.Type.UPDATED, change);
            } else if ("DELETE".equals(type)) {
                events.put(SCMEvent.Type.REMOVED, change);
            } else if ("ADD".equals(type)) {
                events.put(SCMEvent.Type.CREATED, change);
            } else {
                LOGGER.log(Level.INFO, "Unknown change event type of {0} received from Bitbucket Server", type);
            }
        }

        for (final SCMEvent.Type type : events.keySet()) {
            SCMHeadEvent.fireLater(new HeadEvent(serverUrl, type, events.get(type), origin, refsChangedEvent), BitbucketSCMSource.getEventDelaySeconds(), TimeUnit.SECONDS);
        }
    }

    private static final class HeadEvent extends NativeServerHeadEvent<Collection<NativeServerRefsChangedEvent.Change>> implements HasPullRequests {
        private final NativeServerRefsChangedEvent refsChangedEvent;
        private final Map<CacheKey, Map<String, BitbucketServerPullRequest>> cachedPullRequests = new HashMap<>();

        HeadEvent(String serverUrl, Type type, Collection<NativeServerRefsChangedEvent.Change> payload, String origin,
            NativeServerRefsChangedEvent refsChangedEvent) {
            super(serverUrl, type, payload, origin);
            this.refsChangedEvent = refsChangedEvent;
        }

        @Override
        protected BitbucketServerRepository getRepository() {
            return refsChangedEvent.getRepository();
        }

        @Override
        protected Map<SCMHead, SCMRevision> heads(BitbucketSCMSource source) {
            final Map<SCMHead, SCMRevision> result = new HashMap<>();
            if (!eventMatchesRepo(source)) {
                return result;
            }

            addBranchesAndTags(source, result);
            try {
                addPullRequests(source, result);
            } catch (InterruptedException interrupted) {
                LOGGER.log(Level.INFO, "Interrupted while fetching Pull Requests from Bitbucket, results may be incomplete.");
            }
            return result;
        }

        private void addBranchesAndTags(BitbucketSCMSource src, Map<SCMHead, SCMRevision> result) {
            for (final NativeServerRefsChangedEvent.Change change : getPayload()) {
                String refType = change.getRef().getType();

                if ("BRANCH".equals(refType)) {
                    final BranchSCMHead head = new BranchSCMHead(change.getRef().getDisplayId());
                    final SCMRevision revision = getType() == SCMEvent.Type.REMOVED ? null
                            : new AbstractGitSCMSource.SCMRevisionImpl(head, change.getToHash());
                    result.put(head, revision);
                } else if ("TAG".equals(refType)) {
                    SCMHead head = new BitbucketTagSCMHead(change.getRef().getDisplayId(), 0);
                    final SCMRevision revision = getType() == SCMEvent.Type.REMOVED ? null
                            : new AbstractGitSCMSource.SCMRevisionImpl(head, change.getToHash());
                    result.put(head, revision);
                } else {
                    LOGGER.log(Level.INFO, "Received event for unknown ref type {0} of ref {1}",
                            new Object[] { change.getRef().getType(), change.getRef().getDisplayId() });
                }
            }
        }

        private void addPullRequests(BitbucketSCMSource src, Map<SCMHead, SCMRevision> result) throws InterruptedException {
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
            final BitbucketServerRepository eventRepo = refsChangedEvent.getRepository();
            final SCMHeadOrigin headOrigin = src.originOf(eventRepo.getOwnerName(), eventRepo.getRepositoryName());
            final Set<ChangeRequestCheckoutStrategy> strategies = headOrigin == SCMHeadOrigin.DEFAULT
                ? ctx.originPRStrategies() : ctx.forkPRStrategies();

            for (final NativeServerRefsChangedEvent.Change change : getPayload()) {
                if (!"BRANCH".equals(change.getRef().getType())) {
                    LOGGER.log(Level.INFO, "Received event for unknown ref type {0} of ref {1}",
                        new Object[] { change.getRef().getType(), change.getRef().getDisplayId() });
                    continue;
                }

                // iterate over all PRs in which this change is involved
                for (final BitbucketServerPullRequest pullRequest : getPullRequests(src, change).values()) {
                    final BitbucketServerRepository targetRepo = pullRequest.getDestination().getRepository();
                    // check if the target of the PR is actually this source
                    if (!sourceOwnerName.equalsIgnoreCase(targetRepo.getOwnerName())
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

                        final PullRequestSCMHead head = new PullRequestSCMHead(branchName, sourceOwnerName,
                            sourceRepoName, originalBranchName, pullRequest, headOrigin, strategy);

                        final String targetHash = pullRequest.getDestination().getCommit().getHash();
                        final String pullHash = pullRequest.getSource().getCommit().getHash();

                        result.put(head,
                            new PullRequestSCMRevision<>(head,
                                new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(), targetHash),
                                new AbstractGitSCMSource.SCMRevisionImpl(head, pullHash)));
                    }
                }
            }
        }

        private Map<String, BitbucketServerPullRequest> getPullRequests(BitbucketSCMSource src, NativeServerRefsChangedEvent.Change change)
            throws InterruptedException {

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

        private Map<String, BitbucketServerPullRequest> loadPullRequests(BitbucketSCMSource src,
            NativeServerRefsChangedEvent.Change change) throws InterruptedException {

            final BitbucketServerRepository eventRepo = refsChangedEvent.getRepository();
            final BitbucketServerAPIClient api = (BitbucketServerAPIClient) src
                .buildBitbucketClient(eventRepo.getOwnerName(), eventRepo.getRepositoryName());

            final Map<String, BitbucketServerPullRequest> pullRequests = new HashMap<>();

            try {
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
            }

            return pullRequests;
        }

        @Override
        public Collection<BitbucketPullRequest> getPullRequests(BitbucketSCMSource src) throws InterruptedException {
            List<BitbucketPullRequest> prs = new ArrayList<>();
            for (final NativeServerRefsChangedEvent.Change change : getPayload()) {
                Map<String, BitbucketServerPullRequest> prsForChange = getPullRequests(src, change);
                prs.addAll(prsForChange.values());
            }

            return prs;
        }
    }

    private static final class CacheKey {
        @NonNull
        private final String refId;
        @CheckForNull
        private final String credentialsId;

        CacheKey(BitbucketSCMSource src, NativeServerRefsChangedEvent.Change change) {
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

            if (obj instanceof CacheKey) {
                CacheKey other = (CacheKey) obj;
                return Objects.equals(credentialsId, other.credentialsId) && refId.equals(other.refId);
            }

            return false;
        }
    }
}
