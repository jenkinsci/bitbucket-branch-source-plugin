/*
 * The MIT License
 *
 * Copyright (c) 2025, Falco Nikolas
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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.server;

import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketApiUtils;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketCredentialsUtils;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.AbstractBitbucketWebhook;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.Messages;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

public class ServerWebhook extends AbstractBitbucketWebhook {

    public ServerWebhook(boolean manageHooks, @CheckForNull String credentialsId) {
        super(manageHooks, credentialsId, false, null);
    }

    @DataBoundConstructor
    public ServerWebhook(boolean manageHooks, @CheckForNull String credentialsId,
                         boolean enableHookSignature, @CheckForNull String hookSignatureCredentialsId) {
        super(manageHooks, credentialsId, enableHookSignature, hookSignatureCredentialsId);
    }

    @Override
    public String getDisplayName() {
        return Messages.ServerWebhookImplementation_displayName();
    }

    @Override
    public String getId() {
        return "NATIVE";
    }

    @Symbol("serverWebhook")
    @Extension
    public static class DescriptorImpl extends AbstractBitbucketWebhookDescriptorImpl {

        @Override
        public String getDisplayName() {
            return "Native Data Center";
        }

        @Override
        public boolean isApplicable(String serverURL) {
            return !BitbucketApiUtils.isCloud(serverURL);
        }

        /**
         * Stapler form completion.
         *
         * @param credentialsId selected credentials.
         * @param serverURL the server URL.
         * @return the available credentials.
         */
        @RequirePOST
        public ListBoxModel doFillCredentialsIdItems(@QueryParameter(fixEmpty = true) String credentialsId,
                                                     @QueryParameter(value = "serverURL", fixEmpty = true) String serverURL) {
            Jenkins jenkins = checkPermission();
            return BitbucketCredentialsUtils.listCredentials(jenkins, serverURL, credentialsId);
        }

        /**
         * Stapler form completion.
         *
         * @param hookSignatureCredentialsId selected hook signature credentials.
         * @param serverURL the server URL.
         * @return the available credentials.
         */
        @RequirePOST
        public ListBoxModel doFillHookSignatureCredentialsIdItems(@QueryParameter(fixEmpty = true) String hookSignatureCredentialsId,
                                                                  @QueryParameter(value = "serverURL", fixEmpty = true) String serverURL) {
            Jenkins jenkins = checkPermission();
            StandardListBoxModel result = new StandardListBoxModel();
            result.includeMatchingAs(ACL.SYSTEM2,
                    jenkins,
                    StringCredentials.class,
                    URIRequirementBuilder.fromUri(serverURL).build(),
                    CredentialsMatchers.always());
            if (hookSignatureCredentialsId != null) {
                result.includeCurrentValue(hookSignatureCredentialsId);
            }
            return result;
        }
    }
}
