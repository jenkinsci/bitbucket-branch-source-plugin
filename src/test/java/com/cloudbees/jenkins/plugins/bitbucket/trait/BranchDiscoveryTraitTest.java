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
import hudson.util.ListBoxModel;
import jenkins.scm.api.SCMHeadObserver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BranchDiscoveryTraitTest {

    @Test
    void given__discoverAll__when__appliedToContext__then__noFilter() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches()).isFalse();
        assumeThat(ctx.wantPRs()).isFalse();
        assumeThat(ctx.prefilters().isEmpty()).isTrue();
        assumeThat(ctx.filters().isEmpty()).isTrue();
        assumeThat(ctx.authorities().stream()).hasAtLeastOneElementOfType(BranchDiscoveryTrait.BranchSCMHeadAuthority.class);

        BranchDiscoveryTrait instance = new BranchDiscoveryTrait(true, true);
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches()).isTrue();
        assertThat(ctx.wantPRs()).isFalse();
        assertThat(ctx.prefilters()).isEmpty();
        assertThat(ctx.filters()).isEmpty();
        assertThat(ctx.authorities()).hasAtLeastOneElementOfType(BranchDiscoveryTrait.BranchSCMHeadAuthority.class);
    }

    @Test
    void given__excludingPRs__when__appliedToContext__then__filter() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches()).isFalse();
        assumeThat(ctx.wantPRs()).isFalse();
        assumeThat(ctx.prefilters().isEmpty()).isTrue();
        assumeThat(ctx.filters().isEmpty()).isTrue();
        assumeThat(ctx.authorities().stream()).hasAtLeastOneElementOfType(BranchDiscoveryTrait.BranchSCMHeadAuthority.class);

        BranchDiscoveryTrait instance = new BranchDiscoveryTrait(true, false);
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches()).isTrue();
        assertThat(ctx.wantPRs()).isTrue();
        assertThat(ctx.prefilters()).isEmpty();
        assertThat(ctx.filters()).hasAtLeastOneElementOfType(BranchDiscoveryTrait.ExcludeOriginPRBranchesSCMHeadFilter.class);
        assertThat(ctx.authorities()).hasAtLeastOneElementOfType(BranchDiscoveryTrait.BranchSCMHeadAuthority.class);
    }

    @Test
    void given__onlyPRs__when__appliedToContext__then__filter() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeFalse(ctx.wantBranches());
        assumeFalse(ctx.wantPRs());
        assumeTrue(ctx.prefilters().isEmpty());
        assumeTrue(ctx.filters().isEmpty());
        assumeTrue(ctx.authorities().stream().anyMatch(item -> item instanceof BranchDiscoveryTrait.BranchSCMHeadAuthority));

        BranchDiscoveryTrait instance = new BranchDiscoveryTrait(false, true);
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches()).isTrue();
        assertThat(ctx.wantPRs()).isTrue();
        assertThat(ctx.prefilters()).isEmpty();
        assertThat(ctx.filters()).hasAtLeastOneElementOfType(BranchDiscoveryTrait.OnlyOriginPRBranchesSCMHeadFilter.class);
        assertThat(ctx.authorities()).hasAtLeastOneElementOfType(BranchDiscoveryTrait.BranchSCMHeadAuthority.class);
    }

    @Test
    void given__descriptor__when__displayingOptions__then__allThreePresent() {
        ListBoxModel options = new BranchDiscoveryTrait.DescriptorImpl().doFillStrategyIdItems();
        assertThat(options).hasSize(3);
        assertThat(options.get(0).value).isEqualTo("1");
        assertThat(options.get(1).value).isEqualTo("2");
        assertThat(options.get(2).value).isEqualTo("3");
    }

}
