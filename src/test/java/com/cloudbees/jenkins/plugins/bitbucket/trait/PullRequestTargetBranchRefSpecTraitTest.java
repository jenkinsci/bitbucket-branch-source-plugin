/*
 * The MIT License
 *
 * Copyright (c) 2025, Falco Nikolas
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
package com.cloudbees.jenkins.plugins.bitbucket.trait;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketGitSCMBuilder;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketGitSCMRevision;
import com.cloudbees.jenkins.plugins.bitbucket.BranchSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMHead;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.scm.api.SCMHead;
import org.assertj.core.api.Assertions;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PullRequestTargetBranchRefSpecTraitTest {

    @Test
    void verify_that_pull_request_target_branch_is_added_as_ref_spec() throws Exception {
        PullRequestSCMHead head = mock(PullRequestSCMHead.class);
        when(head.getTarget()).thenReturn(new SCMHead("support/1.x"));
        BitbucketGitSCMRevision revision = mock(BitbucketGitSCMRevision.class);
        GitSCMBuilder<BitbucketGitSCMBuilder> ctx = new GitSCMBuilder<>(head, revision, "origin", null);

        PullRequestTargetBranchRefSpecTrait trait = new PullRequestTargetBranchRefSpecTrait();
        trait.decorateBuilder(ctx);

        Assertions.assertThat(ctx.asRefSpecs()).contains(new RefSpec("+refs/heads/support/1.x:refs/remotes/origin/support/1.x"));
    }

    @Test
    void verify_that_no_ref_spec_is_added_for_non_pull_request() throws Exception {
        BranchSCMHead head = mock(BranchSCMHead.class);
        when(head.getName()).thenReturn("support/1.x");
        BitbucketGitSCMRevision revision = mock(BitbucketGitSCMRevision.class);
        GitSCMBuilder<BitbucketGitSCMBuilder> ctx = new GitSCMBuilder<>(head, revision, "origin", null);

        PullRequestTargetBranchRefSpecTrait trait = new PullRequestTargetBranchRefSpecTrait();
        trait.decorateBuilder(ctx);

        Assertions.assertThat(ctx.asRefSpecs()).containsOnly(new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
    }

}
