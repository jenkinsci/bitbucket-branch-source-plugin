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

import com.google.common.base.Objects;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.ExtensionList;

/**
 * Provider of {@link BitbucketWebhookConfiguration} builders registered in the
 * system.
 *
 * @since 937.1.0
 */
public final class BitbucketWebhookConfigurationsBuilder {

    private BitbucketWebhookConfigurationsBuilder() {
    }

    /**
     * Returns a {@link BitbucketWebhookConfiguration} builder for the given
     * configuration identifier.
     *
     * @param <T> specific builder interface
     * @param id webhook configuration identifier
     * @param builderInterface class of specific builder
     * @return an instance of {@link BitbucketWebhookConfigurationBuilder},
     *         {@code null} otherwise if no builder found with the given
     *         paramenters.
     */
    @Nullable
    public static <T extends BitbucketWebhookConfigurationBuilder> T lookup(@NonNull String id, Class<T> builderInterface) {
        return ExtensionList.lookup(builderInterface) //
                .stream() //
                .filter(provider -> Objects.equal(id, provider.getId())) //
                .findFirst() //
                .orElse(null);
    }

    /**
     * Returns a {@link BitbucketWebhookConfiguration} builder for the given
     * configuration identifier.
     *
     * @param id webhook configuration identifier
     * @return an instance of {@link BitbucketWebhookConfigurationBuilder},
     *         {@code null} otherwise if no builder found.
     */
    @Nullable
    public static BitbucketWebhookConfigurationBuilder lookup(@NonNull String id) {
        return lookup(id, BitbucketWebhookConfigurationBuilder.class);
    }
}
