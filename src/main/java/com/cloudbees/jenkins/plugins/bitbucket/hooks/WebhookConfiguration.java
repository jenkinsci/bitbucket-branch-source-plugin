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
package com.cloudbees.jenkins.plugins.bitbucket.hooks;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpointProvider;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudHook;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.AbstractBitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketServerEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketApiUtils;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketPluginWebhook;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketServerWebhook;
import com.damnhandy.uri.template.UriTemplate;
import com.google.common.base.Objects;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Util;
import hudson.util.Secret;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import org.apache.commons.collections.CollectionUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

/**
 * Contains the webhook configuration
 */
public class WebhookConfiguration {
    private static final Logger logger = Logger.getLogger(WebhookConfiguration.class.getName());

    /**
     * The list of events available in Bitbucket Cloud.
     */
    private static final List<String> CLOUD_EVENTS = Collections.unmodifiableList(Arrays.asList(
            HookEventType.PUSH.getKey(),
            HookEventType.PULL_REQUEST_CREATED.getKey(),
            HookEventType.PULL_REQUEST_UPDATED.getKey(),
            HookEventType.PULL_REQUEST_MERGED.getKey(),
            HookEventType.PULL_REQUEST_DECLINED.getKey()
    ));

    /**
     * The list of events available in Bitbucket Server v7.x.
     */
    private static final List<String> NATIVE_SERVER_EVENTS_v7 = Collections.unmodifiableList(Arrays.asList(
            HookEventType.SERVER_REFS_CHANGED.getKey(),
            HookEventType.SERVER_PULL_REQUEST_OPENED.getKey(),
            HookEventType.SERVER_PULL_REQUEST_MERGED.getKey(),
            HookEventType.SERVER_PULL_REQUEST_DECLINED.getKey(),
            HookEventType.SERVER_PULL_REQUEST_DELETED.getKey(),
            // only on v5.10 and above
            HookEventType.SERVER_PULL_REQUEST_MODIFIED.getKey(),
            HookEventType.SERVER_PULL_REQUEST_REVIEWER_UPDATED.getKey(),
            // only on v6.5 and above
            HookEventType.SERVER_MIRROR_REPO_SYNCHRONIZED.getKey(),
            // only on v7.x and above
            HookEventType.SERVER_PULL_REQUEST_FROM_REF_UPDATED.getKey()
    ));

    // See https://help.moveworkforward.com/BPW/how-to-manage-configurations-using-post-webhooks-f#HowtomanageconfigurationsusingPostWebhooksforBitbucketAPIs?-Possibleeventtypes
    private static final List<String> PLUGIN_SERVER_EVENTS = Collections.unmodifiableList(Arrays.asList(
            "ABSTRACT_REPOSITORY_REFS_CHANGED", // push event
            "BRANCH_CREATED",
            "BRANCH_DELETED",
            "PULL_REQUEST_DECLINED",
            "PULL_REQUEST_DELETED",
            "PULL_REQUEST_MERGED",
            "PULL_REQUEST_OPENED",
            "PULL_REQUEST_REOPENED",
            "PULL_REQUEST_UPDATED",
            "REPOSITORY_MIRROR_SYNCHRONIZED", // not supported by the hookprocessor
            "TAG_CREATED"));

    /**
     * The list of events available in Bitbucket Server v6.5+.
     */
    private static final List<String> NATIVE_SERVER_EVENTS_v6_5 = Collections.unmodifiableList(NATIVE_SERVER_EVENTS_v7.subList(0, 8));

    /**
     * The list of events available in Bitbucket Server v6.x.  Applies to v5.10+.
     */
    private static final List<String> NATIVE_SERVER_EVENTS_v6 = Collections.unmodifiableList(NATIVE_SERVER_EVENTS_v7.subList(0, 7));

    /**
     * The list of events available in Bitbucket Server v5.9-.
     */
    private static final List<String> NATIVE_SERVER_EVENTS_v5 = Collections.unmodifiableList(NATIVE_SERVER_EVENTS_v7.subList(0, 5));

