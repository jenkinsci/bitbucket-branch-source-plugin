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
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPushEvent;
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
public class CloudPushWebhookProcessor extends AbstractWebhookProcessor {

    private static final List<String> supportedEvents = List.of(
            HookEventType.PUSH.getKey());

    @Override
    public boolean canHandle(Map<String, String> headers, MultiValuedMap<String, String> parameters) {
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
        BitbucketPushEvent push = BitbucketCloudWebhookPayload.pushEventFromPayload(payload);
        if (push != null) {
            if (push.getChanges().isEmpty()) {
                final String owner = push.getRepository().getOwnerName();
                final String repository = push.getRepository().getRepositoryName();
                scmSourceReIndex(owner, repository, null);
            } else {
                SCMEvent.Type type = null;
                for (BitbucketPushEvent.Change change : push.getChanges()) {
                    if ((type == null || type == SCMEvent.Type.CREATED) && change.isCreated()) {
                        type = SCMEvent.Type.CREATED;
                    } else if ((type == null || type == SCMEvent.Type.REMOVED) && change.isClosed()) {
                        type = SCMEvent.Type.REMOVED;
                    } else {
                        type = SCMEvent.Type.UPDATED;
                    }
                }
                notifyEvent(new CloudPushEvent(type, push, getOrigin(context)), BitbucketSCMSource.getEventDelaySeconds());
            }
        }
    }

}
