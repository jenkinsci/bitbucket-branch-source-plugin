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

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSourceContext;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMRevision;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestEvent;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;

import static com.cloudbees.jenkins.plugins.bitbucket.hooks.HookEventType.PULL_REQUEST_DECLINED;
import static com.cloudbees.jenkins.plugins.bitbucket.hooks.HookEventType.PULL_REQUEST_MERGED;

final class PREvent extends AbstractSCMHeadEvent<BitbucketPullRequestEvent> implements HasPullRequests {
    private final HookEventType hookEvent;

    PREvent(Type type, BitbucketPullRequestEvent payload,
                 String origin,
                 HookEventType hookEvent) {
        super(type, payload, origin);
        this.hookEvent = hookEvent;
    }

    @Override
    protected BitbucketRepository getRepository() {
        return getPayload().getRepository();
    }

    @NonNull
    @Override
    public String getSourceName() {
        return getRepository().getRepositoryName();
    }

    @NonNull
    @Override
    @SuppressFBWarnings(value = "SBSC_USE_STRINGBUFFER_CONCATENATION", justification = "false positive, the scope of branchName variable is inside the for cycle, no string contatenation happens into a loop")
    public Map<SCMHead, SCMRevision> heads(@NonNull SCMSource source) {
        if (!(source instanceof BitbucketSCMSource)) {
            return Collections.emptyMap();
        }
        BitbucketSCMSource src = (BitbucketSCMSource) source;
        if (!isServerURLMatch(src.getServerUrl())) {
            return Collections.emptyMap();
        }
        BitbucketRepository repository = getRepository();
        if (!src.getRepoOwner().equalsIgnoreCase(repository.getOwnerName())) {
            return Collections.emptyMap();
        }
        if (!src.getRepository().equalsIgnoreCase(repository.getRepositoryName())) {
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
            PullRequestSCMHead head = new PullRequestSCMHead(
                branchName,
                pullRepoOwner,
                pullRepository,
                originalBranchName,
                pull,
                headOrigin,
                strategy
            );
            if (hookEvent == PULL_REQUEST_DECLINED || hookEvent == PULL_REQUEST_MERGED) {
                // special case for repo being deleted
                result.put(head, null);
            } else {
                String targetHash = pull.getDestination().getCommit().getHash();
                String pullHash = pull.getSource().getCommit().getHash();

                SCMRevision revision = new PullRequestSCMRevision(head,
                    new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(), targetHash),
                    new AbstractGitSCMSource.SCMRevisionImpl(head, pullHash)
                );
                result.put(head, revision);
            }
        }
        return result;
    }

    @Override
    public Iterable<BitbucketPullRequest> getPullRequests(BitbucketSCMSource src) throws InterruptedException {
        if (hookEvent == PULL_REQUEST_DECLINED || hookEvent == PULL_REQUEST_MERGED) {
            return Collections.emptyList();
        }
        return Collections.singleton(getPayload().getPullRequest());
    }
}
