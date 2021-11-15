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
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.security.ACL;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.displayurlapi.ClassicDisplayURLProvider;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Represents a {@link BitbucketCloudEndpoint} or a {@link BitbucketServerEndpoint}.
 *
 * @since 2.2.0
 */
public abstract class AbstractBitbucketEndpoint extends AbstractDescribableImpl<AbstractBitbucketEndpoint> {

    /**
     * {@code true} if and only if Jenkins is supposed to auto-manage hooks for this end-point.
     */
    private final boolean manageHooks;

    /**
     * The {@link StandardCredentials#getId()} of the credentials to use for auto-management of hooks.
     */
    @CheckForNull
    private final String credentialsId;

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
    AbstractBitbucketEndpoint(boolean manageHooks, @CheckForNull String credentialsId) {
        this.manageHooks = manageHooks && StringUtils.isNotBlank(credentialsId);
        this.credentialsId = manageHooks ? credentialsId : null;
    }

    /**
     * Optional name to use to describe the end-point.
     *
     * @return the name to use for the end-point
     */
    @CheckForNull
    public abstract String getDisplayName();

    /**
     * The URL of this endpoint.
     *
     * @return the URL of the endpoint.
     */
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
        return Util.ensureEndsWith(
            BitbucketEndpointConfiguration.normalizeServerUrl(rootUrl),"/");
    }

    /**
     * Jenkins Server Root URL to be used by this Bitbucket endpoint.
     * The global setting from Jenkins.get().getRootUrl()
     * will be used if this field is null or equals an empty string.
     *
     * @return the verbatim setting provided by endpoint configuration
     */
    @CheckForNull
    public String getBitbucketJenkinsRootUrl() {
        return bitbucketJenkinsRootUrl;
    }

    @DataBoundSetter
    public void setBitbucketJenkinsRootUrl(String bitbucketJenkinsRootUrl) {
        if (manageHooks) {
            this.bitbucketJenkinsRootUrl = Util.fixEmptyAndTrim(bitbucketJenkinsRootUrl);
            if (this.bitbucketJenkinsRootUrl != null) {
                this.bitbucketJenkinsRootUrl = normalizeJenkinsRootUrl(this.bitbucketJenkinsRootUrl);
            }
        } else {
            this.bitbucketJenkinsRootUrl = null;
        }
    }

    /**
     * Jenkins Server Root URL to be used by this Bitbucket endpoint.
     * The global setting from Jenkins.get().getRootUrl()
     * will be used if this field is null or equals an empty string.
     *
     * @return the normalized value from setting provided by endpoint
     *      configuration (if not empty), or the global setting of
     *      the Jenkins Root URL
     */
    @NonNull
    public String getEndpointJenkinsRootUrl() {
        if (StringUtils.isBlank(bitbucketJenkinsRootUrl))
            return ClassicDisplayURLProvider.get().getRoot();
        else
            return bitbucketJenkinsRootUrl;
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
     * @param serverUrl Bitbucket Server URL for the endpoint config
     *
     * @return the normalized custom or default Jenkins Root URL value
     */
    @NonNull
    public static String getEndpointJenkinsRootUrl(String serverUrl) {
        // If this instance of Bitbucket connection has a custom root URL
        // configured to have this Jenkins server known by (e.g. when a
        // private network has different names preferable for different
        // clients), return this custom string. Otherwise use global one.
        // Note: do not pre-initialize to the global value, so it can be
        // reconfigured on the fly.

        AbstractBitbucketEndpoint endpoint = BitbucketEndpointConfiguration.get().findEndpoint(serverUrl);
        if (endpoint != null) {
            return endpoint.getEndpointJenkinsRootUrl();
        }
        return ClassicDisplayURLProvider.get().getRoot();
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
    @CheckForNull
    public final String getCredentialsId() {
        return credentialsId;
    }

    /**
     * Looks up the {@link StandardCredentials} to use for auto-management of hooks.
     *
     * @return the credentials or {@code null}.
     */
    @CheckForNull
    public StandardCredentials credentials() {
        return StringUtils.isBlank(credentialsId) ? null : CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        StandardCredentials.class,
                        Jenkins.get(),
                        ACL.SYSTEM,
                        URIRequirementBuilder.fromUri(getServerUrl()).build()
                ),
                CredentialsMatchers.allOf(
                        CredentialsMatchers.withId(credentialsId),
                        AuthenticationTokens.matcher(BitbucketAuthenticator.authenticationContext(getServerUrl()))
                )
        );
    }

    /**
     * Retrieves the {@link BitbucketAuthenticator} to use for auto-management of hooks.
     *
     * @return the authenticator or {@code null}.
     */
    @CheckForNull
    public BitbucketAuthenticator authenticator() {
        return AuthenticationTokens.convert(BitbucketAuthenticator.authenticationContext(getServerUrl()), credentials());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AbstractBitbucketEndpointDescriptor getDescriptor() {
        return (AbstractBitbucketEndpointDescriptor) super.getDescriptor();
    }
}
