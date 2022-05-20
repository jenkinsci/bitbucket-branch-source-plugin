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
package com.cloudbees.jenkins.plugins.bitbucket.hooks;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMNavigator;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSourceContext;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMRevision;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestEvent;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudWebhookPayload;
import com.cloudbees.jenkins.plugins.bitbucket.client.events.BitbucketCloudPullRequestEvent;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerWebhookPayload;
import com.cloudbees.jenkins.plugins.bitbucket.server.events.BitbucketServerPullRequestEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.scm.SCM;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMEvent.Type;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;

import static com.cloudbees.jenkins.plugins.bitbucket.hooks.HookEventType.PULL_REQUEST_DECLINED;
import static com.cloudbees.jenkins.plugins.bitbucket.hooks.HookEventType.PULL_REQUEST_MERGED;

public class PullRequestHookProcessor extends HookProcessor {

    private static final Logger LOGGER = Logger.getLogger(PullRequestHookProcessor.class.getName());

    @Override
    public void process(final HookEventType hookEvent, String payload, final BitbucketType instanceType, String origin) {
        if (payload != null) {
            BitbucketPullRequestEvent pull;
            if (instanceType == BitbucketType.SERVER) {
                pull = BitbucketServerWebhookPayload.pullRequestEventFromPayload(payload);
            } else {
                pull = BitbucketCloudWebhookPayload.pullRequestEventFromPayload(payload);
            }
            if (pull != null) {
                SCMEvent.Type eventType;
                switch (hookEvent) {
                    case PULL_REQUEST_CREATED:
                        eventType = SCMEvent.Type.CREATED;
                        break;
                    case PULL_REQUEST_DECLINED:
                    case PULL_REQUEST_MERGED:
                        eventType = SCMEvent.Type.REMOVED;
                        break;
                    default:
                        eventType = SCMEvent.Type.UPDATED;
                        break;
                }
                // assume updated as a catch-all type
                SCMHeadEvent.fireLater(new HeadEvent(eventType, pull, origin, hookEvent, instanceType), BitbucketSCMSource.getEventDelaySeconds(), TimeUnit.SECONDS);
            }
        }
    }

    private static final class HeadEvent extends SCMHeadEvent<BitbucketPullRequestEvent> implements HasPullRequests{
        private final HookEventType hookEvent;
        private final BitbucketType instanceType;

        private HeadEvent(Type type, BitbucketPullRequestEvent payload, String origin, HookEventType hookEvent,
                BitbucketType instanceType) {
            super(type, payload, origin);
            this.hookEvent = hookEvent;
            this.instanceType = instanceType;
        }

        @Override
        public boolean isMatch(@NonNull SCMNavigator navigator) {
            if (!(navigator instanceof BitbucketSCMNavigator)) {
                return false;
            }
            BitbucketSCMNavigator bbNav = (BitbucketSCMNavigator) navigator;
            if (!isServerUrlMatch(bbNav.getServerUrl())) {
                return false;
            }
            return bbNav.getRepoOwner().equalsIgnoreCase(getPayload().getRepository().getOwnerName());
        }

        private boolean isServerUrlMatch(String serverUrl) {
            if (serverUrl == null || BitbucketCloudEndpoint.SERVER_URL.equals(serverUrl)) {
                // this is a Bitbucket cloud navigator
                if (getPayload() instanceof BitbucketServerPullRequestEvent) {
                    return false;
                }
            } else {
                // this is a Bitbucket server navigator
                if (getPayload() instanceof BitbucketCloudPullRequestEvent) {
                    return false;
                }
                Map<String, List<BitbucketHref>> links = getPayload().getRepository().getLinks();
                if (links != null && links.containsKey("self")) {
                    boolean matches = false;
                    for (BitbucketHref link: links.get("self")) {
                        try {
                            URI navUri = new URI(serverUrl);
                            URI evtUri = new URI(link.getHref());
                            if (navUri.getHost().equalsIgnoreCase(evtUri.getHost())) {
                                matches = true;
                                break;
                            }
                        } catch (URISyntaxException e) {
                            // ignore
                        }
                    }
                    return matches;
                }
            }
            return true;
        }

        @NonNull
        @Override
        public String getSourceName() {
            return getPayload().getRepository().getRepositoryName();
        }

        @NonNull
        @Override
        public Map<SCMHead, SCMRevision> heads(@NonNull SCMSource source) {
            if (!(source instanceof BitbucketSCMSource)) {
                return Collections.emptyMap();
            }
            BitbucketSCMSource src = (BitbucketSCMSource) source;
            if (!isServerUrlMatch(src.getServerUrl())) {
                return Collections.emptyMap();
            }
            if (!src.getRepoOwner().equalsIgnoreCase(getPayload().getRepository().getOwnerName())) {
                return Collections.emptyMap();
            }
            if (!src.getRepository().equalsIgnoreCase(getPayload().getRepository().getRepositoryName())) {
                return Collections.emptyMap();
            }

            BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none())
                    .withTraits(src.getTraits());
            if (!ctx.wantPRs()) {
                // doesn't want PRs, let the push event handle origin branches
                return Collections.emptyMap();
            }
            BitbucketPullRequest pull = getPayload().getPullRequest();
            String pullRepoOwner = pull.getSource().getRepository().getOwnerName();
            String pullRepository = pull.getSource().getRepository().getRepositoryName();
            SCMHeadOrigin headOrigin = src.originOf(pullRepoOwner, pullRepository);
            Set<ChangeRequestCheckoutStrategy> strategies =
                    headOrigin == SCMHeadOrigin.DEFAULT
                            ? ctx.originPRStrategies()
                            : ctx.forkPRStrategies();
            Map<SCMHead, SCMRevision> result = new HashMap<>(strategies.size());
            for (ChangeRequestCheckoutStrategy strategy : strategies) {
                String branchName = "PR-" + pull.getId();
                if (strategies.size() > 1) {
                    branchName = branchName + "-" + strategy.name().toLowerCase(Locale.ENGLISH);
                }
                String originalBranchName = pull.getSource().getBranch().getName();
                PullRequestSCMHead head;
                if (instanceType == BitbucketType.CLOUD) {
                    head = new PullRequestSCMHead(
                            branchName,
                            pullRepoOwner,
                            pullRepository,
                            originalBranchName,
                            pull,
                            headOrigin,
                            strategy
                    );
                } else {
                    head = new PullRequestSCMHead(
                            branchName,
                            src.getRepoOwner(),
                            src.getRepository(),
                            originalBranchName,
                            pull,
                            headOrigin,
                            strategy
                    );
                }
                if (hookEvent == PULL_REQUEST_DECLINED || hookEvent == PULL_REQUEST_MERGED) {
                    // special case for repo being deleted
                    result.put(head, null);
                } else {
                    String targetHash = pull.getDestination().getCommit().getHash();
                    String pullHash = pull.getSource().getCommit().getHash();

                    SCMRevision revision = new PullRequestSCMRevision<>(head,
                        new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(), targetHash),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, pullHash)
                    );
                    result.put(head, revision);
                }
            }
            return result;
        }

        @Override
        public boolean isMatch(@NonNull SCM scm) {
            // TODO
            return false;
        }

        @Override
        public Iterable<BitbucketPullRequest> getPullRequests(BitbucketSCMSource src) throws InterruptedException {
            if (hookEvent == PULL_REQUEST_DECLINED || hookEvent == PULL_REQUEST_MERGED) {
                return Collections.emptyList();
            }
            return Collections.singleton(getPayload().getPullRequest());
        }
    }
}
