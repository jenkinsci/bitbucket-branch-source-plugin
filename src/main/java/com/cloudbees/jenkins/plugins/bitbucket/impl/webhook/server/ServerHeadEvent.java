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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.server;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSourceContext;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMRevision;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.api.HasPullRequests;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketServerRepository;
import com.cloudbees.jenkins.plugins.bitbucket.server.events.NativeServerPullRequestEvent;
import com.google.common.base.Ascii;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;

final class ServerHeadEvent extends AbstractNativeServerSCMHeadEvent<NativeServerPullRequestEvent> implements HasPullRequests {
    ServerHeadEvent(String serverUrl, Type type, NativeServerPullRequestEvent payload, String origin) {
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
            final PullRequestSCMHead head = new PullRequestSCMHead(
                branchName,
                sourceRepo.getOwnerName(),
                sourceRepo.getRepositoryName(),
                originalBranchName,
                pullRequest,
                headOrigin,
                strategy
            );

            switch (getType()) {
                case CREATED,
                     UPDATED:
                    final String targetHash = pullRequest.getDestination().getCommit().getHash();
                    final String pullHash = pullRequest.getSource().getCommit().getHash();
                    result.put(head,
                        new PullRequestSCMRevision(head,
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
