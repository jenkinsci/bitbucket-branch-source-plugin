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

import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;

/**
 * Listener for {@link BitbucketWebhookProcessor} to receive notification about
 * each steps done by the matching processor for an incoming webhook.
 */
public interface BitbucketWebhookProcessorListener extends ExtensionPoint {

    /**
     * Notify when the processor has been matches.
     *
     * @param processor class
     */
    void onStart(@NonNull Class<? extends BitbucketWebhookProcessor> processor);

    /**
     * Notify after the processor has processed the incoming webhook payload.
     *
     * @param eventType of incoming request
     * @param payload content that comes with incoming request
     * @param endpoint that match the incoming request
     */
    void onProcess(@NonNull String eventType, @NonNull String payload, @NonNull BitbucketEndpoint endpoint);

    /**
     * Notify of failure while processing the incoming webhook.
     *
     * @param failure exception raised by webhook consumer or by processor.
     */
    void onFailure(@NonNull BitbucketWebhookProcessorException failure);
}
