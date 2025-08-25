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
package com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint;

import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.cloud.CloudWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.plugin.PluginWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.server.ServerWebhookConfiguration;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Represents a {@link BitbucketCloudEndpoint} or a {@link BitbucketServerEndpoint}.
 *
 * @since 2.2.0
 */
public abstract class AbstractBitbucketEndpoint implements BitbucketEndpoint {
    private static final Logger logger = Logger.getLogger(AbstractBitbucketEndpoint.class.getName());

    // Kept for backward XStream compatibility
    @Deprecated
    private boolean manageHooks;
    @Deprecated
    private String credentialsId;
    @Deprecated
    private boolean enableHookSignature;
    @Deprecated
    private String hookSignatureCredentialsId;
    @Deprecated
    private String bitbucketJenkinsRootUrl;
    @Deprecated
    private String webhookImplementation;

    @NonNull
    private BitbucketWebhookConfiguration webhook;

    AbstractBitbucketEndpoint(@NonNull BitbucketWebhookConfiguration webhook) {
        this.webhook = Objects.requireNonNull(webhook);
    }

    @NonNull
    @Override
    public BitbucketWebhookConfiguration getWebhook() {
        return webhook;
    }

    // required by CasC
    public void setWebhook(@NonNull BitbucketWebhookConfiguration webhook) {
        this.webhook = webhook;
    }

    @Deprecated(since = "937.0.0", forRemoval = true)
    @Override
    public void setManageHooks(boolean manageHooks, String credentialsId) {
        logger.warning("You are calling the deprecated method setManageHooks(), this method will be remove in future releases.");
        if (webhook instanceof CloudWebhookConfiguration) {
            webhook = new CloudWebhookConfiguration(manageHooks, credentialsId);
        } else if (webhook instanceof ServerWebhookConfiguration) {
            webhook = new ServerWebhookConfiguration(manageHooks, credentialsId);
        } else if (webhook instanceof PluginWebhookConfiguration) {
            webhook = new PluginWebhookConfiguration(manageHooks, credentialsId);
        } else {
            throw new UnsupportedOperationException("This method does not support webhook of type " + webhook.getClass().getName());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated(since = "937.0.0", forRemoval = true)
    @NonNull
    @Override
    public String getEndpointJenkinsRootURL() {
        logger.warning("You are calling the deprecated method getEndpointJenkinsRootURL(), this method will be remove in future releases.");
        String jenkinsURL = null;
        if (webhook instanceof CloudWebhookConfiguration cloud) {
            jenkinsURL = cloud.getEndpointJenkinsRootURL();
        } else if (webhook instanceof ServerWebhookConfiguration server) {
            jenkinsURL = server.getEndpointJenkinsRootURL();
        } else if (webhook instanceof PluginWebhookConfiguration plugin) {
            jenkinsURL = plugin.getEndpointJenkinsRootURL();
        } else {
            throw new UnsupportedOperationException("This method does not support webhook of type " + webhook.getClass().getName());
        }
        return jenkinsURL == null ? BitbucketWebhookConfiguration.getDefaultJenkinsRootURL() : jenkinsURL;
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated(since = "937.0.0", forRemoval = true)
    @Override
    public final boolean isManageHooks() {
        logger.warning("You are calling the deprecated method isManageHooks(), this method will be remove in future releases.");
        if (webhook instanceof CloudWebhookConfiguration cloud) {
            return cloud.isManageHooks();
        } else if (webhook instanceof ServerWebhookConfiguration server) {
            return server.isManageHooks();
        } else if (webhook instanceof PluginWebhookConfiguration plugin) {
            return plugin.isManageHooks();
        } else {
            throw new UnsupportedOperationException("This deprecated method does not support webhook of type " + webhook.getClass().getName());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated(since = "937.0.0", forRemoval = true)
    @Override
    @CheckForNull
    public final String getCredentialsId() {
        logger.warning("You are calling the deprecated method getCredentialsId(), this method will be remove in future releases.");
        if (webhook instanceof CloudWebhookConfiguration cloud) {
            return cloud.getCredentialsId();
        } else if (webhook instanceof ServerWebhookConfiguration server) {
            return server.getCredentialsId();
        } else if (webhook instanceof PluginWebhookConfiguration plugin) {
            return plugin.getCredentialsId();
        } else {
            throw new UnsupportedOperationException("This deprecated method does not support webhook of type " + webhook.getClass().getName());
        }
    }

    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
    protected Object readResolve() {
        if (webhook == null) {
            if ("NATIVE".equals(webhookImplementation)) {
                webhook = new ServerWebhookConfiguration(manageHooks, credentialsId, enableHookSignature, hookSignatureCredentialsId);
                ((ServerWebhookConfiguration) webhook).setEndpointJenkinsRootURL(bitbucketJenkinsRootUrl);
            } else if ("PLUGIN".equals(webhookImplementation)) {
                webhook = new PluginWebhookConfiguration(manageHooks, credentialsId);
                ((PluginWebhookConfiguration) webhook).setEndpointJenkinsRootURL(bitbucketJenkinsRootUrl);
            } else {
                webhook = new CloudWebhookConfiguration(manageHooks, credentialsId, enableHookSignature, hookSignatureCredentialsId);
                ((CloudWebhookConfiguration) webhook).setEndpointJenkinsRootURL(bitbucketJenkinsRootUrl);
            }
        }
        return this;
    }

}
