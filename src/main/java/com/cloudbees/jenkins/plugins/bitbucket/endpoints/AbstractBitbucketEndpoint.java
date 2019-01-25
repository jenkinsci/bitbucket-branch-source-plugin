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
import hudson.model.AbstractDescribableImpl;
import hudson.security.ACL;
import hudson.Util;
import hudson.util.FormValidation;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a {@link BitbucketCloudEndpoint} or a {@link BitbucketServerEndpoint}.
 *
 * @since 2.2.0
 */
public abstract class AbstractBitbucketEndpoint extends AbstractDescribableImpl<AbstractBitbucketEndpoint> {

    private static final Logger LOGGER = Logger.getLogger(AbstractBitbucketEndpoint.class.getName());

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
     * The global setting from Jenkins.getActiveInstance().getRootUrl()
     * will be used if this field is null or equals an empty string.
     * This variable is bound to the UI, so an empty value is saved
     * and returned by getter as such.
     */
    private final String bitbucketJenkinsRootUrl;

    /**
     * Value of not-empty bitbucketJenkinsRootUrl normalized for end-users
     * and saved, to avoid recalculating it over and over; an empty or null
     * value still causes evaluation of current global setting every time.
     */
    private transient String endpointJenkinsRootUrl;


    /**
     * Constructor.
     *
     * @param manageHooks   {@code true} if and only if Jenkins is supposed to auto-manage hooks for this end-point.
     * @param credentialsId The {@link StandardCredentials#getId()} of the credentials to use for
     *                      auto-management of hooks.
     * @param bitbucketJenkinsRootUrl The custom (or empty for global setting) Jenkins Root URL
     *                      auto-management of hooks.
     */
    AbstractBitbucketEndpoint(boolean manageHooks, @CheckForNull String credentialsId, @CheckForNull String bitbucketJenkinsRootUrl) {
        this.manageHooks = manageHooks && StringUtils.isNotBlank(credentialsId);
        if (this.manageHooks) {
            this.credentialsId = credentialsId;
            this.bitbucketJenkinsRootUrl = bitbucketJenkinsRootUrl ;
        } else {
            this.credentialsId = null;
            this.bitbucketJenkinsRootUrl = "";
        }
        this.setEndpointJenkinsRootUrl(this.bitbucketJenkinsRootUrl);
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
     * @param rootUrl the original value of an URL which would be normalized
     * @return the normalized URL ending with a slash
     */
    @NonNull
    public static String normalizeJenkinsRootUrl(String rootUrl) {
        // This routine is not really BitbucketEndpointConfiguration
        // specific, it just works on strings with some defaults:
        rootUrl = BitbucketEndpointConfiguration.normalizeServerUrl(rootUrl);
        if ( !rootUrl.endsWith("/") ) {
            rootUrl += "/";
        }
        return rootUrl;
    }

    /**
     * Jenkins Server Root URL to be used by this Bitbucket endpoint.
     * The global setting from Jenkins.getActiveInstance().getRootUrl()
     * will be used if this field is null or equals an empty string.
     *
     * @return the verbatim setting provided by endpoint configuration
     */
    @NonNull
    public String getBitbucketJenkinsRootUrl() {
        // In the AbstractBitbucketEndpoint return the value "as is"
        // even if empty for proper Web-GUI config management
        if (bitbucketJenkinsRootUrl == null) {
            LOGGER.log(Level.FINEST, "AbstractBitbucketEndpoint::getBitbucketJenkinsRootUrl : <null>");
            return "";
        }
        LOGGER.log(Level.FINEST, "AbstractBitbucketEndpoint::getBitbucketJenkinsRootUrl : {0}", bitbucketJenkinsRootUrl);
        return bitbucketJenkinsRootUrl;
    }

    /**
     * Jenkins Server Root URL to be used by this Bitbucket endpoint.
     * The global setting from Jenkins.getActiveInstance().getRootUrl()
     * will be used if this field is null or equals an empty string.
     *
     * @return the normalized value from setting provided by endpoint
     *      configuration (if not empty), or the global setting of
     *      the Jenkins Root URL
     */
    @NonNull
    public String getEndpointJenkinsRootUrl() {
        // If this instance of Bitbucket connection has a custom root URL
        // configured to have this Jenkins server known by (e.g. when a
        // private network has different names preferable for different
        // clients), return this custom string. Otherwise use global one.
        // Note: do not pre-initialize to the global value, so it can be
        // reconfigured on the fly.

        if ((endpointJenkinsRootUrl == null || endpointJenkinsRootUrl.equals(""))
                && !(bitbucketJenkinsRootUrl == null || bitbucketJenkinsRootUrl.equals(""))) {
            // If this class was loaded (e.g. from config or test fixture)
            // it might forgo the normal constructor above. So make sure
            // we have a real URL (or really don't).
            this.setEndpointJenkinsRootUrl(this.bitbucketJenkinsRootUrl);
        }

        if (endpointJenkinsRootUrl == null || endpointJenkinsRootUrl.equals("")) {
            LOGGER.log(Level.FINEST, "AbstractBitbucketEndpoint::getEndpointJenkinsRootUrl : empty : {0}", endpointJenkinsRootUrl != null ? "''" : "<null>" );
            String rootUrl;
            try {
                rootUrl = Jenkins.getActiveInstance().getRootUrl(); // Can throw if core is not started, e.g. in some tests
                if (rootUrl != null && !rootUrl.equals("")) {
                    rootUrl = AbstractBitbucketEndpoint.normalizeJenkinsRootUrl(rootUrl);
                } else {
                    LOGGER.log(Level.INFO, "AbstractBitbucketEndpoint::getEndpointJenkinsRootUrl : got nothing from Jenkins.getActiveInstance().getRootUrl()");
                    rootUrl = "";
                }
            } catch (IllegalStateException e) {
                // java.lang.IllegalStateException: Jenkins has not been started, or was already shut down
                LOGGER.log(Level.INFO, "AbstractBitbucketEndpoint::getEndpointJenkinsRootUrl : got nothing from Jenkins.getActiveInstance().getRootUrl() : threw {0}", e.toString() );
                rootUrl = "";
            }
            LOGGER.log(Level.FINEST, "AbstractBitbucketEndpoint::getEndpointJenkinsRootUrl : normalized global value: {0}", "'" + rootUrl + "'" );
            return rootUrl;
        }

        // The non-null not-empty endpointJenkinsRootUrl after the setter
        // below is an already processed and normalized string
        LOGGER.log(Level.FINEST, "AbstractBitbucketEndpoint::getEndpointJenkinsRootUrl : original: {0}", "'" + endpointJenkinsRootUrl + "'" );
        return endpointJenkinsRootUrl;
    }

    /**
     * Assign a normalized version of the custom Jenkins Server Root URL value.
     *
     * @param rootUrl the original value of an URL which would be normalized
     */
    private void setEndpointJenkinsRootUrl(String rootUrl) {
        LOGGER.log(Level.FINEST, "AbstractBitbucketEndpoint::setEndpointJenkinsRootUrl : got : {0}", rootUrl == null ? "<null>" : "'" + rootUrl + "'" );
        if (rootUrl == null || rootUrl.equals("")) {
            // The getter will return the current value of global
            // Jenkins Root URL config every time it is called.
            // A null (not empty-string "") value would also cause
            // the setting to be re-evaluated in subsequent calls
            // to updateBitbucketJenkinsRootUrl().
            this.endpointJenkinsRootUrl = rootUrl;
            return;
        }

        // This routine is not really BitbucketEndpointConfiguration
        // specific, it just works on strings with some defaults:
        rootUrl = normalizeJenkinsRootUrl(rootUrl);
        LOGGER.log(Level.FINEST, "AbstractBitbucketEndpoint::setEndpointJenkinsRootUrl : normalized into : {0}", "'" + rootUrl + "'");
        this.endpointJenkinsRootUrl = rootUrl;
    }

    @Restricted(NoExternalUse.class)
    @Deprecated
    public static FormValidation doCheckBitbucketJenkinsRootUrl(@QueryParameter String bitbucketJenkinsRootUrl) {
        String url = Util.fixEmpty(bitbucketJenkinsRootUrl);
        if (url == null) {
            return FormValidation.ok();
        }
        try {
            new URL(bitbucketJenkinsRootUrl);
        } catch (MalformedURLException e) {
            return FormValidation.error("Invalid URL: " +  e.getMessage());
        }
        return FormValidation.ok();
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
                        Jenkins.getActiveInstance(),
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
