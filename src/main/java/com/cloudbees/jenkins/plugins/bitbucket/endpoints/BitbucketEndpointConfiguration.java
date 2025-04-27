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
     * The list of {@link AbstractBitbucketEndpoint}, this is subject to the constraint that there can only ever be
     * one entry for each {@link AbstractBitbucketEndpoint#getServerUrl()}.
     */
    private List<AbstractBitbucketEndpoint> endpoints;

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
     * @param bitbucketServerUrl the value of the old url field.
     * @return the value of the new url field.
     */
    @Restricted(NoExternalUse.class) // only for plugin internal use.
    @NonNull
    public String readResolveServerUrl(@CheckForNull String bitbucketServerUrl) {
        String serverUrl = normalizeServerUrl(bitbucketServerUrl);
        serverUrl = StringUtils.defaultIfBlank(serverUrl, BitbucketCloudEndpoint.SERVER_URL);
        AbstractBitbucketEndpoint endpoint = findEndpoint(serverUrl).orElse(null);
        if (endpoint == null && ACL.SYSTEM2.equals(Jenkins.getAuthentication2())) {
            if (BitbucketCloudEndpoint.SERVER_URL.equals(serverUrl)
                    || BitbucketCloudEndpoint.BAD_SERVER_URL.equals(serverUrl)) {
                // exception case
                addEndpoint(new BitbucketCloudEndpoint(false, null));
            } else {
                addEndpoint(new BitbucketServerEndpoint(null, serverUrl, false, null));
            }
        }
        return endpoint == null ?  serverUrl : endpoint.getServerUrl();
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
        for (AbstractBitbucketEndpoint endpoint : getEndpoints()) {
            String serverUrl = endpoint.getServerUrl();
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
    public synchronized List<AbstractBitbucketEndpoint> getEndpoints() {
        return endpoints == null || endpoints.isEmpty()
                ? Collections.<AbstractBitbucketEndpoint>singletonList(new BitbucketCloudEndpoint(false, null))
                : Collections.unmodifiableList(endpoints);
    }

    /**
     * Sets the list of endpoints.
     *
     * @param endpoints the list of endpoints.
     */
    public synchronized void setEndpoints(@CheckForNull List<? extends AbstractBitbucketEndpoint> endpoints) {
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        List<AbstractBitbucketEndpoint> eps = new ArrayList<>(Util.fixNull(endpoints));
        // remove duplicates and empty urls
        Set<String> serverUrls = new HashSet<>();
        for (ListIterator<AbstractBitbucketEndpoint> iterator = eps.listIterator(); iterator.hasNext(); ) {
            AbstractBitbucketEndpoint endpoint = iterator.next();
            String serverUrl = endpoint.getServerUrl();
            if (StringUtils.isBlank(serverUrl) || serverUrls.contains(serverUrl)) {
                iterator.remove();
                continue;
            } else if (!(endpoint instanceof BitbucketCloudEndpoint)
                    && BitbucketCloudEndpoint.SERVER_URL.equals(serverUrl)) {
                // fix type for the special case
                iterator.set(new BitbucketCloudEndpoint(endpoint.isManageHooks(), endpoint.getCredentialsId(), endpoint.getBitbucketJenkinsRootUrl()));
            }
            serverUrls.add(serverUrl);
        }
        if (eps.isEmpty()) {
            eps.add(new BitbucketCloudEndpoint(false, null));
        }
        this.endpoints = eps;
        save();
    }

    /**
     * Adds an endpoint.
     *
     * @param endpoint the endpoint to add.
     * @return {@code true} if the list of endpoints was modified
     */
    public synchronized boolean addEndpoint(@NonNull AbstractBitbucketEndpoint endpoint) {
        List<AbstractBitbucketEndpoint> endpoints = new ArrayList<>(getEndpoints());
        for (AbstractBitbucketEndpoint ep : endpoints) {
            if (ep.getServerUrl().equals(endpoint.getServerUrl())) {
                return false;
            }
        }
        endpoints.add(endpoint);
        setEndpoints(endpoints);
        return true;
    }

    /**
     * Updates an existing endpoint (or adds if missing).
     *
     * @param endpoint the endpoint to update.
     */
    public synchronized void updateEndpoint(@NonNull AbstractBitbucketEndpoint endpoint) {
        List<AbstractBitbucketEndpoint> endpoints = new ArrayList<>(getEndpoints());
        boolean found = false;
        for (int i = 0; i < endpoints.size(); i++) {
            AbstractBitbucketEndpoint ep = endpoints.get(i);
            if (ep.getServerUrl().equals(endpoint.getServerUrl())) {
                endpoints.set(i, endpoint);
                found = true;
                break;
            }
        }
        if (!found) {
            endpoints.add(endpoint);
        }
        setEndpoints(endpoints);
    }

    /**
     * Removes an endpoint.
     *
     * @param endpoint the endpoint to remove.
     * @return {@code true} if the list of endpoints was modified
     */
    public boolean removeEndpoint(@NonNull AbstractBitbucketEndpoint endpoint) {
        return removeEndpoint(endpoint.getServerUrl());
    }

    /**
     * Removes an endpoint.
     *
     * @param serverURL the server URL to remove.
     * @return {@code true} if the list of endpoints was modified
     */
    public synchronized boolean removeEndpoint(@CheckForNull String serverURL) {
        String fixedServerURL = normalizeServerUrl(serverURL);
        List<AbstractBitbucketEndpoint> newEndpoints = new ArrayList<>(getEndpoints());
        boolean modified = newEndpoints.removeIf(endpoint -> Objects.equals(fixedServerURL, endpoint.getServerUrl()));
        setEndpoints(newEndpoints);
        return modified;
    }

    /**
     * Checks to see if the supplied server URL is defined in the global configuration.
     *
     * @param serverURL the server url to check.
     * @return the global configuration for the specified server url or {@code null} if not defined.
     */
    public synchronized Optional<AbstractBitbucketEndpoint> findEndpoint(@CheckForNull String serverURL) {
        serverURL = normalizeServerUrl(serverURL);
        for (AbstractBitbucketEndpoint endpoint : getEndpoints()) {
            if (Objects.equals(serverURL, endpoint.getServerUrl())) {
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
    public synchronized <T extends AbstractBitbucketEndpoint> Optional<T> findEndpoint(@CheckForNull String serverURL,
                                                                                       Class<T> clazz) {
        return findEndpoint(serverURL)
            .filter(clazz::isInstance)
            .map(clazz::cast);
    }

    @NonNull
    public synchronized AbstractBitbucketEndpoint getDefaultEndpoint() {
        return getEndpoints().get(0);
    }

    /**
     * Fix a serverUrl.
     *
     * @param serverURL the server URL.
     * @return the normalized server URL.
     */
    @CheckForNull
    public static String normalizeServerUrl(@CheckForNull String serverURL) {
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
