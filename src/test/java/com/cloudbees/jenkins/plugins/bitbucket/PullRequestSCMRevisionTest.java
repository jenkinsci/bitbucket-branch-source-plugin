/*
 * The MIT License
 *
 * Copyright (c) 2026, CloudBees, Inc.
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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBranch;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestDestination;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketReviewer;
import com.cloudbees.jenkins.plugins.bitbucket.api.PullRequestBranchType;
import java.util.List;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PullRequestSCMRevisionTest {

    @Test
    void equivalentWhenPullRequestMetadataIsUnchanged() {
        PullRequestSCMRevision first = revision(pullRequest("Build me", "Validate me", reviewer("alice", false)));
        PullRequestSCMRevision second = revision(pullRequest("Build me", "Validate me", reviewer("alice", false)));

        assertThat(first.equivalent(second)).isTrue();
    }

    @Test
    void notEquivalentWhenPullRequestTitleChanges() {
        PullRequestSCMRevision first = revision(pullRequest("Build me", "Validate me", reviewer("alice", false)));
        PullRequestSCMRevision second = revision(pullRequest("Build me again", "Validate me", reviewer("alice", false)));

        assertThat(first.equivalent(second)).isFalse();
    }

    @Test
    void notEquivalentWhenPullRequestDescriptionChanges() {
        PullRequestSCMRevision first = revision(pullRequest("Build me", "Validate me", reviewer("alice", false)));
        PullRequestSCMRevision second = revision(pullRequest("Build me", "Validate me again", reviewer("alice", false)));

        assertThat(first.equivalent(second)).isFalse();
    }

    @Test
    void notEquivalentWhenPullRequestReviewerListChanges() {
        PullRequestSCMRevision first = revision(pullRequest("Build me", "Validate me", reviewer("alice", false)));
        PullRequestSCMRevision second = revision(pullRequest("Build me", "Validate me", reviewer("bob", false)));

        assertThat(first.equivalent(second)).isFalse();
    }

    @Test
    void equivalentWhenPullRequestReviewersAreReordered() {
        PullRequestSCMRevision first = revision(pullRequest("Build me", "Validate me",
                reviewer("alice", false), reviewer("bob", true)));
        PullRequestSCMRevision second = revision(pullRequest("Build me", "Validate me",
                reviewer("bob", true), reviewer("alice", false)));

        assertThat(first.equivalent(second)).isTrue();
    }

    private PullRequestSCMRevision revision(BitbucketPullRequest pullRequest) {
        PullRequestSCMHead head = new PullRequestSCMHead(
                "PR-1",
                "example",
                "repository",
                "feature",
                pullRequest,
                SCMHeadOrigin.DEFAULT,
                ChangeRequestCheckoutStrategy.HEAD);
        return new PullRequestSCMRevision(
                head,
                new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(), "target"),
                new AbstractGitSCMSource.SCMRevisionImpl(head, "pull"));
    }

    private BitbucketPullRequest pullRequest(String title, String description, BitbucketReviewer... reviewers) {
        BitbucketPullRequest pullRequest = mock(BitbucketPullRequest.class);
        BitbucketPullRequestSource source = mock(BitbucketPullRequestSource.class);
        BitbucketPullRequestDestination destination = mock(BitbucketPullRequestDestination.class);
        BitbucketBranch sourceBranch = mock(BitbucketBranch.class);
        BitbucketBranch destinationBranch = mock(BitbucketBranch.class);

        when(sourceBranch.getName()).thenReturn("feature");
        when(destinationBranch.getName()).thenReturn("main");
        when(source.getBranch()).thenReturn(sourceBranch);
        when(source.getBranchType()).thenReturn(PullRequestBranchType.BRANCH);
        when(destination.getBranch()).thenReturn(destinationBranch);
        when(pullRequest.getSource()).thenReturn(source);
        when(pullRequest.getDestination()).thenReturn(destination);
        when(pullRequest.getId()).thenReturn("1");
        when(pullRequest.getTitle()).thenReturn(title);
        when(pullRequest.getDescription()).thenReturn(description);
        when(pullRequest.getReviewers()).thenReturn(List.of(reviewers));
        return pullRequest;
    }

    private BitbucketReviewer reviewer(String id, boolean approved) {
        BitbucketReviewer.User user = new BitbucketReviewer.User();
        user.setIdentifier(id);
        BitbucketReviewer reviewer = new BitbucketReviewer();
        reviewer.setUser(user);
        reviewer.setApproved(approved);
        return reviewer;
    }
}
