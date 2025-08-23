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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook;

import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.EndpointType;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;

public class DummyBitbucketWebhook extends AbstractBitbucketWebhookConfiguration {

    DummyBitbucketWebhook(boolean manageHooks, String credentialsId) {
        super(manageHooks, credentialsId, false, null);
    }

    @Override
    public String getDisplayName() {
        return "Dummy";
    }

    @NonNull
    @Override
    public String getId() {
        return "DUMMY";
    }

    @NonNull
    @Override
    public String getEndpointJenkinsRootURL() {
        return "http://master.example.com";
    }

    @Override
    public Class<? extends BitbucketWebhookManager> getManager() {
        return DummyWebhookManager.class;
    }

    @Extension // TestExtension could be used only for embedded classes
    public static class DescriptorImpl extends AbstractBitbucketWebhookDescriptorImpl {
        @Override
        public boolean isApplicable(@NonNull EndpointType type) {
            return true;
        }
    }

}