package com.cloudbees.jenkins.plugins.bitbucket;

import hudson.util.ListBoxModel;
import java.util.Collections;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.trait.SCMHeadFilter;
import jenkins.scm.api.trait.SCMHeadPrefilter;
import org.hamcrest.Matcher;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeThat;

public class BranchDiscoveryTraitTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Test
    public void given__discoverAll__when__appliedToContext__then__noFilter() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(ctx.authorities(), not((Matcher) hasItem(
                instanceOf(BranchDiscoveryTrait.BranchSCMHeadAuthority.class)
        )));
        BranchDiscoveryTrait instance = new BranchDiscoveryTrait(true, true);
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(true));
        assertThat(ctx.wantPRs(), is(false));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.authorities(), (Matcher) hasItem(
                instanceOf(BranchDiscoveryTrait.BranchSCMHeadAuthority.class)
        ));
    }

    @Test
    public void given__excludingPRs__when__appliedToContext__then__filter() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(ctx.authorities(), not((Matcher) hasItem(
                instanceOf(BranchDiscoveryTrait.BranchSCMHeadAuthority.class)
        )));
        BranchDiscoveryTrait instance = new BranchDiscoveryTrait(true, false);
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(true));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(),
                contains(instanceOf(BranchDiscoveryTrait.ExcludeOriginPRBranchesSCMHeadFilter.class)));
        assertThat(ctx.authorities(), (Matcher) hasItem(
                instanceOf(BranchDiscoveryTrait.BranchSCMHeadAuthority.class)
        ));
    }

    @Test
    public void given__onlyPRs__when__appliedToContext__then__filter() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(ctx.authorities(), not((Matcher) hasItem(
                instanceOf(BranchDiscoveryTrait.BranchSCMHeadAuthority.class)
        )));
        BranchDiscoveryTrait instance = new BranchDiscoveryTrait(false, true);
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(true));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), contains(instanceOf(BranchDiscoveryTrait.OnlyOriginPRBranchesSCMHeadFilter.class)));
        assertThat(ctx.authorities(), (Matcher) hasItem(
                instanceOf(BranchDiscoveryTrait.BranchSCMHeadAuthority.class)
        ));
    }

    @Test
    public void given__descriptor__when__displayingOptions__then__allThreePresent() {
        ListBoxModel options =
                j.jenkins.getDescriptorByType(BranchDiscoveryTrait.DescriptorImpl.class).doFillStrategyIdItems();
        assertThat(options.size(), is(3));
        assertThat(options.get(0).value, is("1"));
        assertThat(options.get(1).value, is("2"));
        assertThat(options.get(2).value, is("3"));
    }

}
