/*
 * The MIT License
 *
 * Copyright (c) 2025, Madis Parn
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
import java.util.stream.Stream;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.trait.SCMHeadPrefilter;
import jenkins.scm.impl.NullSCMSource;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class DiscardOldTagTraitTest {

    static Stream<Arguments> tagSCMHeadProvider() {
        return Stream.of(
            Arguments.argumentSet("expired", new BitbucketTagSCMHead("tag/1234", DateUtils.addDays(new Date(), -6).getTime()), true),
            Arguments.argumentSet("too_recent", new BitbucketTagSCMHead("tag/1234", DateUtils.addDays(new Date(), -4).getTime()), false),
            Arguments.argumentSet("no_timestamp", new BitbucketTagSCMHead("tag/zer0", 0L), false),
            Arguments.argumentSet("not_a_tag", new BranchSCMHead("someBranch"), false)
        );
    }

    @ParameterizedTest
    @MethodSource("tagSCMHeadProvider")
    void verify_that_tag_is_not_excluded(SCMHead head, boolean expectedResult) {
        DiscardOldTagTrait trait = new DiscardOldTagTrait(5);
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        trait.decorateContext(ctx);
        assertThat(ctx.prefilters()).hasAtLeastOneElementOfType(ExcludeOldSCMTag.class);

        for (SCMHeadPrefilter filter : ctx.prefilters()) {
            assertThat(filter.isExcluded(new NullSCMSource(), head)).isEqualTo(expectedResult);
        }
    }

}
