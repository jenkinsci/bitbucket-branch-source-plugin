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
import com.cloudbees.jenkins.plugins.bitbucket.WebhookRegistration;
import com.cloudbees.jenkins.plugins.bitbucket.trait.WebhookRegistrationTrait;
import hudson.util.ListBoxModel;
import jenkins.scm.api.SCMHeadObserver;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
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
