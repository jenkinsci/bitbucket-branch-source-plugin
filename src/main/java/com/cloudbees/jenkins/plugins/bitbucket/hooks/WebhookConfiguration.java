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
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketRepositoryHook;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.AbstractBitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketServerEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketServerWebhook;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.NativeBitbucketServerWebhook;
import com.damnhandy.uri.template.UriTemplate;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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
            // only on v7.x and above
            HookEventType.SERVER_PULL_REQUEST_FROM_REF_UPDATED.getKey()
    ));

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
        if (hook instanceof BitbucketRepositoryHook) {
            if (!hook.getEvents().containsAll(CLOUD_EVENTS)) {
                Set<String> events = new TreeSet<>(hook.getEvents());
                events.addAll(CLOUD_EVENTS);
                BitbucketRepositoryHook repoHook = (BitbucketRepositoryHook) hook;
                repoHook.setEvents(new ArrayList<>(events));
                return true;
            }

            return false;
        }

        if (hook instanceof BitbucketServerWebhook) {
            BitbucketServerWebhook serverHook = (BitbucketServerWebhook) hook;

            // Handle null case
            String hookCommittersToIgnore = ((BitbucketServerWebhook) hook).getCommittersToIgnore();
            if (hookCommittersToIgnore == null) {
                hookCommittersToIgnore = "";
            }

            // Handle null case
            String thisCommittersToIgnore = committersToIgnore;
            if (thisCommittersToIgnore == null) {
                thisCommittersToIgnore = "";
            }

            if (!hookCommittersToIgnore.trim().equals(thisCommittersToIgnore.trim())) {
                serverHook.setCommittersToIgnore(committersToIgnore);
                return true;
            }

            return false;
        }

        if (hook instanceof NativeBitbucketServerWebhook) {
            boolean updated = false;

            NativeBitbucketServerWebhook serverHook = (NativeBitbucketServerWebhook) hook;
            String serverUrl = owner.getServerUrl();
            String url = getNativeServerWebhookUrl(serverUrl, owner.getEndpointJenkinsRootUrl());

            if (!url.equals(serverHook.getUrl())) {
                serverHook.setUrl(url);
                updated = true;
            }

            List<String> events = serverHook.getEvents();
            if (events == null) {
                serverHook.setEvents(getNativeServerEvents(serverUrl));
                updated = true;
            } else if (!events.containsAll(getNativeServerEvents(serverUrl))) {
                Set<String> newEvents = new TreeSet<>(events);
                newEvents.addAll(getNativeServerEvents(serverUrl));
                serverHook.setEvents(new ArrayList<>(newEvents));
                updated = true;
            }

            return updated;
        }

        return false;
    }

    public BitbucketWebHook getHook(BitbucketSCMSource owner) {
        final String serverUrl = owner.getServerUrl();
        final String rootUrl = owner.getEndpointJenkinsRootUrl();

        if (BitbucketCloudEndpoint.SERVER_URL.equals(serverUrl)) {
            BitbucketRepositoryHook hook = new BitbucketRepositoryHook();
            hook.setEvents(CLOUD_EVENTS);
            hook.setActive(true);
            hook.setDescription(description);
            hook.setUrl(rootUrl + BitbucketSCMSourcePushHookReceiver.FULL_PATH);
            return hook;
        }

        switch (BitbucketServerEndpoint.findWebhookImplementation(serverUrl)) {
            case NATIVE: {
                NativeBitbucketServerWebhook hook = new NativeBitbucketServerWebhook();
                hook.setActive(true);
                hook.setEvents(getNativeServerEvents(serverUrl));
                hook.setDescription(description);
                hook.setUrl(getNativeServerWebhookUrl(serverUrl, rootUrl));
                return hook;
            }

            case PLUGIN:
            default: {
                BitbucketServerWebhook hook = new BitbucketServerWebhook();
                hook.setActive(true);
                hook.setDescription(description);
                hook.setUrl(rootUrl + BitbucketSCMSourcePushHookReceiver.FULL_PATH);
                hook.setCommittersToIgnore(committersToIgnore);
                return hook;
            }
        }
    }

    private static List<String> getNativeServerEvents(String serverUrl) {
        AbstractBitbucketEndpoint endpoint = BitbucketEndpointConfiguration.get().findEndpoint(serverUrl);
        if (endpoint instanceof BitbucketServerEndpoint) {
            switch (((BitbucketServerEndpoint) endpoint).getServerVersion()) {
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
            case VERSION_7:
            default:
                return NATIVE_SERVER_EVENTS_v7;
            }
        }

        // Not specifically v6, use v7.
        // Better to give an error than quietly not register some events.
        return NATIVE_SERVER_EVENTS_v7;
    }

    private static String getNativeServerWebhookUrl(String serverUrl, String rootUrl) {
        return UriTemplate.buildFromTemplate(rootUrl)
            .template(BitbucketSCMSourcePushHookReceiver.FULL_PATH)
            .query("server_url")
            .build()
            .set("server_url", serverUrl)
            .expand();
    }
}
