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

import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketApiUtils;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.util.ListBoxModel;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Represents the global configuration of Bitbucket Cloud and Bitbucket Server endpoints.
 *
 * @since 2.2.0
 */
@Extension
public class BitbucketEndpointConfiguration extends GlobalConfiguration {

    /**
     * The list of {@link BitbucketEndpoint}, this is subject to the constraint that there can only ever be
     * one entry for each {@link BitbucketEndpoint#getServerURL()}.
     */
    private List<BitbucketEndpoint> endpoints;

    /**
     * Constructor.
     */
    public BitbucketEndpointConfiguration() {
        load();
    }

    /**
     * Gets the {@link BitbucketEndpointConfiguration} singleton.
     *
     * @return the {@link BitbucketEndpointConfiguration} singleton.
     */
    public static BitbucketEndpointConfiguration get() {
        return ExtensionList.lookup(GlobalConfiguration.class).get(BitbucketEndpointConfiguration.class);
    }

    @NonNull
    @Override
    public Permission getRequiredGlobalConfigPagePermission() {
        return Jenkins.MANAGE;
    }

    /**
     * Called from a {@code readResolve()} method only to convert the old {@code bitbucketServerUrl} field into the new
     * {@code serverUrl} field. When called from {@link ACL#SYSTEM} this will update the configuration with the
     * missing definitions of resolved URLs.
     *
     * @param serverURL the value of the old url field.
     * @return the value of the new url field.
     */
    @Restricted(NoExternalUse.class) // only for plugin internal use.
    @NonNull
    public String readResolveServerUrl(@CheckForNull String serverURL) {
        String normalizedURL = normalizeServerURL(serverURL);
        normalizedURL = StringUtils.defaultIfBlank(normalizedURL, BitbucketCloudEndpoint.SERVER_URL);
        BitbucketEndpoint endpoint = findEndpoint(normalizedURL).orElse(null);
        if (endpoint == null && ACL.SYSTEM2.equals(Jenkins.getAuthentication2())) {
            if (BitbucketCloudEndpoint.SERVER_URL.equals(normalizedURL)
                    || BitbucketCloudEndpoint.BAD_SERVER_URL.equals(normalizedURL)) {
                // exception case
                addEndpoint(new BitbucketCloudEndpoint(false, null));
            } else {
                addEndpoint(new BitbucketServerEndpoint(null, normalizedURL, false, null));
            }
        }
        return endpoint == null ?  normalizedURL : endpoint.getServerURL();
    }

    /**
     * Returns {@code true} if and only if there is more than one configured endpoint.
     *
     * @return {@code true} if and only if there is more than one configured endpoint.
     */
    public boolean isEndpointSelectable() {
        return getEndpoints().size() > 1;
    }

