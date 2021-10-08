package com.cloudbees.jenkins.plugins.bitbucket;

import jenkins.scm.api.SCMHeadObserver;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;

public class WebhookConfigurationTraitTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Test
    public void ignoredCommittersDefault()
            throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assertEquals(ctx.webhookConfiguration().getCommittersToIgnore(), null);
        WebhookConfigurationTrait instance = new WebhookConfigurationTrait("");
        instance.decorateContext(ctx);
        assertEquals(ctx.webhookConfiguration().getCommittersToIgnore(), "");
    }

    @Test
    public void ignoredCommittersWithValue()
            throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assertEquals(ctx.webhookConfiguration().getCommittersToIgnore(), null);
        WebhookConfigurationTrait instance = new WebhookConfigurationTrait("jenkins");
        instance.decorateContext(ctx);
        assertEquals(ctx.webhookConfiguration().getCommittersToIgnore(), "jenkins");
    }
}
