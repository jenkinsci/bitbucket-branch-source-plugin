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

public class OriginPullRequestDiscoveryTraitTest {
    @Test
    public void given__discoverHeadMerge__when__appliedToContext__then__strategiesCorrect() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(ctx.authorities(), not((Matcher) hasItem(
                instanceOf(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class)
        )));
        OriginPullRequestDiscoveryTrait instance = new OriginPullRequestDiscoveryTrait(
                EnumSet.allOf(ChangeRequestCheckoutStrategy.class)
        );
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.originPRStrategies(),
                Matchers.<Set<ChangeRequestCheckoutStrategy>>is(EnumSet.allOf(ChangeRequestCheckoutStrategy.class)));
        assertThat(ctx.authorities(), (Matcher) hasItem(
                instanceOf(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class)
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
                instanceOf(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class)
        )));
        OriginPullRequestDiscoveryTrait instance = new OriginPullRequestDiscoveryTrait(
                EnumSet.of(ChangeRequestCheckoutStrategy.HEAD)
        );
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.originPRStrategies(),
                Matchers.<Set<ChangeRequestCheckoutStrategy>>is(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD)));
        assertThat(ctx.authorities(), (Matcher) hasItem(
                instanceOf(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class)
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
                instanceOf(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class)
        )));
        OriginPullRequestDiscoveryTrait instance = new OriginPullRequestDiscoveryTrait(
                EnumSet.of(ChangeRequestCheckoutStrategy.MERGE)
        );
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.originPRStrategies(),
                Matchers.<Set<ChangeRequestCheckoutStrategy>>is(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE)));
        assertThat(ctx.authorities(), (Matcher) hasItem(
                instanceOf(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class)
        ));
    }

    @Test
    public void given__programmaticConstructor__when__appliedToContext__then__strategiesCorrect() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(ctx.authorities(), not((Matcher) hasItem(
                instanceOf(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class)
        )));
        OriginPullRequestDiscoveryTrait instance =
                new OriginPullRequestDiscoveryTrait(EnumSet.allOf(ChangeRequestCheckoutStrategy.class));
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(false));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.originPRStrategies(),
                Matchers.<Set<ChangeRequestCheckoutStrategy>>is(EnumSet.allOf(ChangeRequestCheckoutStrategy.class)));
        assertThat(ctx.authorities(), (Matcher) hasItem(
                instanceOf(OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority.class)
        ));
    }
}
