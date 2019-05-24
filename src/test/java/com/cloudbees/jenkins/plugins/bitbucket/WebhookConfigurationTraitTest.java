package com.cloudbees.jenkins.plugins.bitbucket;

import jenkins.scm.api.SCMHeadObserver;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;

public class WebhookConfigurationTraitTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    /**
     * Test the default empty value
     * @throws Exception
     */
    @Test
    public void given__webhookConfigurationEmpty__when__appliedToContext__then__webhookConfigurationEmpty()
            throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assertEquals(ctx.webhookConfiguration().getCommittersToIgnore(), null);
        WebhookConfigurationTrait instance = new WebhookConfigurationTrait("");
        instance.decorateContext(ctx);
        assertEquals(ctx.webhookConfiguration().getCommittersToIgnore(), "");
    }

    /**
     * Test a set value
     * @throws Exception
     */
    @Test
    public void given__webhookRegistrationFromItem__when__appliedToContext__then__webhookRegistrationFromItem()
            throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assertEquals(ctx.webhookConfiguration().getCommittersToIgnore(), null);
        WebhookConfigurationTrait instance = new WebhookConfigurationTrait("jenkins");
        instance.decorateContext(ctx);
        assertEquals(ctx.webhookConfiguration().getCommittersToIgnore(), "jenkins");
    }
}
