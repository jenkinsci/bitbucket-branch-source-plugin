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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPushEvent;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudWebhookPayload;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerWebhookPayload;

import java.util.logging.Logger;

public class PushHookProcessor extends HookProcessor {

    private static final Logger LOGGER = Logger.getLogger(PushHookProcessor.class.getName());

    @Override
    public void process(String payload, BitbucketType instanceType) {
        if (payload != null) {
            BitbucketPushEvent push;
            if (instanceType == BitbucketType.SERVER) {
                push = BitbucketServerWebhookPayload.pushEventFromPayload(payload);
            } else {
                push = BitbucketCloudWebhookPayload.pushEventFromPayload(payload);
            }
            if (push != null) {
                String owner = push.getRepository().getOwnerName();
                String repository = push.getRepository().getRepositoryName();
                String commitMessage = push.getCommitMessage();

                LOGGER.info(String.format("Received hook from Bitbucket. Processing push event on %s/%s", owner, repository));
                scmSourceReIndex(owner, repository, commitMessage);
            }
        }
    }
}
