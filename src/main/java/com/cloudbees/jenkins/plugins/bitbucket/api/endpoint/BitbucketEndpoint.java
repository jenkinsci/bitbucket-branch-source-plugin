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
package com.cloudbees.jenkins.plugins.bitbucket.api.endpoint;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketCredentialsUtils;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.Describable;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.Strings;

/**
 * The implementation represents an endpoint configuration to be used in
 * {@link BitbucketSCMSource}.
 *
 * @since 936.4.0
 */
public interface BitbucketEndpoint extends Describable<BitbucketEndpoint> {

    /**
     * Name to use to describe the endpoint.
     *
     * @return the name to use for the endpoint
     */
    @CheckForNull
    String getDisplayName();

    /**
     * The user facing URL of the specified repository.
     *
     * @param repoOwner the repository owner.
     * @param repoSlug the repository name
     * @return the user facing URL of the specified repository.
     */
    @NonNull
    String getRepositoryURL(@NonNull String repoOwner, @NonNull String repoSlug);

    /**
     * Returns the type of this endpoint.
     *
     * @return endpoint type.
     */
    @NonNull
    EndpointType getType();

    /**
     * The URL of this endpoint.
     *
     * @return the URL of the endpoint.
     */
    @NonNull
    String getServerURL();

    /**
     * Returns the webhook implementation that this endpoint is using to manage the incoming payload.
     *
     * @return the {@link BitbucketWebhookConfiguration} implementation selected for this endpoint.
     * @since 937.0.0
     */
    @NonNull
    BitbucketWebhookConfiguration getWebhook();

    /**
     * Returns {@code true} if and only if Jenkins is supposed to auto-manage
     * hooks for this end-point.
     *
     * @return {@code true} if and only if Jenkins is supposed to auto-manage
     *         hooks for this end-point.
     * @deprecated Use {@link #getWebhook()} instead of this to retrieve information webhook
     */
    @Deprecated(since = "937.0.0", forRemoval = true)
    boolean isManageHooks();

    /**
     * Sets if Jenkins is supposed to auto-manage hooks for this end-point.
     *
     * @param manageHooks   if Jenkins must auto-manage hooks registration.
     * @param credentialsId credentialsId to use with rights to create or update web
     *                      hook for Bitbucket repositories.
     * @deprecated Use {@link #getWebhook()} instead of this to retrieve information webhook
     */
    @Deprecated(since = "937.0.0", forRemoval = true)
    void setManageHooks(boolean manageHooks, @CheckForNull String credentialsId);

    /**
     * Returns the {@link StandardUsernamePasswordCredentials#getId()} of the
     * credentials to use for auto-management of hooks.
     *
     * @return the {@link StandardUsernamePasswordCredentials#getId()} of the
     *         credentials to use for auto-management of hooks.
     * @deprecated Use {@link #getWebhook()} instead of this to retrieve information webhook
     */
    @CheckForNull
    @Deprecated(since = "937.0.0", forRemoval = true)
    String getCredentialsId();

    /**
     * Jenkins Server Root URL to be used by this Bitbucket endpoint. The global
     * setting from Jenkins.get().getRootUrl() will be used if this field is
     * null or equals an empty string.
     *
     * @return the verbatim setting provided by endpoint configuration
     * @deprecated Use {@link #getWebhook()} instead of this to retrieve information webhook
     */
    @Deprecated(since = "937.0.0", forRemoval = true)
    @NonNull
    String getEndpointJenkinsRootURL();

    /**
     * Looks up the {@link StandardCredentials} to use for auto-management of hooks.
     *
     * @return the credentials or {@code null}.
     * @deprecated Use {@link #getWebhook()} instead of this to retrieve information webhook
     */
    @Deprecated(since = "937.0.0", forRemoval = true)
    @CheckForNull
    default StandardCredentials credentials() {
        String credentialsId = Util.fixEmptyAndTrim(getWebhook().getCredentialsId());
        if (credentialsId == null) {
            return null;
        } else {
            return BitbucketCredentialsUtils.lookupCredentials(Jenkins.get(), getServerURL(), credentialsId, StandardCredentials.class);
        }
    }

    /**
     * Returns if two endpoint are the equals.
     *
     * @param endpoint to compare
     * @return {@code true} if endpoint are the same, {@code false} otherwise
     */
    default boolean isEquals(BitbucketEndpoint endpoint) {
        return Strings.CI.equals(getServerURL(), endpoint.getServerURL());
    }

   /**
    *
    * @see Describable#getDescriptor()
    */
   @Override
   default BitbucketEndpointDescriptor getDescriptor() {
       return (BitbucketEndpointDescriptor) Jenkins.get().getDescriptorOrDie(getClass());
   }
}
