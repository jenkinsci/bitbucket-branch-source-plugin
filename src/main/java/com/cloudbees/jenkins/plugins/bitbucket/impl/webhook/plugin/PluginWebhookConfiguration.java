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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.plugin;

import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.EndpointType;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookDescriptor;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookManager;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketCredentialsUtils;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.Messages;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.server.ServerWebhookConfiguration;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import static hudson.Util.fixEmptyAndTrim;

@Deprecated(since = "937.0.0")
// https://help.moveworkforward.com/BPW/atlassian-bitbucket-post-webhook-api
// https://help.moveworkforward.com/BPW/how-to-get-configurations-using-post-webhooks-for-
public class PluginWebhookConfiguration implements BitbucketWebhookConfiguration {
    private static final Logger logger = Logger.getLogger(ServerWebhookConfiguration.class.getName());
    private static final String WEBHOOK_API = "/rest/webhook/1.0/projects/{owner}/repos/{repo}/configurations";

    /**
     * {@code true} if and only if Jenkins is supposed to auto-manage hooks for
     * this end-point.
     */
    private boolean manageHooks;

    /**
     * The {@link StandardCredentials#getId()} of the credentials to use for
     * auto-management of hooks.
     */
    @CheckForNull
    private String credentialsId;

    /**
     * Jenkins Server Root URL to be used by that Bitbucket endpoint.
     * The global setting from Jenkins.get().getRootUrl()
     * will be used if this field is null or equals an empty string.
     * This variable is bound to the UI, so an empty value is saved
     * and returned by getter as such.
     */
    private String endpointJenkinsRootURL;

    @DataBoundConstructor
    public PluginWebhookConfiguration(boolean manageHooks, @CheckForNull String credentialsId) {
        this.manageHooks = manageHooks && StringUtils.isNotBlank(credentialsId);
        this.credentialsId = manageHooks ? fixEmptyAndTrim(credentialsId) : null;
    }

    /**
     * Returns {@code true} if and only if Jenkins is supposed to auto-manage hooks for this end-point.
     *
     * @return {@code true} if and only if Jenkins is supposed to auto-manage hooks for this end-point.
     */
    @Override
    public final boolean isManageHooks() {
        return manageHooks;
    }

    /**
     * Returns the {@link StandardUsernamePasswordCredentials#getId()} of the credentials to use for auto-management
     * of hooks.
     *
     * @return the {@link StandardUsernamePasswordCredentials#getId()} of the credentials to use for auto-management
     * of hooks.
     */
    @Override
    @CheckForNull
    public final String getCredentialsId() {
        return credentialsId;
    }

    @Override
    public String getEndpointJenkinsRootURL() {
        return endpointJenkinsRootURL;
    }

    @DataBoundSetter
    public void setEndpointJenkinsRootURL(@CheckForNull String endpointJenkinsRootURL) {
        this.endpointJenkinsRootURL = fixEmptyAndTrim(endpointJenkinsRootURL);
    }

    @Override
    public String getDisplayName() {
        return Messages.ServerWebhookImplementation_displayName();
    }

    @NonNull
    @Override
    public String getId() {
        return "PLUGIN";
    }

    @Override
    public Class<? extends BitbucketWebhookManager> getManager() {
        return PluginWebhookManager.class;
    }

    @Symbol("pluginWebhook")
    @Extension
    public static class DescriptorImpl extends BitbucketWebhookDescriptor {

        @Override
        public String getDisplayName() {
            return "Post Webhooks for Bitbucket";
        }

        @Override
        public boolean isApplicable(@NonNull EndpointType type) {
            return type == EndpointType.SERVER;
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

        @Restricted(NoExternalUse.class)
        @RequirePOST
        public static FormValidation doCheckEndpointJenkinsRootURL(@QueryParameter String value) {
            checkPermission();
            String url = fixEmptyAndTrim(value);
            if (url == null) {
                return FormValidation.ok();
            }
            try {
                new URL(url);
            } catch (MalformedURLException e) {
                return FormValidation.error("Invalid URL: " + e.getMessage());
            }
            return FormValidation.ok();
        }

        private static Jenkins checkPermission() {
            Jenkins jenkins = Jenkins.get();
            jenkins.checkPermission(Jenkins.MANAGE);
            return jenkins;
        }

    }

}