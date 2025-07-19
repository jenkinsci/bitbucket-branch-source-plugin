/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.cloud;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestEvent;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudWebhookPayload;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.HookEventType;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.AbstractWebhookProcessor;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.RestrictedSince;
import java.util.List;
import java.util.Map;
import jenkins.scm.api.SCMEvent;
import org.apache.commons.collections4.MultiValuedMap;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Extension
@Restricted(NoExternalUse.class)
@RestrictedSince("933.3.0")
public class CloudPullRequestWebhookProcessor extends AbstractWebhookProcessor {

    private static final List<String> supportedEvents = List.of(
            HookEventType.PULL_REQUEST_CREATED.getKey(), // needed to create job
            HookEventType.PULL_REQUEST_DECLINED.getKey(), // needed to remove job
            HookEventType.PULL_REQUEST_MERGED.getKey(), // needed to remove job
            HookEventType.PULL_REQUEST_UPDATED.getKey()); // needed to update git content and trigger build job

    @Override
    public boolean canHandle(@NonNull Map<String, String> headers, @NonNull MultiValuedMap<String, String> parameters) {
        return headers.containsKey(EVENT_TYPE_HEADER)
                && headers.containsKey(REQUEST_ID_CLOUD_HEADER)
                && supportedEvents.contains(headers.get(EVENT_TYPE_HEADER))
                && !parameters.containsKey(SERVER_URL_PARAMETER);
    }

    @NonNull
    @Override
    public String getServerURL(@NonNull Map<String, String> headers, @NonNull MultiValuedMap<String, String> parameters) {
        return BitbucketCloudEndpoint.SERVER_URL;
    }

    @Override
    public void process(@NonNull String hookEventType, @NonNull String payload, @NonNull Map<String, Object> context, @NonNull BitbucketEndpoint endpoint) {
        HookEventType hookEvent = HookEventType.fromString(hookEventType);
        BitbucketPullRequestEvent pull = BitbucketCloudWebhookPayload.pullRequestEventFromPayload(payload);
        if (pull != null) {
            SCMEvent.Type eventType;
            switch (hookEvent) {
                case PULL_REQUEST_CREATED:
                    eventType = SCMEvent.Type.CREATED;
                    break;
                case PULL_REQUEST_DECLINED,
                     PULL_REQUEST_MERGED:
                    eventType = SCMEvent.Type.REMOVED;
                    break;
                default:
                    eventType = SCMEvent.Type.UPDATED;
                    break;
            }
            // assume updated as a catch-all type
            notifyEvent(new CloudPREvent(eventType, pull, getOrigin(context), hookEvent), BitbucketSCMSource.getEventDelaySeconds());
        }
    }
}
