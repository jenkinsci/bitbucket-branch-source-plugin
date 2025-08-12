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
package com.cloudbees.jenkins.plugins.bitbucket.api.webhook;

import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.EndpointType;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Descriptor;

/**
 * {@link Descriptor} for {@link BitbucketWebhookConfiguration}s.
 *
 * @since 937.0.0
 */
public abstract class BitbucketWebhookDescriptor extends Descriptor<BitbucketWebhookConfiguration> {

    /**
     * Returns if this implementation can supports and can be installed by the
     * given endpoint type.
     *
     * @param type of the endpoint
     * @return {@code true} if this implementation can manage payload from this
     *         endpoint, {@code false} otherwise.
     */
    public abstract boolean isApplicable(@NonNull EndpointType type);

}
