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
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

/**
 * Contains the webhook configuration
 */
public class WebhookConfiguration {

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
    private static final String description = "Jenkins hook";

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

        final String signatureSecret = getSecret(owner.getServerUrl());

        if (hook instanceof BitbucketCloudHook cloudHook) {
            if (!hook.getEvents().containsAll(CLOUD_EVENTS)) {
                Set<String> events = new TreeSet<>(hook.getEvents());
                events.addAll(CLOUD_EVENTS);
                cloudHook.setEvents(new ArrayList<>(events));
                updated = true;
            }
            if (!Objects.equal(hook.getSecret(), signatureSecret)) {
                cloudHook.setSecret(signatureSecret);
                updated = true;
            }
        } else if (hook instanceof BitbucketPluginWebhook serverHook) {
            String hookCommittersToIgnore = Util.fixEmptyAndTrim(serverHook.getCommittersToIgnore());
            String thisCommittersToIgnore = Util.fixEmptyAndTrim(committersToIgnore);
            if (!Objects.equal(thisCommittersToIgnore, hookCommittersToIgnore)) {
                serverHook.setCommittersToIgnore(thisCommittersToIgnore);
                updated = true;
            }
        } else if (hook instanceof BitbucketServerWebhook serverHook) {
            String serverURL = owner.getServerUrl();
            String url = getServerWebhookURL(serverURL, owner.getEndpointJenkinsRootURL());

            if (!url.equals(serverHook.getUrl())) {
                serverHook.setUrl(url);
                updated = true;
            }

            List<String> events = serverHook.getEvents();
            if (events == null) {
                serverHook.setEvents(getNativeServerEvents(serverURL));
                updated = true;
            } else if (!events.containsAll(getNativeServerEvents(serverURL))) {
                Set<String> newEvents = new TreeSet<>(events);
                newEvents.addAll(getNativeServerEvents(serverURL));
                serverHook.setEvents(new ArrayList<>(newEvents));
                updated = true;
            }

            if (!Objects.equal(serverHook.getSecret(), signatureSecret)) {
                serverHook.setSecret(signatureSecret);
                updated = true;
            }
        }

        return updated;
    }

    public BitbucketWebHook getHook(BitbucketSCMSource owner) {
        final String serverUrl = owner.getServerUrl();
        final String rootUrl = owner.getEndpointJenkinsRootURL();
        final String signatureSecret = getSecret(owner.getServerUrl());

        if (BitbucketApiUtils.isCloud(serverUrl)) {
            BitbucketCloudHook hook = new BitbucketCloudHook();
            hook.setEvents(CLOUD_EVENTS);
            hook.setActive(true);
            hook.setDescription(description);
            hook.setUrl(rootUrl + BitbucketSCMSourcePushHookReceiver.FULL_PATH);
            hook.setSecret(signatureSecret);
            return hook;
        }

        switch (BitbucketServerEndpoint.findWebhookImplementation(serverUrl)) {
            case NATIVE: {
                BitbucketServerWebhook hook = new BitbucketServerWebhook();
                hook.setActive(true);
                hook.setDescription(description);
                hook.setEvents(getNativeServerEvents(serverUrl));
                hook.setUrl(getServerWebhookURL(serverUrl, rootUrl));
                hook.setSecret(signatureSecret);
                return hook;
            }

            case PLUGIN:
            default: {
                BitbucketPluginWebhook hook = new BitbucketPluginWebhook();
                hook.setActive(true);
                hook.setDescription(description);
                hook.setUrl(getServerWebhookURL(serverUrl, rootUrl));
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

    private static List<String> getNativeServerEvents(String serverUrl) {
        BitbucketServerEndpoint endpoint = BitbucketEndpointProvider
                .lookupEndpoint(serverUrl, BitbucketServerEndpoint.class)
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

    private static String getServerWebhookURL(String serverURL, String rootURL) {
        return UriTemplate.buildFromTemplate(rootURL)
            .template(BitbucketSCMSourcePushHookReceiver.FULL_PATH)
            .query("server_url")
            .build()
            .set("server_url", serverURL)
            .expand();
    }
}
