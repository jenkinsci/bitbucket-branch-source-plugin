/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.hooks.WebhookConfiguration;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A {@link SCMSourceTrait} for {@link BitbucketSCMSource} that sets the committersToIgnore
 * setting in {@link WebhookConfiguration}.
 *
 * @since 2.4.5
 */
public class WebhookConfigurationTrait extends SCMSourceTrait {

    /**
     * The committers that should be ignored in the webhook
     */
    @NonNull
    private final String committersToIgnore;

    /**
     * Constructor.
     *
     * @param committersToIgnore a string of comma separated Bitbucket usernames to ignore
     */
    @DataBoundConstructor
    public WebhookConfigurationTrait(@NonNull String committersToIgnore) {
        this.committersToIgnore = committersToIgnore;
    }

    /**
     * @return the committers to ignore
     */
    public String getCommittersToIgnore() {
        return this.committersToIgnore;
    }

    /**
     * Gets the WebhookConfiguration to apply.
     *
     * @return the WebhookConfiguration to apply.
     */
    @NonNull
    public final WebhookConfiguration getWebhookConfiguration() {
        return new WebhookConfiguration(this.committersToIgnore);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        ((BitbucketSCMSourceContext) context).webhookConfiguration(getWebhookConfiguration());
    }

    /**
     * Our constructor.
     */
    @Extension
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.WebhookConfigurationTrait_displayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<? extends SCMSourceContext> getContextClass() {
            return BitbucketSCMSourceContext.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<? extends SCMSource> getSourceClass() {
            return BitbucketSCMSource.class;
        }
    }
}
