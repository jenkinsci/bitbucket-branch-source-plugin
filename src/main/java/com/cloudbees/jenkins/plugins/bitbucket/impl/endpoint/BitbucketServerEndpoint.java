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
package com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint;

import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpointDescriptor;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpointProvider;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.EndpointType;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookDescriptor;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.URLUtils;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.server.ServerWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.server.BitbucketServerVersion;
import com.damnhandy.uri.template.UriTemplate;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMName;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Represents a Bitbucket Server instance.
 *
 * @since 2.2.0
 */
public class BitbucketServerEndpoint extends AbstractBitbucketEndpoint {

    /**
     * Common prefixes that we should remove when inferring a display name.
     */
    private static final String[] COMMON_PREFIX_HOSTNAMES = {
            "git.",
            "bitbucket.",
            "bb.",
            "stash.",
            "vcs.",
            "scm.",
            "source."
    };

    @NonNull
    public static BitbucketServerVersion findServerVersion(String serverURL) {
        return BitbucketEndpointProvider
                .lookupEndpoint(serverURL, BitbucketServerEndpoint.class)
                .map(endpoint -> endpoint.getServerVersion())
                .map(BitbucketServerVersion::valueOf)
                .orElse(BitbucketServerVersion.getMinSupportedVersion());
    }

    /**
     * Optional name to use to describe the end-point.
     */
    @CheckForNull
    private final String displayName;

    /**
     * The URL of this Bitbucket Server.
     */
    @NonNull
    private final String serverURL;

    /**
     * The server version for this endpoint.
     */
    private BitbucketServerVersion serverVersion = BitbucketServerVersion.getMinSupportedVersion();

    /**
     * Default constructor.
     * @param serverURL
     */
    public BitbucketServerEndpoint(@CheckForNull String displayName, @NonNull String serverURL) {
        this(displayName, serverURL, new ServerWebhookConfiguration(false, null, false, null));
    }

    /**
     * Constructor.
     *
     * @param displayName Optional name to use to describe the end-point.
     * @param serverURL The URL of this Bitbucket Server
     * @param webhook implementation to work for this end-point.
     */
    @DataBoundConstructor
    public BitbucketServerEndpoint(@CheckForNull String displayName, @NonNull String serverURL, @NonNull BitbucketWebhookConfiguration webhook) {
        super(webhook);
        // use fixNull to silent nullability check
        this.serverURL = Util.fixNull(URLUtils.normalizeURL(serverURL));
        this.displayName = StringUtils.isBlank(displayName)
                ? SCMName.fromUrl(this.serverURL, COMMON_PREFIX_HOSTNAMES)
                        : displayName.trim();
    }

    @NonNull
    @Override
    public EndpointType getType() {
        return EndpointType.SERVER;
    }

    @NonNull
    public String getServerVersion() {
        if (serverVersion == null) {
            // force value to the minimum supported version
            this.serverVersion = BitbucketServerVersion.getMinSupportedVersion();
        }
        return this.serverVersion.name();
    }

    @DataBoundSetter
    public void setServerVersion(@NonNull String serverVersion) {
        try {
            this.serverVersion = BitbucketServerVersion.valueOf(serverVersion);
        } catch (IllegalArgumentException e) {
            // use default
            this.serverVersion = BitbucketServerVersion.getMinSupportedVersion();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getServerURL() {
        return serverURL;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getRepositoryURL(@NonNull String repoOwner, @NonNull String repository) {
        UriTemplate template = UriTemplate
                .fromTemplate(serverURL + "/{userOrProject}/{owner}/repos/{repo}")
                .set("repo", repository);
        return repoOwner.startsWith("~")
                ? template.set("userOrProject", "users").set("owner", repoOwner.substring(1)).expand()
                        : template.set("userOrProject", "projects").set("owner", repoOwner).expand();
    }

    @Override
    protected Object readResolve() {
        if (serverVersion == null) {
            serverVersion = BitbucketServerVersion.getMinSupportedVersion();
        }
        return super.readResolve();
    }

    /**
     * Our descriptor.
     */
    @Extension
    public static class DescriptorImpl extends BitbucketEndpointDescriptor {

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.BitbucketServerEndpoint_displayName();
        }

        @Restricted(NoExternalUse.class)
        @RequirePOST
        public ListBoxModel doFillServerVersionItems() {
            Jenkins.get().checkPermission(Jenkins.MANAGE);

            ListBoxModel items = new ListBoxModel();
            for (BitbucketServerVersion serverVersion : BitbucketServerVersion.values()) {
                items.add(serverVersion, serverVersion.name());
            }

            return items;
        }

        /**
         * Checks that the supplied URL is valid.
         *
         * @param value the URL to check.
         * @return the validation results.
         */
        @RequirePOST
        public FormValidation doCheckServerURL(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.MANAGE);

            try {
                new URL(value);
            } catch (MalformedURLException e) {
                return FormValidation.error("Invalid URL: " + e.getMessage());
            }
            return FormValidation.ok();
        }

        @RequirePOST
        public Collection<? extends Descriptor<?>> getWebhookDescriptors() {
            Jenkins.get().checkPermission(Jenkins.MANAGE);

            return ExtensionList.lookup(BitbucketWebhookDescriptor.class).stream()
                    .filter(webhook -> webhook.isApplicable(EndpointType.SERVER))
                    .toList();
        }
    }

}
