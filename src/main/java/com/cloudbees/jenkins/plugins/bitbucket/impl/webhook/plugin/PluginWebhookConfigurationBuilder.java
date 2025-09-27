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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.plugin;

import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookConfigurationBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;

@Deprecated(since = "937.1.0")
@Extension
public class PluginWebhookConfigurationBuilder implements BitbucketWebhookConfigurationBuilder {

    private String credentialsId;
    private String callbackRootURL;

    @NonNull
    @Override
    public PluginWebhookConfigurationBuilder autoManaged(@NonNull String credentialsId) {
        this.credentialsId = credentialsId;
        return this;
    }

    @NonNull
    @Override
    public BitbucketWebhookConfigurationBuilder callbackRootURL(String callbackRootURL) {
        this.callbackRootURL = callbackRootURL;
        return this;
    }

    @Override
    public String getId() {
        return "PLUGIN";
    }

    @NonNull
    @Override
    public PluginWebhookConfiguration build() {
        PluginWebhookConfiguration configuration = new PluginWebhookConfiguration(credentialsId != null, credentialsId);
        configuration.setEndpointJenkinsRootURL(callbackRootURL);
        return configuration;
    }
}