    /**
     * The title of the webhook.
     */
    private static final String DESCRIPTION = "Jenkins hook";

    /**
     * The comma separated list of committers to ignore.
     */
    private final String committersToIgnore;

    public WebhookConfiguration() {
        this.committersToIgnore = null;
    }

    public WebhookConfiguration(@CheckForNull final String committersToIgnore) {
        this.committersToIgnore = committersToIgnore;
    }

    public String getCommittersToIgnore() {
        return this.committersToIgnore;
    }

    boolean updateHook(BitbucketWebHook hook, BitbucketSCMSource owner) {
        boolean updated = false;

        final String serverURL = owner.getServerUrl();
        final String rootURL = getEndpointJenkinsRootURL(serverURL);
        final String signatureSecret = getSecret(owner.getServerUrl());

        if (hook instanceof BitbucketCloudHook cloudHook) {
            String url = getCloudWebhookURL(serverURL, rootURL);
            if (!Objects.equal(hook.getUrl(), url)) {
                cloudHook.setUrl(url);
                updated = true;
            }

            List<String> events = hook.getEvents();
            if (!events.containsAll(CLOUD_EVENTS)) {
                Set<String> newEvents = new TreeSet<>(events);
                newEvents.addAll(CLOUD_EVENTS);
                cloudHook.setEvents(new ArrayList<>(newEvents));
                logger.info(() -> "Update cloud webhook because the following events was missing: " + CollectionUtils.subtract(CLOUD_EVENTS, events));
                updated = true;
            }

            if (!Objects.equal(hook.getSecret(), signatureSecret)) {
                cloudHook.setSecret(signatureSecret);
                updated = true;
            }
        } else if (hook instanceof BitbucketPluginWebhook pluginHook) {
            String hookCommittersToIgnore = Util.fixEmptyAndTrim(pluginHook.getCommittersToIgnore());
            String thisCommittersToIgnore = Util.fixEmptyAndTrim(committersToIgnore);
            if (!Objects.equal(thisCommittersToIgnore, hookCommittersToIgnore)) {
                pluginHook.setCommittersToIgnore(thisCommittersToIgnore);
                updated = true;
            }

            String url = getServerWebhookURL(serverURL, rootURL);
            if (!url.equals(pluginHook.getUrl())) {
                pluginHook.setUrl(url);
                updated = true;
            }

            if (!pluginHook.isActive()) {
                pluginHook.setActive(true);
                updated = true;
            }

            List<String> supportedPluginEvents = getPluginServerEvents(serverURL);
            List<String> events = pluginHook.getEvents();
            if (!events.containsAll(supportedPluginEvents)) {
                Set<String> newEvents = new TreeSet<>(events);
                newEvents.addAll(supportedPluginEvents);
                pluginHook.setEvents(new ArrayList<>(newEvents));
                logger.info(() -> "Update plugin webhook because the following events was missing: " + CollectionUtils.subtract(supportedPluginEvents, events));
                updated = true;
            }
        } else if (hook instanceof BitbucketServerWebhook serverHook) {
            String url = getServerWebhookURL(serverURL, rootURL);
            if (!url.equals(serverHook.getUrl())) {
                serverHook.setUrl(url);
                updated = true;
            }

            List<String> supportedNativeEvents = getNativeServerEvents(serverURL);
            List<String> events = serverHook.getEvents();
            if (!events.containsAll(supportedNativeEvents)) {
                Set<String> newEvents = new TreeSet<>(events);
                newEvents.addAll(supportedNativeEvents);
                serverHook.setEvents(new ArrayList<>(newEvents));
                logger.info(() -> "Update native webhook because the following events was missing: " + CollectionUtils.subtract(supportedNativeEvents, events));
                updated = true;
            }

            if (!Objects.equal(serverHook.getSecret(), signatureSecret)) {
                serverHook.setSecret(signatureSecret);
                updated = true;
            }
        }

        return updated;
    }

    @NonNull
    private String getEndpointJenkinsRootURL(@NonNull String serverURL) {
        return AbstractBitbucketEndpoint.getEndpointJenkinsRootUrl(serverURL);
    }

