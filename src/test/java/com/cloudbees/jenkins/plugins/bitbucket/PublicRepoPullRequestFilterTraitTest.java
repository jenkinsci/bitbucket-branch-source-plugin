package com.cloudbees.jenkins.plugins.bitbucket;

import jenkins.scm.api.SCMHeadObserver;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeThat;

public class PublicRepoPullRequestFilterTraitTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Test
    public void given__instance__when__decoratingContext__then__filterApplied() throws Exception {
        PublicRepoPullRequestFilterTrait instance = new PublicRepoPullRequestFilterTrait();
        BitbucketSCMSourceContext probe = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(probe.skipPublicPRs(), is(false));
        instance.decorateContext(probe);
        assertThat(probe.skipPublicPRs(), is(true));
    }
}
