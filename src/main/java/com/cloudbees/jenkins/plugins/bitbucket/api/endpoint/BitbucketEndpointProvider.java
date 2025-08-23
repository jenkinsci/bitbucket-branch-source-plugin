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

import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketServerEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketApiUtils;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.URLUtils;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Util;
import hudson.util.ListBoxModel;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.apache.commons.lang3.StringUtils;

/**
 * A provider of {@link BitbucketEndpoint}s
 *
 * @since 936.4.0
 */
public final class BitbucketEndpointProvider{

    private BitbucketEndpointProvider() {
    }

    /**
     * Returns all configured Bitbucket endpoints in the global page.
     *
     * @return a list of Bitbucket endpoints.
     */
    @NonNull
    public static List<BitbucketEndpoint> all() {
        return List.copyOf(BitbucketEndpointConfiguration.get().getEndpoints());
    }

    /**
     * Populates a {@link ListBoxModel} with all Bitbucket endpoints in the
     * global page.
     *
     * @return a {@link ListBoxModel} with all the endpoints
     */
    @NonNull
    public static ListBoxModel listEndpoints() {
        ListBoxModel result = new ListBoxModel();
        for (BitbucketEndpoint endpoint : all()) {
            String serverUrl = endpoint.getServerURL();
            String displayName = endpoint.getDisplayName();
            result.add(StringUtils.isBlank(displayName) ? serverUrl : displayName + " (" + serverUrl + ")", serverUrl);
        }
        return result;
    }

    /**
     * Checks to see if the supplied server URL is defined in the global
     * configuration.
     *
     * @param serverURL the server url to check.
     * @return the global configuration for the specified server URL or
     *         {@code Optional#empty()} if not found.
     */
    @SuppressWarnings("unchecked")
    public static <T extends BitbucketEndpoint> Optional<T> lookupEndpoint(@CheckForNull String serverURL) {
        String normalizedServerURL = URLUtils.normalizeURL(serverURL);
        return BitbucketEndpointConfiguration.get()
                .getEndpoints().stream()
                .filter(endpoint -> Objects.equals(normalizedServerURL, endpoint.getServerURL()))
                .map(endpoint -> (T) endpoint)
                .findFirst();
    }

    /**
     * Jenkins Server Root URL to be used by the Bitbucket endpoint that matches
     * the given serverURL. The global setting from Jenkins.get().getRootUrl()
     * will be used if this field is null or equals an empty string.
     *
     * @param serverURL the server url to check.
     * @return the verbatim setting provided by endpoint configuration
     * @deprecated This value depends on the underline {@link BitbucketWebhookConfiguration} configured for the endpoint.
     */
    @Deprecated(since = "937.0.0", forRemoval = true)
    @NonNull
    public static String lookupEndpointJenkinsRootURL(@CheckForNull String serverURL) {
        String jenkinsURL = lookupEndpoint(serverURL)
                .map(BitbucketEndpoint::getEndpointJenkinsRootURL)
                .orElse(BitbucketWebhookConfiguration.getDefaultJenkinsRootURL());
        return Util.ensureEndsWith(jenkinsURL, "/");
    }

    /**
     * Checks to see if the supplied server URL is defined in the global
     * configuration.
     *
     * @param serverURL the server url to check.
     * @param clazz the class to check.
     * @return the global configuration for the specified server URL or
     *         {@code Optional#empty()} if not found.
     */
    public static <T extends BitbucketEndpoint> Optional<T> lookupEndpoint(@CheckForNull String serverURL, @NonNull Class<T> clazz) {
        return lookupEndpoint(serverURL)
                .filter(clazz::isInstance)
                .map(clazz::cast);
    }

    /**
     * Checks to see if the supplied server URL is defined in the global
     * configuration.
     *
     * @param type filter of the endpoint to return.
     * @return a collection of endpoints of given type.
     */
    @NonNull
    @SuppressWarnings("unchecked")
    public static <T extends BitbucketEndpoint> Collection<T> lookupEndpoint(@NonNull EndpointType type) {
        return all().stream()
                .filter(endpoint -> endpoint.getType() == type)
                .map(endpoint -> (T) endpoint)
                .toList();
    }

    /**
     * Register a new {@link BitbucketEndpoint} to the global configuration. The
     * endpoint is created with default values and could be customised by the
     * given endpointCustomiser.
     * <p>
     * The given customiser can also return a different implementation
     *
     * @param name of the endpoint, alias for label
     * @param serverURL the bitbucket endpoint URL
     * @param endpointCustomiser an optional customiser for the created endpoint
     * @return the registered endpoint instance.
     */
    public static BitbucketEndpoint registerEndpoint(@NonNull String name, @NonNull String serverURL, @Nullable UnaryOperator<BitbucketEndpoint> endpointCustomiser) {
        BitbucketEndpoint endpoint;
        if (BitbucketApiUtils.isCloud(serverURL)) {
            endpoint = new BitbucketCloudEndpoint();
        } else {
            endpoint = new BitbucketServerEndpoint(name, serverURL);
        }
        if (endpointCustomiser != null) {
            endpoint = endpointCustomiser.apply(endpoint);
        }
        BitbucketEndpointConfiguration.get().addEndpoint(endpoint);
        return endpoint;
    }

    /**
     * Removes a {@link BitbucketEndpoint} that matches the given URl from the
     * global configuration.
     *
     * @param serverURL the bitbucket endpoint URL
     * @return {@code true} if the endpoint has been removed from the global
     *         configuration, {@code false} otherwise
     */
    public static boolean unregisterEndpoint(@NonNull String serverURL) {
        return BitbucketEndpointConfiguration.get().removeEndpoint(serverURL);
    }
}
