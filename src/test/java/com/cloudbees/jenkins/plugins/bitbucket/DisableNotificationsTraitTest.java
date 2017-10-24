package com.cloudbees.jenkins.plugins.bitbucket;

import jenkins.scm.api.SCMHeadObserver;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

public class DisableNotificationsTraitTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Test
    public void given__instance__when__decoratingContext__then__notificationsDisabled() throws Exception {
        DisableNotificationsTrait instance = new DisableNotificationsTrait();
        BitbucketSCMSourceContext probe = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(probe.notificationsDisabled(), is(false));
        instance.decorateContext(probe);
        assertThat(probe.notificationsDisabled(), is(true));
    }
}
