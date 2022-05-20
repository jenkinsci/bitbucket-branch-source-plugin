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
import com.cloudbees.jenkins.plugins.bitbucket.JsonParser;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMRevision;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketServerRepository;
import com.cloudbees.jenkins.plugins.bitbucket.server.events.NativeServerPullRequestEvent;
import com.google.common.base.Ascii;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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

public class NativeServerPullRequestHookProcessor extends HookProcessor {

    private static final Logger LOGGER = Logger.getLogger(NativeServerPullRequestHookProcessor.class.getName());

    @Override
    public void process(HookEventType hookEvent, String payload, BitbucketType instanceType, String origin) {
        // without a server URL, the event wouldn't match anything
    }

    @Override
    public void process(HookEventType hookEvent, String payload, BitbucketType instanceType, String origin, String serverUrl) {

        final NativeServerPullRequestEvent pullRequestEvent;
        try {
            pullRequestEvent = JsonParser.toJava(payload, NativeServerPullRequestEvent.class);
        } catch (final IOException e) {
            LOGGER.log(Level.SEVERE, "Can not read hook payload", e);
            return;
        }

        final SCMEvent.Type eventType;
        switch (hookEvent) {
            case SERVER_PULL_REQUEST_OPENED:
                eventType = SCMEvent.Type.CREATED;
                break;
            case SERVER_PULL_REQUEST_MERGED:
            case SERVER_PULL_REQUEST_DECLINED:
            case SERVER_PULL_REQUEST_DELETED:
                eventType = SCMEvent.Type.REMOVED;
                break;
            case SERVER_PULL_REQUEST_MODIFIED:
            case SERVER_PULL_REQUEST_REVIEWER_UPDATED:
            case SERVER_PULL_REQUEST_FROM_REF_UPDATED:
                eventType = SCMEvent.Type.UPDATED;
                break;
            default:
                LOGGER.log(Level.INFO, "Unknown hook event {0} received from Bitbucket Server", hookEvent);
                return;
        }

        SCMHeadEvent.fireLater(new HeadEvent(serverUrl, eventType, pullRequestEvent, origin), BitbucketSCMSource.getEventDelaySeconds(), TimeUnit.SECONDS);
    }

    private static final class HeadEvent extends NativeServerHeadEvent<NativeServerPullRequestEvent> implements HasPullRequests {
        private HeadEvent(String serverUrl, Type type, NativeServerPullRequestEvent payload, String origin) {
            super(serverUrl, type, payload, origin);
        }

        @Override
        protected BitbucketServerRepository getRepository() {
            return getPayload().getPullRequest().getDestination().getRepository();
        }

        @NonNull
        @Override
        protected Map<SCMHead, SCMRevision> heads(@NonNull BitbucketSCMSource source) {
            if (!eventMatchesRepo(source)) {
                return Collections.emptyMap();
            }

            final BitbucketSCMSourceContext ctx = contextOf(source);
            if (!ctx.wantPRs()) {
                return Collections.emptyMap(); // doesn't want PRs, nothing to do here
            }

            final BitbucketPullRequest pullRequest = getPayload().getPullRequest();
            final BitbucketRepository sourceRepo = pullRequest.getSource().getRepository();
            final SCMHeadOrigin headOrigin = source.originOf(sourceRepo.getOwnerName(), sourceRepo.getRepositoryName());
            final Set<ChangeRequestCheckoutStrategy> strategies = headOrigin == SCMHeadOrigin.DEFAULT
                ? ctx.originPRStrategies()
                : ctx.forkPRStrategies();
            final Map<SCMHead, SCMRevision> result = new HashMap<>(strategies.size());
            for (final ChangeRequestCheckoutStrategy strategy : strategies) {
                final String originalBranchName = pullRequest.getSource().getBranch().getName();
                final String branchName = String.format("PR-%s%s", pullRequest.getId(),
                    strategies.size() > 1 ? "-" + Ascii.toLowerCase(strategy.name()) : "");
                final PullRequestSCMHead head = new PullRequestSCMHead(branchName, source.getRepoOwner(),
                    source.getRepository(), originalBranchName, pullRequest, headOrigin, strategy);

                switch (getType()) {
                    case CREATED:
                    case UPDATED:
                        final String targetHash = pullRequest.getDestination().getCommit().getHash();
                        final String pullHash = pullRequest.getSource().getCommit().getHash();
                        result.put(head,
                            new PullRequestSCMRevision<>(head,
                                new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(), targetHash),
                                new AbstractGitSCMSource.SCMRevisionImpl(head, pullHash)));
                        break;

                    case REMOVED:
                        // special case for repo being deleted
                        result.put(head, null);
                        break;

                    default:
                        break;
                }
            }

            return result;
        }

        @Override
        public Iterable<BitbucketPullRequest> getPullRequests(BitbucketSCMSource src) throws InterruptedException {
            if (Type.REMOVED.equals(getType())) {
                return Collections.emptySet();
            }
            return Collections.singleton(getPayload().getPullRequest());
        }
    }
}
