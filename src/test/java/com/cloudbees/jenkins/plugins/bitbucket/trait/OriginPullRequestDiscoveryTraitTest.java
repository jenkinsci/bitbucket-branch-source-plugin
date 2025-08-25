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
import java.util.Collections;
import java.util.EnumSet;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMHeadFilter;
import jenkins.scm.api.trait.SCMHeadPrefilter;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OriginPullRequestDiscoveryTraitTest {

    @Test
    void given__discoverHeadMerge__when__appliedToContext__then__strategiesCorrect() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeFalse(ctx.wantBranches());
        assumeFalse(ctx.wantPRs());
        assumeTrue(ctx.prefilters().isEmpty());
        assumeTrue(ctx.filters().isEmpty());
        assumeTrue(ctx.authorities().stream().anyMatch(item -> item instanceof OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority));

        OriginPullRequestDiscoveryTrait instance = new OriginPullRequestDiscoveryTrait(
                EnumSet.allOf(ChangeRequestCheckoutStrategy.class)
        );
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.originPRStrategies(),
                Matchers.is(EnumSet.allOf(ChangeRequestCheckoutStrategy.class)));
        assertThat(ctx.authorities(), hasItem(
                instanceOf(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class)
        ));
    }

    @Test
    void given__discoverHeadOnly__when__appliedToContext__then__strategiesCorrect() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeFalse(ctx.wantBranches());
        assumeFalse(ctx.wantPRs());
        assumeTrue(ctx.prefilters().isEmpty());
        assumeTrue(ctx.filters().isEmpty());
        assumeTrue(ctx.authorities().stream().anyMatch(item -> item instanceof OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority));

        OriginPullRequestDiscoveryTrait instance = new OriginPullRequestDiscoveryTrait(
                EnumSet.of(ChangeRequestCheckoutStrategy.HEAD)
        );
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.originPRStrategies(),
                Matchers.is(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD)));
        assertThat(ctx.authorities(), hasItem(
                instanceOf(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class)
        ));
    }

    @Test
    void given__discoverMergeOnly__when__appliedToContext__then__strategiesCorrect() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeFalse(ctx.wantBranches());
        assumeFalse(ctx.wantPRs());
        assumeTrue(ctx.prefilters().isEmpty());
        assumeTrue(ctx.filters().isEmpty());
        assumeTrue(ctx.authorities().stream().anyMatch(item -> item instanceof OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority));

        OriginPullRequestDiscoveryTrait instance = new OriginPullRequestDiscoveryTrait(
                EnumSet.of(ChangeRequestCheckoutStrategy.MERGE)
        );
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.originPRStrategies(),
                Matchers.is(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE)));
        assertThat(ctx.authorities(), hasItem(
                instanceOf(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class)
        ));
    }

    @Test
    void given__programmaticConstructor__when__appliedToContext__then__strategiesCorrect() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeFalse(ctx.wantBranches());
        assumeFalse(ctx.wantPRs());
        assumeTrue(ctx.prefilters().isEmpty());
        assumeTrue(ctx.filters().isEmpty());
        assumeTrue(ctx.authorities().stream().anyMatch(item -> item instanceof OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority));

        OriginPullRequestDiscoveryTrait instance =
                new OriginPullRequestDiscoveryTrait(EnumSet.allOf(ChangeRequestCheckoutStrategy.class));
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.originPRStrategies(),
                Matchers.is(EnumSet.allOf(ChangeRequestCheckoutStrategy.class)));
        assertThat(ctx.authorities(), hasItem(
                instanceOf(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class)
        ));
    }
}
