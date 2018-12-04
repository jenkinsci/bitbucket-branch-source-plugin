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
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

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
