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
package com.cloudbees.jenkins.plugins.bitbucket.server.client;

import com.cloudbees.jenkins.plugins.bitbucket.JsonParser;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestEvent;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPushEvent;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.cache.BitbucketCache;
import com.cloudbees.jenkins.plugins.bitbucket.server.events.BitbucketServerPullRequestEvent;
import com.cloudbees.jenkins.plugins.bitbucket.server.events.BitbucketServerPushEvent;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BitbucketServerWebhookPayload {

    private static final Logger LOGGER = Logger.getLogger(BitbucketServerWebhookPayload.class.getName());

    @CheckForNull
    public static BitbucketPushEvent pushEventFromPayload(@NonNull String payload) {
        BitbucketPushEvent result = null;
        try {
            LOGGER.log(Level.FINE, "Received pushEventFromPayload");
            result =  JsonParser.toJava(payload, BitbucketServerPushEvent.class);
            BitbucketCache.getInstance().invalidate(result.getRepository());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Can not read hook payload", e);
        }
        return result;
    }

    @CheckForNull
    public static BitbucketPullRequestEvent pullRequestEventFromPayload(@NonNull String payload) {
        BitbucketPullRequestEvent result = null;
        try {
            LOGGER.log(Level.FINE, "Received pullRequestEventFromPayload");
            result =  JsonParser.toJava(payload, BitbucketServerPullRequestEvent.class);
            BitbucketCache.getInstance().invalidate(result.getRepository());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Can not read hook payload", e);
        }
        return result;
    }
}
