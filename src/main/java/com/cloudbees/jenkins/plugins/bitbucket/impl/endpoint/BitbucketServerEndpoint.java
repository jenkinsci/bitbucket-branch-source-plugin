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
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.URLUtils;
import com.cloudbees.jenkins.plugins.bitbucket.server.BitbucketServerVersion;
import com.cloudbees.jenkins.plugins.bitbucket.server.BitbucketServerWebhookImplementation;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.damnhandy.uri.template.UriTemplate;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Util;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;
import jenkins.scm.api.SCMName;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import static java.util.Objects.requireNonNull;

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
    public static BitbucketServerWebhookImplementation findWebhookImplementation(String serverURL) {
        return BitbucketEndpointProvider.lookupEndpoint(serverURL, BitbucketServerEndpoint.class)
                .map(BitbucketServerEndpoint::getWebhookImplementation)
                .orElse(BitbucketServerWebhookImplementation.NATIVE);
    }

    @NonNull
    public static BitbucketServerVersion findServerVersion(String serverURL) {
        return BitbucketEndpointProvider
                .lookupEndpoint(serverURL, BitbucketServerEndpoint.class)
                .map(endpoint -> endpoint.getServerVersion())
                .orElse(BitbucketServerVersion.VERSION_7);
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
    private final String serverUrl;

    @NonNull
    private BitbucketServerWebhookImplementation webhookImplementation = BitbucketServerWebhookImplementation.PLUGIN;

    /**
     * The server version for this endpoint.
     */
    private BitbucketServerVersion serverVersion = BitbucketServerVersion.VERSION_7;

    /**
     * Whether to always call the can merge api when retrieving pull requests.
     */
    private boolean callCanMerge = true;

    /**
     * Whether to always call the can diff api when retrieving pull requests.
     */
    private boolean callChanges = true;

    /**
     * Default constructor.
     * @param serverURL
     */
    public BitbucketServerEndpoint(@NonNull String serverURL) {
        this(null, serverURL, false, null, false, null);
    }

    @Deprecated(since = "936.3.1")
    public BitbucketServerEndpoint(@CheckForNull String displayName, @NonNull String serverUrl,
                                   boolean manageHooks, @CheckForNull String credentialsId) {
        this(displayName, serverUrl, manageHooks, credentialsId, false, null);
    }

    /**
     * Constructor.
     *
     * @param displayName Optional name to use to describe the end-point.
     * @param serverUrl The URL of this Bitbucket Server
     * @param manageHooks {@code true} if and only if Jenkins is supposed to
     *        auto-manage hooks for this end-point.
     * @param credentialsId The {@link StandardCredentials#getId()} of the
     *        credentials to use for auto-management of hooks.
     * @param enableHookSignature {@code true} hooks that comes Bitbucket Data
     *        Center are signed.
     * @param hookSignatureCredentialsId The {@link StringCredentials#getId()} of the
     *        credentials to use for verify the signature of payload.
     */
    @DataBoundConstructor
    public BitbucketServerEndpoint(@CheckForNull String displayName, @NonNull String serverUrl,
                                   boolean manageHooks, @CheckForNull String credentialsId,
                                   boolean enableHookSignature, @CheckForNull String hookSignatureCredentialsId) {
        super(manageHooks, credentialsId, enableHookSignature, hookSignatureCredentialsId);
        // use fixNull to silent nullability check
        this.serverUrl = Util.fixNull(URLUtils.normalizeURL(serverUrl));
        this.displayName = StringUtils.isBlank(displayName)
                ? SCMName.fromUrl(this.serverUrl, COMMON_PREFIX_HOSTNAMES)
                : displayName.trim();
    }

    public boolean isCallCanMerge() {
        return callCanMerge;
    }

    @NonNull
    @Override
    public EndpointType getType() {
        return EndpointType.SERVER;
    }

    @DataBoundSetter
    public void setCallCanMerge(boolean callCanMerge) {
        this.callCanMerge = callCanMerge;
    }

    public boolean isCallChanges() {
        return callChanges;
    }

    @DataBoundSetter
    public void setCallChanges(boolean callChanges) {
        this.callChanges = callChanges;
    }

    @NonNull
    public BitbucketServerVersion getServerVersion() {
        return this.serverVersion;
    }

    @DataBoundSetter
    public void setServerVersion(@NonNull BitbucketServerVersion serverVersion) {
        this.serverVersion = Objects.requireNonNull(serverVersion);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return displayName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    @Deprecated(since = "936.4.0", forRemoval = true)
    public String getServerUrl() {
        return serverUrl;
    }

    @Override
    public String getServerURL() {
        return getServerUrl();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getRepositoryUrl(@NonNull String repoOwner, @NonNull String repository) {
        return getRepositoryURL(repoOwner, repository);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getRepositoryURL(@NonNull String repoOwner, @NonNull String repository) {
        UriTemplate template = UriTemplate
                .fromTemplate(serverUrl + "/{userOrProject}/{owner}/repos/{repo}")
                .set("repo", repository);
        return repoOwner.startsWith("~")
                ? template.set("userOrProject", "users").set("owner", repoOwner.substring(1)).expand()
                        : template.set("userOrProject", "projects").set("owner", repoOwner).expand();
    }

    @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification = "Only non-null after we set them here!")
    private Object readResolve() {
        if (webhookImplementation == null) {
            webhookImplementation = BitbucketServerWebhookImplementation.PLUGIN;
        }
        if (getBitbucketJenkinsRootUrl() != null) {
            setBitbucketJenkinsRootUrl(getBitbucketJenkinsRootUrl());
        }
        if (serverVersion == null) {
            serverVersion = BitbucketServerVersion.VERSION_7;
        }

        return this;
    }

    @NonNull
    public BitbucketServerWebhookImplementation getWebhookImplementation() {
        return webhookImplementation;
    }

    @DataBoundSetter
    public void setWebhookImplementation(@NonNull BitbucketServerWebhookImplementation webhookImplementation) {
        this.webhookImplementation = requireNonNull(webhookImplementation);
    }

    /**
     * Our descriptor.
     */
    @Extension
    public static class DescriptorImpl extends BitbucketEndpointDescriptor {

        @Restricted(NoExternalUse.class) // stapler
        @RequirePOST
        public FormValidation doCheckEnableHookSignature(@QueryParameter BitbucketServerWebhookImplementation webhookImplementation,
                                                         @QueryParameter boolean enableHookSignature) {
            if (enableHookSignature && webhookImplementation == BitbucketServerWebhookImplementation.PLUGIN) {
                return FormValidation.error("Signature verification not supported for PLUGIN webhook");
            }
            return FormValidation.ok();
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.BitbucketServerEndpoint_displayName();
        }

        @Restricted(NoExternalUse.class)
        public ListBoxModel doFillWebhookImplementationItems() {
            ListBoxModel items = new ListBoxModel();
            for (BitbucketServerWebhookImplementation webhookImplementation : BitbucketServerWebhookImplementation.values()) {
                items.add(webhookImplementation, webhookImplementation.name());
            }

            return items;
        }

        @Restricted(NoExternalUse.class)
        public ListBoxModel doFillServerVersionItems() {
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
        public static FormValidation doCheckServerUrl(@QueryParameter String value) {
            try {
                new URL(value);
            } catch (MalformedURLException e) {
                return FormValidation.error("Invalid URL: " + e.getMessage());
            }
            return FormValidation.ok();
        }

    }

}
