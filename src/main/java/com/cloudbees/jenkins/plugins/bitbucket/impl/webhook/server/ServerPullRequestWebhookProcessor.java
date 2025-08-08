/*
 * The MIT License
 *
 * Copyright (c) 2016-2018, Yieldlab AG
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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.server;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.HookEventType;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.JsonParser;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.AbstractWebhookProcessor;
import com.cloudbees.jenkins.plugins.bitbucket.server.events.NativeServerPullRequestEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.RestrictedSince;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.scm.api.SCMEvent;
import org.apache.commons.collections4.MultiValuedMap;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Extension
@Restricted(NoExternalUse.class)
@RestrictedSince("933.3.0")
public class ServerPullRequestWebhookProcessor extends AbstractWebhookProcessor {

    private static final Logger LOGGER = Logger.getLogger(ServerPullRequestWebhookProcessor.class.getName());

    private static final List<String> supportedEvents = List.of(
            HookEventType.SERVER_PULL_REQUEST_OPENED.getKey(),
            HookEventType.SERVER_PULL_REQUEST_MERGED.getKey(),
            HookEventType.SERVER_PULL_REQUEST_DECLINED.getKey(),
            HookEventType.SERVER_PULL_REQUEST_DELETED.getKey(),
            HookEventType.SERVER_PULL_REQUEST_MODIFIED.getKey(),
            HookEventType.SERVER_PULL_REQUEST_FROM_REF_UPDATED.getKey());

    @Override
    public boolean canHandle(@NonNull Map<String, String> headers, @NonNull MultiValuedMap<String, String> parameters) {
        return headers.containsKey(EVENT_TYPE_HEADER)
                && headers.containsKey(REQUEST_ID_SERVER_HEADER)
                && supportedEvents.contains(headers.get(EVENT_TYPE_HEADER))
                && parameters.containsKey(SERVER_URL_PARAMETER);
    }

    @Override
    public void process(@NonNull String hookEventType, @NonNull String payload, @NonNull Map<String, Object> context, @NonNull BitbucketEndpoint endpoint) {
        final NativeServerPullRequestEvent pullRequestEvent;
        try {
            pullRequestEvent = JsonParser.toJava(payload, NativeServerPullRequestEvent.class);
        } catch (final IOException e) {
            LOGGER.log(Level.SEVERE, "Can not read hook payload", e);
            return;
        }

        HookEventType hookEvent = HookEventType.fromString(hookEventType);
        final SCMEvent.Type eventType;
        switch (hookEvent) {
            case SERVER_PULL_REQUEST_OPENED:
                eventType = SCMEvent.Type.CREATED;
                break;
            case SERVER_PULL_REQUEST_MERGED,
                 SERVER_PULL_REQUEST_DECLINED,
                 SERVER_PULL_REQUEST_DELETED:
                eventType = SCMEvent.Type.REMOVED;
                break;
            case SERVER_PULL_REQUEST_MODIFIED,
                 SERVER_PULL_REQUEST_FROM_REF_UPDATED:
                eventType = SCMEvent.Type.UPDATED;
                break;
            default:
                LOGGER.log(Level.INFO, "Unknown hook event {0} received from Bitbucket Server", hookEvent);
                return;
        }

        notifyEvent(new ServerHeadEvent(endpoint.getServerURL(), eventType, pullRequestEvent, getOrigin(context)), BitbucketSCMSource.getEventDelaySeconds());
    }
}
