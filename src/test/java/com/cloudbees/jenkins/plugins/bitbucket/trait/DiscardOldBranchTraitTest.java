/*
 * The MIT License
 *
 * Copyright (c) 2025, CloudBees, Inc.
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

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSourceContext;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSourceRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBranch;
import com.cloudbees.jenkins.plugins.bitbucket.trait.DiscardOldBranchTrait.ExcludeOldSCMHeadBranch;
import java.util.Arrays;
import java.util.Date;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.trait.SCMHeadFilter;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscardOldBranchTraitTest {

    @Test
    void verify_that_branch_is_not_excluded_if_has_recent_commits() throws Exception {
        DiscardOldBranchTrait trait = new DiscardOldBranchTrait(10);
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        trait.decorateContext(ctx);
        assertThat(ctx.filters()).hasAtLeastOneElementOfType(ExcludeOldSCMHeadBranch.class);

        Date now = new Date();

        SCMHead head = mock(SCMHead.class);
        when(head.getName()).thenReturn("feature/release");

        BitbucketSCMSourceRequest request = prepareRequest(
                buildBranch("feature/xyz", DateUtils.addDays(now, -1).getTime()),
                buildBranch("feature/release", DateUtils.addDays(now, -10).getTime())
        );

        for (SCMHeadFilter filter : ctx.filters()) {
            assertThat(filter.isExcluded(request, head)).isFalse();
        }
    }

    @Test
    void verify_that_branch_is_excluded_if_has_head_commit_older_than_specified() throws Exception {
        DiscardOldBranchTrait trait = new DiscardOldBranchTrait(5);
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        trait.decorateContext(ctx);
        assertThat(ctx.filters()).hasAtLeastOneElementOfType(ExcludeOldSCMHeadBranch.class);

        Date now = new Date();

        SCMHead head = mock(SCMHead.class);
        when(head.getName()).thenReturn("feature/release");

        BitbucketSCMSourceRequest request = prepareRequest(
                buildBranch("feature/xyz", DateUtils.addDays(now, -6).getTime()),
                buildBranch("feature/release", DateUtils.addDays(now, -10).getTime())
        );

        for (SCMHeadFilter filter : ctx.filters()) {
            assertThat(filter.isExcluded(request, head)).isTrue();
        }
    }

    private BitbucketBranch buildBranch(String name, long date) {
        BitbucketBranch branch = mock(BitbucketBranch.class);
        when(branch.getName()).thenReturn(name);
        when(branch.getDateMillis()).thenReturn(date);
        return branch;
    }

    private BitbucketSCMSourceRequest prepareRequest(BitbucketBranch ...branches) {
        BitbucketSCMSourceRequest request = mock(BitbucketSCMSourceRequest.class);
        when(request.getBranches()).thenReturn(Arrays.asList(branches));
        return request;
    }

}
