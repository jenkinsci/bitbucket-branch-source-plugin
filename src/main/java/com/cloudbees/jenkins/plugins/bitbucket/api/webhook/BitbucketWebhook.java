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
package com.cloudbees.jenkins.plugins.bitbucket.api.webhook;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.WebhookConfiguration;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Describable;
import java.util.Collection;
import jenkins.model.Jenkins;

/**
 * The implementation represents an a webhook configuration that can be used in
 * a {@link BitbucketEndpoint}.
 *
 * @since 937.0.0
 */
public interface BitbucketWebhook extends Describable<BitbucketWebhook> {

    /**
     * Name to use to describe the hook implementation.
     *
     * @return the name to use for the implementation
     */
    @CheckForNull
    String getDisplayName();

    /**
     * The hook implementation identifier.
     *
     * @return the unique identifier for this implementation
     */
    @NonNull
    String getId();

    /**
     * Returns {@code true} if and only if Jenkins is supposed to auto-manage
     * hooks for this end-point.
     *
     * @return {@code true} if and only if Jenkins is supposed to auto-manage
     *         hooks for this end-point.
     */
    boolean isManageHooks();

    /**
     * Returns the {@link StandardUsernamePasswordCredentials#getId()} of the
     * credentials to use for auto-management of hooks.
     *
     * @return the {@link StandardUsernamePasswordCredentials#getId()} of the
     *         credentials to use for auto-management of hooks.
     */
    @CheckForNull
    String getCredentialsId();

    /**
     * @see Describable#getDescriptor()
     */
    @Override
    default BitbucketWebhookDescriptor getDescriptor() {
        return (BitbucketWebhookDescriptor) Jenkins.get().getDescriptorOrDie(getClass());
    }

    @NonNull
    <T extends BitbucketWebHook> Collection<T> retrieveHooks(@NonNull String serverURL, @NonNull BitbucketWebhookClient client);
    <T extends BitbucketWebHook> void registerHook(@NonNull T payload, @NonNull BitbucketWebhookClient client);
    <T extends BitbucketWebHook> void updateHook(@NonNull T payload, @NonNull BitbucketWebhookClient client);
    void removeHook(@NonNull BitbucketWebHook payload, @NonNull BitbucketWebhookClient client);

    @NonNull
    <T extends BitbucketWebHook> T buildPayload(@NonNull WebhookConfiguration hookConfig, @NonNull BitbucketSCMSource source);
    <T extends BitbucketWebHook> boolean shouldUpdate(@NonNull T existing, @NonNull T newOne);


}