    /**
     * Populates a {@link ListBoxModel} with the endpoints.
     *
     * @return A {@link ListBoxModel} with all the endpoints
     */
    public ListBoxModel getEndpointItems() {
        ListBoxModel result = new ListBoxModel();
        for (BitbucketEndpoint endpoint : getEndpoints()) {
            String serverUrl = endpoint.getServerURL();
            String displayName = endpoint.getDisplayName();
            result.add(StringUtils.isBlank(displayName) ? serverUrl : displayName + " (" + serverUrl + ")", serverUrl);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        return true;
    }

    /**
     * Gets the list of endpoints.
     *
     * @return the list of endpoints
     */
    @NonNull
    public List<BitbucketEndpoint> getEndpoints() {
        return endpoints == null || endpoints.isEmpty()
                ? Collections.<BitbucketEndpoint>singletonList(new BitbucketCloudEndpoint(false, null))
                : List.copyOf(endpoints);
    }

    /**
     * Sets the list of endpoints.
     *
     * @param endpoints the list of endpoints.
     */
    public void setEndpoints(@CheckForNull List<? extends BitbucketEndpoint> endpoints) {
        Jenkins.get().checkPermission(Jenkins.MANAGE);

        List<BitbucketEndpoint> eps = new ArrayList<>(Util.fixNull(endpoints));
        // remove duplicates and empty urls
        Set<String> serverURLs = new HashSet<>();
        for (ListIterator<BitbucketEndpoint> iterator = eps.listIterator(); iterator.hasNext(); ) {
            BitbucketEndpoint endpoint = iterator.next();
            String serverURL = endpoint.getServerURL();
            if (StringUtils.isBlank(serverURL) || serverURLs.contains(serverURL)) {
                iterator.remove();
                continue;
            } else if (!(endpoint instanceof BitbucketCloudEndpoint) && BitbucketApiUtils.isCloud(serverURL)) {
                // fix type for the special case
                BitbucketCloudEndpoint cloudEndpoint = new BitbucketCloudEndpoint(endpoint.isManageHooks(), endpoint.getCredentialsId(), endpoint.getEndpointJenkinsRootURL());
                cloudEndpoint.setEnableHookSignature(endpoint.isEnableHookSignature());
                cloudEndpoint.setHookSignatureCredentialsId(endpoint.getHookSignatureCredentialsId());
                iterator.set(endpoint);
            }
            serverURLs.add(serverURL);
        }
        if (eps.isEmpty()) {
            eps.add(new BitbucketCloudEndpoint(false, null));
        }
        this.endpoints = new CopyOnWriteArrayList<>(eps);
        save();
    }

    /**
     * Adds an endpoint.
     *
     * @param endpoint the endpoint to add.
     * @return {@code true} if the list of endpoints was modified
     */
    public boolean addEndpoint(@NonNull BitbucketEndpoint endpoint) {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        List<BitbucketEndpoint> newEndpoints = new ArrayList<>(endpoints);
        for (BitbucketEndpoint ep : newEndpoints) {
            if (endpoint.isEquals(ep)) {
                return false;
            }
        }
        newEndpoints.add(endpoint);
        return true;
    }

    /**
     * Updates an existing endpoint (or adds if missing).
     *
     * @param endpoint the endpoint to update.
     */
    public void updateEndpoint(@NonNull BitbucketEndpoint endpoint) {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        List<BitbucketEndpoint> newEndpoints = endpoints;
        boolean found = false;
        for (int i = 0; i < newEndpoints.size(); i++) {
            BitbucketEndpoint ep = newEndpoints.get(i);
            if (endpoint.isEquals(ep)) {
                newEndpoints.set(i, endpoint);
                found = true;
                break;
            }
        }
        if (!found) {
            newEndpoints.add(endpoint);
        }
    }

    /**
     * Removes an endpoint.
     *
     * @param endpoint the endpoint to remove.
     * @return {@code true} if the list of endpoints was modified
     */
    public boolean removeEndpoint(@NonNull BitbucketEndpoint endpoint) {
        return endpoints.removeIf(e -> e.isEquals(endpoint));
    }

    /**
     * Removes an endpoint.
     *
     * @param serverURL the server URL to remove.
     * @return {@code true} if the list of endpoints was modified
     */
    public synchronized boolean removeEndpoint(@CheckForNull String serverURL) {
        String fixedServerURL = normalizeServerURL(serverURL);
        return endpoints.removeIf(e -> Objects.equals(fixedServerURL, e.getServerURL()));
    }

    /**
     * Checks to see if the supplied server URL is defined in the global configuration.
     *
     * @param serverURL the server url to check.
     * @return the global configuration for the specified server url or {@code null} if not defined.
     */
    public synchronized Optional<BitbucketEndpoint> findEndpoint(@CheckForNull String serverURL) {
        serverURL = normalizeServerURL(serverURL);
        for (BitbucketEndpoint endpoint : getEndpoints()) {
            if (Objects.equals(serverURL, endpoint.getServerURL())) {
                return Optional.of(endpoint);
            }
        }
        return Optional.empty();
    }

    /**
     * Checks to see if the supplied server URL is defined in the global configuration.
     *
     * @param serverURL the server url to check.
     * @param clazz the class to check.
     * @return the global configuration for the specified server url or {@code null} if not defined.
     */
    public synchronized <T extends BitbucketEndpoint> Optional<T> findEndpoint(@CheckForNull String serverURL,
                                                                                       Class<T> clazz) {
        return findEndpoint(serverURL)
            .filter(clazz::isInstance)
            .map(clazz::cast);
    }

    @NonNull
    public synchronized BitbucketEndpoint getDefaultEndpoint() {
        return getEndpoints().get(0);
    }

    /**
     * Fix a serverUrl.
     *
     * @param serverURL the server URL.
     * @return the normalized server URL.
     * @deprecated
     * @see #normalizeServerURL
     */
    @CheckForNull
    @Deprecated(forRemoval = true)
    public static String normalizeServerUrl(@CheckForNull String serverURL) {
        return normalizeServerURL(serverURL);
    }

    /**
     * Fix a server URL.
     *
     * @param serverURL the server URL.
     * @return the normalized server URL.
     */
    @Restricted(NoExternalUse.class) // only for plugin internal use.
    @CheckForNull
    public static String normalizeServerURL(@CheckForNull String serverURL) {
        if (StringUtils.isBlank(serverURL)) {
            return null;
        }
        try {
            URI uri = new URI(serverURL).normalize();
            String scheme = uri.getScheme();
            if ("http".equals(scheme) || "https".equals(scheme)) {
                // we only expect http / https, but also these are the only ones where we know the authority
                // is server based, i.e. [userinfo@]server[:port]
                // DNS names must be US-ASCII and are case insensitive, so we force all to lowercase

                String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ENGLISH);
                int port = uri.getPort();
                if ("http".equals(scheme) && port == 80) {
                    port = -1;
                } else if ("https".equals(scheme) && port == 443) {
                    port = -1;
                }
                serverURL = new URI(
                        scheme,
                        uri.getUserInfo(),
                        host,
                        port,
                        uri.getPath(),
                        uri.getQuery(),
                        uri.getFragment()
                ).toASCIIString();
            }
        } catch (URISyntaxException e) {
            // ignore, this was a best effort tidy-up
        }
        return serverURL.replaceAll("/$", "");
    }

}
