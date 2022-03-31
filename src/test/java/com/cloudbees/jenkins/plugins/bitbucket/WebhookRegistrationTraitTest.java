package com.cloudbees.jenkins.plugins.bitbucket;

import hudson.util.ListBoxModel;
import jenkins.scm.api.SCMHeadObserver;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeThat;

public class WebhookRegistrationTraitTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Test
    public void given__webhookRegistrationDisabled__when__appliedToContext__then__webhookRegistrationDisabled()
            throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.webhookRegistration(), is(WebhookRegistration.SYSTEM));
        WebhookRegistrationTrait instance = new WebhookRegistrationTrait(WebhookRegistration.DISABLE.toString());
        instance.decorateContext(ctx);
        assertThat(ctx.webhookRegistration(), is(WebhookRegistration.DISABLE));
    }

    @Test
    public void given__webhookRegistrationFromItem__when__appliedToContext__then__webhookRegistrationFromItem()
            throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.webhookRegistration(), is(WebhookRegistration.SYSTEM));
        WebhookRegistrationTrait instance = new WebhookRegistrationTrait(WebhookRegistration.ITEM.toString());
        instance.decorateContext(ctx);
        assertThat(ctx.webhookRegistration(), is(WebhookRegistration.ITEM));
    }

    @Test
    public void given__descriptor__when__displayingOptions__then__SYSTEM_not_present() {
        ListBoxModel options =
                j.jenkins.getDescriptorByType(WebhookRegistrationTrait.DescriptorImpl.class).doFillModeItems();
        for (ListBoxModel.Option o : options) {
            assertThat(o.value, not(is(WebhookRegistration.SYSTEM.name())));
        }
    }

}
