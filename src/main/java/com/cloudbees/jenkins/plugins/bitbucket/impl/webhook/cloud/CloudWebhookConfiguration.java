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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.cloud;

import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.EndpointType;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookManager;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.AbstractBitbucketWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.Messages;
import com.cloudbees.jenkins.plugins.bitbucket.util.BitbucketCredentialsUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.ListBoxModel;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

public class CloudWebhookConfiguration extends AbstractBitbucketWebhookConfiguration {

    public CloudWebhookConfiguration(boolean manageHooks, String credentialsId) {
        this(manageHooks, credentialsId, false, null);
    }

    @DataBoundConstructor
    public CloudWebhookConfiguration(boolean manageHooks, String credentialsId, boolean enableHookSignature, String hookSignatureCredentialsId) {
        super(manageHooks, credentialsId, enableHookSignature, hookSignatureCredentialsId);
    }

    @Override
    public String getDisplayName() {
        return Messages.CloudWebhookImplementation_displayName();
    }

    @NonNull
    @Override
    public String getId() {
        return "CLOUD";
    }

    @Override
    public Class<? extends BitbucketWebhookManager> getManager() {
        return CloudWebhookManager.class;
    }

    @Symbol("cloudWebhook")
    @Extension
    public static class DescriptorImpl extends AbstractBitbucketWebhookDescriptorImpl {

        @Override
        protected void clearCaches() {
            CloudWebhookManager.clearCaches();
        }

        @Override
        protected List<String> getStats() {
            return CloudWebhookManager.stats();
        }

        @Override
        public String getDisplayName() {
            return Messages.CloudWebhookImplementation_displayName();
        }

        @Override
        public boolean isApplicable(EndpointType type) {
            return type == EndpointType.CLOUD;
        }

        /**
         * Stapler form completion.
         *
         * @param credentialsId selected credentials.
         * @return the available credentials.
         */
        @RequirePOST
        public ListBoxModel doFillCredentialsIdItems(@QueryParameter(fixEmpty = true) String credentialsId) {
            Jenkins jenkins = checkPermission();
            return BitbucketCredentialsUtils.listCredentials(jenkins, BitbucketCloudEndpoint.SERVER_URL, credentialsId);
        }

        /**
         * Stapler form completion.
         *
         * @param hookSignatureCredentialsId selected hook signature credentials.
         * @return the available credentials.
         */
        @RequirePOST
        public ListBoxModel doFillHookSignatureCredentialsIdItems(@QueryParameter(fixEmpty = true) String hookSignatureCredentialsId) {
            return super.getHookSignatureCredentialsIdItems(hookSignatureCredentialsId, BitbucketCloudEndpoint.SERVER_URL);
        }

    }

}
