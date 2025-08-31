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
package com.cloudbees.jenkins.plugins.bitbucket.trait;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSourceContext;
import java.util.EnumSet;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

class OriginPullRequestDiscoveryTraitTest {

    @Test
    void given__discoverHeadMerge__when__appliedToContext__then__strategiesCorrect() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches()).isFalse();
        assumeThat(ctx.wantPRs()).isFalse();
        assumeThat(ctx.prefilters().isEmpty()).isTrue();
        assumeThat(ctx.filters().isEmpty()).isTrue();
        assumeThat(ctx.authorities().stream()).hasAtLeastOneElementOfType(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class);

        OriginPullRequestDiscoveryTrait instance = new OriginPullRequestDiscoveryTrait(
                EnumSet.allOf(ChangeRequestCheckoutStrategy.class)
        );
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches()).isFalse();
        assertThat(ctx.wantPRs()).isTrue();
        assertThat(ctx.prefilters()).isEmpty();
        assertThat(ctx.filters()).isEmpty();
        assertThat(ctx.originPRStrategies()).isEmpty();
        assertThat(ctx.authorities()).hasAtLeastOneElementOfType(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class);
    }

    @Test
    void given__discoverHeadOnly__when__appliedToContext__then__strategiesCorrect() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches()).isFalse();
        assumeThat(ctx.wantPRs()).isFalse();
        assumeThat(ctx.prefilters().isEmpty()).isTrue();
        assumeThat(ctx.filters().isEmpty()).isTrue();
        assumeThat(ctx.authorities().stream()).hasAtLeastOneElementOfType(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class);

        OriginPullRequestDiscoveryTrait instance = new OriginPullRequestDiscoveryTrait(
                EnumSet.of(ChangeRequestCheckoutStrategy.HEAD)
        );
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches()).isFalse();
        assertThat(ctx.wantPRs()).isTrue();
        assertThat(ctx.prefilters()).isEmpty();
        assertThat(ctx.filters()).isEmpty();
        assertThat(ctx.originPRStrategies()).containsOnly(ChangeRequestCheckoutStrategy.HEAD);
        assertThat(ctx.authorities()).hasAtLeastOneElementOfType(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class);
    }

    @Test
    void given__discoverMergeOnly__when__appliedToContext__then__strategiesCorrect() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches()).isFalse();
        assumeThat(ctx.wantPRs()).isFalse();
        assumeThat(ctx.prefilters().isEmpty()).isTrue();
        assumeThat(ctx.filters().isEmpty()).isTrue();
        assumeThat(ctx.authorities().stream()).hasAtLeastOneElementOfType(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class);

        OriginPullRequestDiscoveryTrait instance = new OriginPullRequestDiscoveryTrait(
                EnumSet.of(ChangeRequestCheckoutStrategy.MERGE)
        );
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches()).isFalse();
        assertThat(ctx.wantPRs()).isTrue();
        assertThat(ctx.prefilters()).isEmpty();
        assertThat(ctx.filters()).isEmpty();
        assertThat(ctx.originPRStrategies()).containsOnly(ChangeRequestCheckoutStrategy.MERGE);
        assertThat(ctx.authorities()).hasAtLeastOneElementOfType(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class);
    }

    @Test
    void given__programmaticConstructor__when__appliedToContext__then__strategiesCorrect() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches()).isFalse();
        assumeThat(ctx.wantPRs()).isFalse();
        assumeThat(ctx.prefilters().isEmpty()).isTrue();
        assumeThat(ctx.filters().isEmpty()).isTrue();
        assumeThat(ctx.authorities().stream()).hasAtLeastOneElementOfType(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class);

        OriginPullRequestDiscoveryTrait instance =
                new OriginPullRequestDiscoveryTrait(EnumSet.allOf(ChangeRequestCheckoutStrategy.class));
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches()).isFalse();
        assertThat(ctx.wantPRs()).isTrue();
        assertThat(ctx.prefilters()).isEmpty();
        assertThat(ctx.filters()).isEmpty();
        assertThat(ctx.originPRStrategies()).isEmpty();
        assertThat(ctx.authorities()).hasAtLeastOneElementOfType(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class);
    }
}
