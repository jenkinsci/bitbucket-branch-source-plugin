package com.cloudbees.jenkins.plugins.bitbucket;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMHeadFilter;
import jenkins.scm.api.trait.SCMHeadPrefilter;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Test;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeThat;

public class ForkPullRequestDiscoveryTraitTest {
    @Test
    public void given__discoverHeadMerge__when__appliedToContext__then__strategiesCorrect() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(ctx.authorities(), not((Matcher) hasItem(
                instanceOf(ForkPullRequestDiscoveryTrait.TrustTeamForks.class)
        )));
        ForkPullRequestDiscoveryTrait instance = new ForkPullRequestDiscoveryTrait(
                EnumSet.allOf(ChangeRequestCheckoutStrategy.class),
                new ForkPullRequestDiscoveryTrait.TrustTeamForks()
        );
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.forkPRStrategies(),
                Matchers.<Set<ChangeRequestCheckoutStrategy>>is(EnumSet.allOf(ChangeRequestCheckoutStrategy.class)));
        assertThat(ctx.authorities(), (Matcher) hasItem(
                instanceOf(ForkPullRequestDiscoveryTrait.TrustTeamForks.class)
        ));
    }

    @Test
    public void given__discoverHeadOnly__when__appliedToContext__then__strategiesCorrect() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(ctx.authorities(), not((Matcher) hasItem(
                instanceOf(ForkPullRequestDiscoveryTrait.TrustTeamForks.class)
        )));
        ForkPullRequestDiscoveryTrait instance = new ForkPullRequestDiscoveryTrait(
                EnumSet.of(ChangeRequestCheckoutStrategy.HEAD),
                new ForkPullRequestDiscoveryTrait.TrustTeamForks()
        );
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.forkPRStrategies(),
                Matchers.<Set<ChangeRequestCheckoutStrategy>>is(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD)));
        assertThat(ctx.authorities(), (Matcher) hasItem(
                instanceOf(ForkPullRequestDiscoveryTrait.TrustTeamForks.class)
        ));
    }

    @Test
    public void given__discoverMergeOnly__when__appliedToContext__then__strategiesCorrect() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(ctx.authorities(), not((Matcher) hasItem(
                instanceOf(ForkPullRequestDiscoveryTrait.TrustTeamForks.class)
        )));
        ForkPullRequestDiscoveryTrait instance = new ForkPullRequestDiscoveryTrait(
                EnumSet.of(ChangeRequestCheckoutStrategy.MERGE),
                new ForkPullRequestDiscoveryTrait.TrustTeamForks()
        );
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.forkPRStrategies(),
                Matchers.<Set<ChangeRequestCheckoutStrategy>>is(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE)));
        assertThat(ctx.authorities(), (Matcher) hasItem(
                instanceOf(ForkPullRequestDiscoveryTrait.TrustTeamForks.class)
        ));
    }

    @Test
    public void given__nonDefaultTrust__when__appliedToContext__then__authoritiesCorrect() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(ctx.authorities(), not((Matcher) hasItem(
                instanceOf(ForkPullRequestDiscoveryTrait.TrustTeamForks.class)
        )));
        ForkPullRequestDiscoveryTrait instance = new ForkPullRequestDiscoveryTrait(
                EnumSet.allOf(ChangeRequestCheckoutStrategy.class),
                new ForkPullRequestDiscoveryTrait.TrustEveryone()
        );
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.forkPRStrategies(),
                Matchers.<Set<ChangeRequestCheckoutStrategy>>is(EnumSet.allOf(ChangeRequestCheckoutStrategy.class)));
        assertThat(ctx.authorities(), (Matcher) hasItem(
                instanceOf(ForkPullRequestDiscoveryTrait.TrustEveryone.class)
        ));
    }
}
