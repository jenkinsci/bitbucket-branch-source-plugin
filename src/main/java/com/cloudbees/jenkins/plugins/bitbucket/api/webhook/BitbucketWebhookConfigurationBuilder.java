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
package com.cloudbees.jenkins.plugins.bitbucket.api.webhook;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;

/**
 * Base interface that a builder must implement or extend to provide an instance
 * of {@link BitbucketWebhookConfiguration}.
 *
 * @since 937.1.0
 */
public interface BitbucketWebhookConfigurationBuilder extends ExtensionPoint {

    /**
     * Returns the identifier of built {@link BitbucketWebhookConfiguration}.
     *
     * @return configuration identifier
     * @see BitbucketWebhookConfiguration#getId()
     */
    @NonNull
    String getId();

    /**
     * Enable the auto manage of webhook for each repository in a Jenkins
     * project.
     *
     * @param credentialsId with admin right to add, update or delete webhook of
     *        a bitbucket repository
     * @return builder itself
     */
    BitbucketWebhookConfigurationBuilder autoManaged(@NonNull String credentialsId);

    /**
     * Set the Jenkins root URL used to send event payload.
     *
     * @param callbackRootURL URL of Jenkins accessible from the Bitbucket
     *        server instance.
     * @return builder itself
     */
    BitbucketWebhookConfigurationBuilder callbackRootURL(@NonNull String callbackRootURL);

    /**
     * Returns an instance of {@link BitbucketWebhookConfiguration} using the
     * provided configuration.
     *
     * @return instance of {@link BitbucketWebhookConfiguration}
     */
    BitbucketWebhookConfiguration build();

}
