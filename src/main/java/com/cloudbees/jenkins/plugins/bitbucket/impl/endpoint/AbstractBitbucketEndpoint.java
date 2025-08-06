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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpointDescriptor;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhook;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.cloud.CloudWebhook;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.plugin.PluginWebhook;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.server.ServerWebhook;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a {@link BitbucketCloudEndpoint} or a {@link BitbucketServerEndpoint}.
 *
 * @since 2.2.0
 */
public abstract class AbstractBitbucketEndpoint implements BitbucketEndpoint {
    protected final transient Logger logger = LoggerFactory.getLogger(getClass());

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
    private BitbucketWebhook webhook;

    AbstractBitbucketEndpoint(@NonNull BitbucketWebhook webhook) {
        this.webhook = Objects.requireNonNull(webhook);
    }

    @NonNull
    @Override
    public BitbucketWebhook getWebhook() {
        return webhook;
    }

//    public void setWebhook(@NonNull BitbucketWebhook webhook) {
//        this.webhook = webhook;
//    }

    @Deprecated(since = "937.0.0", forRemoval = true)
    @Override
    public void setManageHooks(boolean manageHooks, String credentialsId) {
        logger.warn("You are calling the deprecated method setManageHooks(), this method will be remove in future releases.");
        if (webhook instanceof CloudWebhook) {
            webhook = new CloudWebhook(manageHooks, credentialsId);
        } else if (webhook instanceof ServerWebhook) {
            webhook = new ServerWebhook(manageHooks, credentialsId);
        } else if (webhook instanceof PluginWebhook) {
            webhook = new PluginWebhook(manageHooks, credentialsId);
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
        logger.warn("You are calling the deprecated method getEndpointJenkinsRootURL(), this method will be remove in future releases.");
        if (webhook instanceof CloudWebhook cloud) {
            return cloud.getEndpointJenkinsRootURL();
        } else if (webhook instanceof ServerWebhook server) {
            return server.getEndpointJenkinsRootURL();
        } else if (webhook instanceof PluginWebhook plugin) {
            return plugin.getEndpointJenkinsRootURL();
        } else {
            throw new UnsupportedOperationException("This method does not support webhook of type " + webhook.getClass().getName());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated(since = "937.0.0", forRemoval = true)
    @Override
    public final boolean isManageHooks() {
        logger.warn("You are calling the deprecated method isManageHooks(), this method will be remove in future releases.");
        if (webhook instanceof CloudWebhook cloud) {
            return cloud.isManageHooks();
        } else if (webhook instanceof ServerWebhook server) {
            return server.isManageHooks();
        } else if (webhook instanceof PluginWebhook plugin) {
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
        logger.warn("You are calling the deprecated method getCredentialsId(), this method will be remove in future releases.");
        if (webhook instanceof CloudWebhook cloud) {
            return cloud.getCredentialsId();
        } else if (webhook instanceof ServerWebhook server) {
            return server.getCredentialsId();
        } else if (webhook instanceof PluginWebhook plugin) {
            return plugin.getCredentialsId();
        } else {
            throw new UnsupportedOperationException("This deprecated method does not support webhook of type " + webhook.getClass().getName());
        }
    }

    /**
     * Retrieves the {@link BitbucketAuthenticator} to use for auto-management of hooks.
     *
     * @return the authenticator or {@code null}.
     */
    @CheckForNull
    public BitbucketAuthenticator authenticator() {
        return AuthenticationTokens.convert(BitbucketAuthenticator.authenticationContext(getServerURL()), credentials());
    }

    protected Object readResolve() {
        if (webhook == null) {
            if ("NATIVE".equals(webhookImplementation)) {
                webhook = new ServerWebhook(manageHooks, credentialsId, enableHookSignature, hookSignatureCredentialsId);
                ((ServerWebhook) webhook).setEndpointJenkinsRootURL(bitbucketJenkinsRootUrl);
            } else if ("PLUGIN".equals(webhookImplementation)) {
                webhook = new PluginWebhook(manageHooks, credentialsId);
                ((PluginWebhook) webhook).setEndpointJenkinsRootURL(bitbucketJenkinsRootUrl);
            } else {
                webhook = new CloudWebhook(manageHooks, credentialsId, enableHookSignature, hookSignatureCredentialsId);
                ((CloudWebhook) webhook).setEndpointJenkinsRootURL(bitbucketJenkinsRootUrl);
            }
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BitbucketEndpointDescriptor getDescriptor() {
        return (BitbucketEndpointDescriptor) Jenkins.get().getDescriptorOrDie(getClass());
    }
}
