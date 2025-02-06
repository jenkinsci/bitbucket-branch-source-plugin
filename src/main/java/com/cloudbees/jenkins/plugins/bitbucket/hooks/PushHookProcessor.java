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
package com.cloudbees.jenkins.plugins.bitbucket.hooks;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPushEvent;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudWebhookPayload;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerWebhookPayload;
import hudson.RestrictedSince;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMHeadEvent;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
@RestrictedSince("933.3.0")
public class PushHookProcessor extends HookProcessor {

    private static final Logger LOGGER = Logger.getLogger(PushHookProcessor.class.getName());

    @Override
    public void process(HookEventType hookEvent, String payload, BitbucketType instanceType, String origin) {
        if (payload != null) {
            BitbucketPushEvent push;
            if (instanceType == BitbucketType.SERVER) {
                // plugin webhook case
                push = BitbucketServerWebhookPayload.pushEventFromPayload(payload);
            } else {
                push = BitbucketCloudWebhookPayload.pushEventFromPayload(payload);
            }
            if (push != null) {
                if (push.getChanges().isEmpty()) {
                    final String owner = push.getRepository().getOwnerName();
                    final String repository = push.getRepository().getRepositoryName();
                    if (instanceType == BitbucketType.CLOUD || SCAN_ON_EMPTY_CHANGES) {
                        LOGGER.log(Level.INFO, "Received push hook with empty changes from Bitbucket. Processing indexing on {0}/{1}. " +
                                "You may skip this scan by adding the system property -D{2}=false on startup.",
                            new Object[]{owner, repository, SCAN_ON_EMPTY_CHANGES_PROPERTY_NAME});
                        scmSourceReIndex(owner, repository, null);
                    } else {
                        LOGGER.log(Level.INFO, "Received push hook with empty changes from Bitbucket for {0}/{1}. Skipping.",
                            new Object[]{owner, repository});
                    }
                } else {
                    SCMHeadEvent.Type type = null;
                    for (BitbucketPushEvent.Change change : push.getChanges()) {
                        if ((type == null || type == SCMEvent.Type.CREATED) && change.isCreated()) {
                            type = SCMEvent.Type.CREATED;
                        } else if ((type == null || type == SCMEvent.Type.REMOVED) && change.isClosed()) {
                            type = SCMEvent.Type.REMOVED;
                        } else {
                            type = SCMEvent.Type.UPDATED;
                        }
                    }
                    notifyEvent(new PushEvent(type, push, origin), BitbucketSCMSource.getEventDelaySeconds());
                }
            }
        }
    }

}
