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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook;

import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookDescriptor;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.URLUtils;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.net.MalformedURLException;
import java.net.URL;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import static hudson.Util.fixEmptyAndTrim;

@Restricted(NoExternalUse.class)
public abstract class AbstractBitbucketWebhookConfiguration implements BitbucketWebhookConfiguration {

    /**
     * {@code true} if and only if Jenkins is supposed to auto-manage hooks for this end-point.
     */
    private boolean manageHooks;

    /**
     * The {@link StandardCredentials#getId()} of the credentials to use for auto-management of hooks.
     */
    @CheckForNull
    private String credentialsId;

    /**
     * {@code true} if and only if Jenkins have to verify the signature of all incoming hooks.
     */
    private final boolean enableHookSignature;

    /**
     * The {@link StringCredentials#getId()} of the credentials to use to verify the signature of hooks.
     */
    @CheckForNull
    private final String hookSignatureCredentialsId;

    /**
     * Jenkins Server Root URL to be used by that Bitbucket endpoint.
     * The global setting from Jenkins.get().getRootUrl()
     * will be used if this field is null or equals an empty string.
     * This variable is bound to the UI, so an empty value is saved
     * and returned by getter as such.
     */
    private String endpointJenkinsRootURL;

    protected AbstractBitbucketWebhookConfiguration(boolean manageHooks, @CheckForNull String credentialsId,
                                       boolean enableHookSignature, @CheckForNull String hookSignatureCredentialsId) {
        this.manageHooks = manageHooks && StringUtils.isNotBlank(credentialsId);
        this.credentialsId = manageHooks ? fixEmptyAndTrim(credentialsId) : null;
        this.enableHookSignature = enableHookSignature && StringUtils.isNotBlank(hookSignatureCredentialsId);
        this.hookSignatureCredentialsId = enableHookSignature ? fixEmptyAndTrim(hookSignatureCredentialsId) : null;
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

    @CheckForNull
    public String getHookSignatureCredentialsId() {
        return hookSignatureCredentialsId;
    }

    public boolean isEnableHookSignature() {
        return enableHookSignature;
    }

    @Override
    @CheckForNull
    public String getEndpointJenkinsRootURL() {
        return endpointJenkinsRootURL;
    }

    @DataBoundSetter
    public void setEndpointJenkinsRootURL(@CheckForNull String endpointJenkinsRootURL) {
        String url = fixEmptyAndTrim(endpointJenkinsRootURL);
        if (url != null) {
            this.endpointJenkinsRootURL = Util.ensureEndsWith(URLUtils.normalizeURL(url), "/");
        }
    }

    @Override
    @NonNull
    public String getDisplayName() {
        return Messages.ServerWebhookImplementation_displayName();
    }

    public abstract static class AbstractBitbucketWebhookDescriptorImpl extends BitbucketWebhookDescriptor {

        @RequirePOST
        public static FormValidation doCheckEndpointJenkinsRootURL(@QueryParameter String value) {
            checkPermission();
            String url = Util.fixEmptyAndTrim(value);
            if (url == null) {
                return FormValidation.ok();
            }
            try {
                new URL(url);
            } catch (MalformedURLException e) {
                return FormValidation.error("Invalid URL: " +  e.getMessage());
            }
            return FormValidation.ok();
        }

        protected ListBoxModel getHookSignatureCredentialsIdItems(String hookSignatureCredentialsId, String serverURL) {
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

        protected static Jenkins checkPermission() {
            Jenkins jenkins = Jenkins.get();
            jenkins.checkPermission(Jenkins.MANAGE);
            return jenkins;
        }
    }
}
