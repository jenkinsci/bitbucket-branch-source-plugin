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
package com.cloudbees.jenkins.plugins.bitbucket.endpoints;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpointDescriptor;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpointProvider;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketCredentialsUtils;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.URLUtils;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundSetter;

import static hudson.Util.fixEmptyAndTrim;

/**
 * Represents a {@link BitbucketCloudEndpoint} or a {@link BitbucketServerEndpoint}.
 *
 * @since 2.2.0
 */
public abstract class AbstractBitbucketEndpoint implements BitbucketEndpoint {

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
    private boolean enableHookSignature;

    /**
     * The {@link StringCredentials#getId()} of the credentials to use to verify the signature of hooks.
     */
    @CheckForNull
    private String hookSignatureCredentialsId;

    /**
     * Jenkins Server Root URL to be used by that Bitbucket endpoint.
     * The global setting from Jenkins.get().getRootUrl()
     * will be used if this field is null or equals an empty string.
     * This variable is bound to the UI, so an empty value is saved
     * and returned by getter as such.
     */
    private String bitbucketJenkinsRootUrl;

    /**
     * Constructor.
     *
     * @param manageHooks   {@code true} if and only if Jenkins is supposed to auto-manage hooks for this end-point.
     * @param credentialsId The {@link StandardCredentials#getId()} of the credentials to use for
     *                      auto-management of hooks.
     */
    AbstractBitbucketEndpoint(boolean manageHooks, @CheckForNull String credentialsId,
                              boolean enableHookSignature, @CheckForNull String hookSignatureCredentialsId) {
        this.manageHooks = manageHooks && StringUtils.isNotBlank(credentialsId);
        this.credentialsId = manageHooks ? fixEmptyAndTrim(credentialsId) : null;
        this.enableHookSignature = enableHookSignature && StringUtils.isNotBlank(hookSignatureCredentialsId);
        this.hookSignatureCredentialsId = enableHookSignature ? fixEmptyAndTrim(hookSignatureCredentialsId) : null;
    }

    @Override
    public void setManageHooks(boolean manageHooks, String credentialsId) {
        this.manageHooks = manageHooks && StringUtils.isNotBlank(credentialsId);
        this.credentialsId = manageHooks ? fixEmptyAndTrim(credentialsId) : null;
    }

    /**
     * The URL of this endpoint.
     *
     * @return the URL of the endpoint.
     * @deprecated Use {@link BitbucketEndpoint#getServerURL()} instead of this.
     */
    @Deprecated(since = "936.4.0", forRemoval = true)
    @NonNull
    public abstract String getServerUrl();

    /**
     * A Jenkins Server Root URL should end with a slash to use with webhooks.
     *
     * @param rootUrl the original value of a URL which would be normalized
     * @return the normalized URL ending with a slash
     */
    @NonNull
    static String normalizeJenkinsRootUrl(String rootUrl) {
        // This routine is not really BitbucketEndpointConfiguration
        // specific, it just works on strings with some defaults:
        return Util.ensureEndsWith(URLUtils.normalizeURL(fixEmptyAndTrim(rootUrl)), "/");
    }

    /**
     * Jenkins Server Root URL to be used by this Bitbucket endpoint.
     * The global setting from Jenkins.get().getRootUrl()
     * will be used if this field is null or equals an empty string.
     *
     * @return the verbatim setting provided by endpoint configuration
     */
    @Deprecated(since = "936.4.0", forRemoval = true)
    @CheckForNull
    public String getBitbucketJenkinsRootUrl() {
        return bitbucketJenkinsRootUrl;
    }

    @Override
    public String getEndpointJenkinsRootURL() {
        return getBitbucketJenkinsRootUrl();
    }

    @NonNull
    @Override
    public String getRepositoryURL(@NonNull String repoOwner, @NonNull String repoSlug) {
        return this.getRepositoryUrl(repoOwner, repoSlug);
    }

