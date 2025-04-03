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
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketTagSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.BranchSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.trait.DiscardOldTagTrait.ExcludeOldSCMTag;
import java.util.Date;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.trait.SCMHeadPrefilter;
import jenkins.scm.impl.NullSCMSource;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscardOldTagTraitTest {

    @Test
    void verify_that_tag_is_not_excluded_if_recent() {
        DiscardOldTagTrait trait = new DiscardOldTagTrait(5);
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        trait.decorateContext(ctx);
        assertThat(ctx.prefilters()).hasAtLeastOneElementOfType(ExcludeOldSCMTag.class);

        Date now = new Date();

        BitbucketTagSCMHead head = new BitbucketTagSCMHead("tag/1234", now.getTime());

        for (SCMHeadPrefilter filter : ctx.prefilters()) {
            assertThat(filter.isExcluded(new NullSCMSource(), head)).isFalse();
        }
    }

    @Test
    void verify_that_branch_is_not_excluded() {
        DiscardOldTagTrait trait = new DiscardOldTagTrait(5);
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        trait.decorateContext(ctx);
        assertThat(ctx.prefilters()).hasAtLeastOneElementOfType(ExcludeOldSCMTag.class);

        SCMHead head = new BranchSCMHead("someBranch");

        for (SCMHeadPrefilter filter : ctx.prefilters()) {
            assertThat(filter.isExcluded(new NullSCMSource(), head)).isFalse();
        }
    }

    @Test
    void verify_that_branch_is_excluded_if_expired() {
        DiscardOldTagTrait trait = new DiscardOldTagTrait(5);
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        trait.decorateContext(ctx);
        assertThat(ctx.prefilters()).hasAtLeastOneElementOfType(ExcludeOldSCMTag.class);

        Date now = new Date();

        BitbucketTagSCMHead head = new BitbucketTagSCMHead("tag/1234", DateUtils.addDays(now, -6).getTime());

        for (SCMHeadPrefilter filter : ctx.prefilters()) {
            assertThat(filter.isExcluded(new NullSCMSource(), head)).isTrue();
        }
    }

}