    @NonNull
    public BitbucketWebHook getHook(BitbucketSCMSource owner) {
        final String serverURL = owner.getServerUrl();
        final String rootURL = getEndpointJenkinsRootURL(serverURL);
        final String signatureSecret = getSecret(owner.getServerUrl());

        if (BitbucketApiUtils.isCloud(serverURL)) {
            BitbucketCloudHook hook = new BitbucketCloudHook();
            hook.setEvents(CLOUD_EVENTS);
            hook.setActive(true);
            hook.setDescription(DESCRIPTION);
            hook.setUrl(getCloudWebhookURL(serverURL, rootURL));
            hook.setSecret(signatureSecret);
            return hook;
        }

        switch (BitbucketServerEndpoint.findWebhookImplementation(serverURL)) {
            case NATIVE: {
                BitbucketServerWebhook hook = new BitbucketServerWebhook();
                hook.setActive(true);
                hook.setDescription(DESCRIPTION);
                hook.setEvents(getNativeServerEvents(serverURL));
                hook.setUrl(getServerWebhookURL(serverURL, rootURL));
                hook.setSecret(signatureSecret);
                return hook;
            }

            case PLUGIN:
            default: {
                BitbucketPluginWebhook hook = new BitbucketPluginWebhook();
                hook.setActive(true);
                hook.setDescription(DESCRIPTION);
                hook.setUrl(getServerWebhookURL(serverURL, rootURL));
                hook.setCommittersToIgnore(committersToIgnore);
                return hook;
            }
        }
    }

    @Nullable
    private String getSecret(@NonNull String serverURL) {
        BitbucketEndpoint endpoint = BitbucketEndpointProvider
                .lookupEndpoint(serverURL)
                .orElseThrow();
        if (endpoint.isEnableHookSignature()) {
            StringCredentials credentials = endpoint.hookSignatureCredentials();
            if (credentials != null) {
                return Secret.toString(credentials.getSecret());
            } else {
                throw new IllegalStateException("Credentials " + endpoint.getHookSignatureCredentialsId() + " not found on hook registration");
            }
        }
        return null;
    }

    private static List<String> getPluginServerEvents(String serverURL) {
        return PLUGIN_SERVER_EVENTS;
    }

    private static List<String> getNativeServerEvents(String serverURL) {
        BitbucketServerEndpoint endpoint = BitbucketEndpointProvider
                .lookupEndpoint(serverURL, BitbucketServerEndpoint.class)
                .orElse(null);
        if (endpoint != null) {
            switch (endpoint.getServerVersion()) {
            case VERSION_5:
                return NATIVE_SERVER_EVENTS_v5;
            case VERSION_5_10:
                return NATIVE_SERVER_EVENTS_v6;
            case VERSION_6:
                // plugin version 2.9.1 introduced VERSION_6 setting for Bitbucket but it
                // actually applies
                // to Version 5.10+. In order to preserve backwards compatibility, rather than
                // remove
                // VERSION_6, it will use the same list as 5.10 until such time a need arises
                // for it to have its
                // own list
                return NATIVE_SERVER_EVENTS_v6;
            case VERSION_6_5:
                return NATIVE_SERVER_EVENTS_v6_5;
            case VERSION_7:
            default:
                return NATIVE_SERVER_EVENTS_v7;
            }
        }

        // Not specifically v6, use v7.
        // Better to give an error than quietly not register some events.
        return NATIVE_SERVER_EVENTS_v7;
    }

    private static String getCloudWebhookURL(String serverURL, String rootURL) {
        return UriTemplate.buildFromTemplate(rootURL)
                .template(BitbucketSCMSourcePushHookReceiver.FULL_PATH)
                .build()
                .expand();
    }

    private static String getServerWebhookURL(String serverURL, String rootURL) {
        return UriTemplate.buildFromTemplate(rootURL)
            .template(BitbucketSCMSourcePushHookReceiver.FULL_PATH)
            .query("server_url")
            .build()
            .set("server_url", serverURL)
            .expand();
    }
}
