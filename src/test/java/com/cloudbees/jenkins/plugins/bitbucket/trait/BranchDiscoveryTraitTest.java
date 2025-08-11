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
import java.util.Collections;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.trait.SCMHeadFilter;
import jenkins.scm.api.trait.SCMHeadPrefilter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@WithJenkins
class BranchDiscoveryTraitTest {

    private static JenkinsRule j;

    @BeforeAll
    static void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void given__discoverAll__when__appliedToContext__then__noFilter() {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeFalse(ctx.wantBranches());
        assumeFalse(ctx.wantPRs());
        assumeTrue(ctx.prefilters().isEmpty());
        assumeTrue(ctx.filters().isEmpty());
        assumeTrue(ctx.authorities().stream().anyMatch(item -> item instanceof BranchDiscoveryTrait.BranchSCMHeadAuthority));

        BranchDiscoveryTrait instance = new BranchDiscoveryTrait(true, true);
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(true));
        assertThat(ctx.wantPRs(), is(false));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.authorities(), hasItem(
                instanceOf(BranchDiscoveryTrait.BranchSCMHeadAuthority.class)
        ));
    }

    @Test
    void given__excludingPRs__when__appliedToContext__then__filter() {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeFalse(ctx.wantBranches());
        assumeFalse(ctx.wantPRs());
        assumeTrue(ctx.prefilters().isEmpty());
        assumeTrue(ctx.filters().isEmpty());
        assumeTrue(ctx.authorities().stream().anyMatch(item -> item instanceof BranchDiscoveryTrait.BranchSCMHeadAuthority));

        BranchDiscoveryTrait instance = new BranchDiscoveryTrait(true, false);
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(true));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(),
                contains(instanceOf(BranchDiscoveryTrait.ExcludeOriginPRBranchesSCMHeadFilter.class)));
        assertThat(ctx.authorities(), hasItem(
                instanceOf(BranchDiscoveryTrait.BranchSCMHeadAuthority.class)
        ));
    }

    @Test
    void given__onlyPRs__when__appliedToContext__then__filter() {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeFalse(ctx.wantBranches());
        assumeFalse(ctx.wantPRs());
        assumeTrue(ctx.prefilters().isEmpty());
        assumeTrue(ctx.filters().isEmpty());
        assumeTrue(ctx.authorities().stream().anyMatch(item -> item instanceof BranchDiscoveryTrait.BranchSCMHeadAuthority));

        BranchDiscoveryTrait instance = new BranchDiscoveryTrait(false, true);
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(true));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), contains(instanceOf(BranchDiscoveryTrait.OnlyOriginPRBranchesSCMHeadFilter.class)));
        assertThat(ctx.authorities(), hasItem(
                instanceOf(BranchDiscoveryTrait.BranchSCMHeadAuthority.class)
        ));
    }

    @Test
    void given__descriptor__when__displayingOptions__then__allThreePresent() {
        ListBoxModel options =
                j.jenkins.getDescriptorByType(BranchDiscoveryTrait.DescriptorImpl.class).doFillStrategyIdItems();
        assertThat(options.size(), is(3));
        assertThat(options.get(0).value, is("1"));
        assertThat(options.get(1).value, is("2"));
        assertThat(options.get(2).value, is("3"));
    }

}