    @DataBoundSetter
    public void setBitbucketJenkinsRootUrl(String bitbucketJenkinsRootUrl) {
        if (manageHooks) {
            this.bitbucketJenkinsRootUrl = normalizeJenkinsRootUrl(bitbucketJenkinsRootUrl);
        } else {
            this.bitbucketJenkinsRootUrl = null;
        }
    }

    @Override
    @CheckForNull
    public String getHookSignatureCredentialsId() {
        return hookSignatureCredentialsId;
    }

    @Override
    public boolean isEnableHookSignature() {
        return enableHookSignature;
    }

    /**
     * Jenkins Server Root URL to be used by this Bitbucket endpoint.
     * The global setting from Jenkins.get().getRootUrl()
     * will be used if this field is null or equals an empty string.
     *
     * @return the normalized value from setting provided by endpoint
     *      configuration (if not empty), or the global setting of
     *      the Jenkins Root URL
     * @deprecated Use {@link BitbucketEndpoint#getEndpointJenkinsRootURL()} instead of this.
     */
    @Deprecated(since = "936.4.0", forRemoval = true)
    @NonNull
    public String getEndpointJenkinsRootUrl() {
        if (StringUtils.isBlank(bitbucketJenkinsRootUrl)) {
            return DisplayURLProvider.get().getRoot();
        } else {
            return bitbucketJenkinsRootUrl;
        }
    }

    /**
     * Look up in the current endpoint configurations if one exists for the
     * serverUrl, and return its normalized endpointJenkinsRootUrl value,
     * or the normalized global default Jenkins Root URL if nothing was found
     * or if the setting is an empty string; empty string if there was an error
     * finding the global default Jenkins Root URL value (e.g. core not started).
     * This is the routine intended for external consumption when one needs a
     * Jenkins Root URL to use for webhook configuration.
     *
     * @param serverURL Bitbucket Server URL for the endpoint config
     *
     * @return the normalized custom or default Jenkins Root URL value
     */
    @NonNull
    public static String getEndpointJenkinsRootUrl(String serverURL) {
        // If this instance of Bitbucket connection has a custom root URL
        // configured to have this Jenkins server known by (e.g. when a
        // private network has different names preferable for different
        // clients), return this custom string. Otherwise use global one.
        // Note: do not pre-initialize to the global value, so it can be
        // reconfigured on the fly.

        String endpointURL = null;
        BitbucketEndpoint endpoint = BitbucketEndpointProvider
                .lookupEndpoint(serverURL)
                .orElse(null);
        if (endpoint != null) {
            endpointURL = endpoint.getEndpointJenkinsRootURL();
        }
        if (endpointURL == null) {
            endpointURL = DisplayURLProvider.get().getRoot();
        }
        return endpointURL;
    }

    /**
     * The user facing URL of the specified repository.
     *
     * @param repoOwner  the repository owner.
     * @param repository the repository.
     * @return the user facing URL of the specified repository.
     */
    @NonNull
    public abstract String getRepositoryUrl(@NonNull String repoOwner, @NonNull String repository);

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

    /**
     * Looks up the {@link StandardCredentials} to use for auto-management of hooks.
     *
     * @return the credentials or {@code null}.
     */
    @Override
    @CheckForNull
    public StandardCredentials credentials() {
        return BitbucketCredentialsUtils.lookupCredentials(Jenkins.get(), getServerURL(), credentialsId, StandardCredentials.class);
    }

    /**
     * Looks up the {@link StringCredentials} to use to verify the signature of hooks.
     *
     * @return the credentials or {@code null}.
     */
    @Override
    @CheckForNull
    public StringCredentials hookSignatureCredentials() {
        return BitbucketCredentialsUtils.lookupCredentials(Jenkins.get(), getServerURL(), hookSignatureCredentialsId, StringCredentials.class);
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

    /**
     * {@inheritDoc}
     */
    @Override
    public BitbucketEndpointDescriptor getDescriptor() {
        return (BitbucketEndpointDescriptor) Jenkins.get().getDescriptorOrDie(getClass());
    }
}
