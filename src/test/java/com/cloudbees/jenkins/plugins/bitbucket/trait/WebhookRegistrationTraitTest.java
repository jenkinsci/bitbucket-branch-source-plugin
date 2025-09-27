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
import hudson.util.ListBoxModel;
import java.util.Objects;
import jenkins.scm.api.SCMHeadObserver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

class WebhookRegistrationTraitTest {
    @Test
    void given__webhookRegistrationDisabled__when__appliedToContext__then__webhookRegistrationDisabled() {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.webhookRegistration()).isEqualTo(WebhookRegistration.SYSTEM);

        WebhookRegistrationTrait instance = new WebhookRegistrationTrait(WebhookRegistration.DISABLE.toString());
        instance.decorateContext(ctx);
        assertThat(ctx.webhookRegistration()).isEqualTo(WebhookRegistration.DISABLE);
    }

    @Test
    void given__webhookRegistrationFromItem__when__appliedToContext__then__webhookRegistrationFromItem() {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.webhookRegistration()).isEqualTo(WebhookRegistration.SYSTEM);

        WebhookRegistrationTrait instance = new WebhookRegistrationTrait(WebhookRegistration.ITEM.toString());
        instance.decorateContext(ctx);
        assertThat(ctx.webhookRegistration()).isEqualTo(WebhookRegistration.ITEM);
    }

    @Test
    void given__descriptor__when__displayingOptions__then__SYSTEM_not_present() {
        ListBoxModel items = new WebhookRegistrationTrait.DescriptorImpl().doFillModeItems();
        assertThat(items).isNotEmpty()
            .noneMatch(el -> Objects.equals(el.value, WebhookRegistration.SYSTEM.name()));
    }

}
