/*
 * The MIT License
 *
 * Copyright (c) 2025, Nikolas Falco
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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticatedClient;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookManager;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.BitbucketSCMSourcePushHookReceiver;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import static org.apache.commons.lang3.StringUtils.upperCase;

public abstract class AbstractWebhookManager<T extends AbstractBitbucketWebhookConfiguration> implements BitbucketWebhookManager {

    protected T configuration;
    protected String callbackURL;

    @Override
    public void setCallbackURL(String callbackURL, BitbucketEndpoint endpoint) {
        this.callbackURL = callbackURL;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void apply(BitbucketWebhookConfiguration configuration) {
        this.configuration = (T) configuration;
    }

    @Nullable
    protected String buildCacheKey(@NonNull BitbucketAuthenticatedClient client) {
        if (StringUtils.isNotBlank(client.getRepositoryName())) {
            return upperCase(client.getRepositoryOwner()) + "::" + client.getRepositoryName();
        } else {
            return null;
        }
    }

    protected boolean isCacheEnabled(@NonNull BitbucketAuthenticatedClient client) {
        return configuration.isEnableCache() && StringUtils.isNotBlank(client.getRepositoryName());
    }

    @Nullable
    protected String getEndpointJenkinsRootURL() {
        return ObjectUtils.getFirstNonNull(() -> configuration.getEndpointJenkinsRootURL(), BitbucketWebhookConfiguration::getDefaultJenkinsRootURL);
    }

    protected boolean isValidWebhook(String webhookCallbackURL) {
        String jenkinsRootURL = getEndpointJenkinsRootURL();
        return webhookCallbackURL.startsWith(jenkinsRootURL + BitbucketSCMSourcePushHookReceiver.FULL_PATH);
    }
}
