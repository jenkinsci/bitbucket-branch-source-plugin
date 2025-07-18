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
package com.cloudbees.jenkins.plugins.bitbucket.api;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * Represents a webhook in Bitbucket.
 */
public interface BitbucketWebHook {

    /**
     * @return webhook description text
     */
    String getDescription();

    /**
     * @return webhook URL (not required at creation time)
     */
    String getUrl();

    /**
     * @return tru if the webhook is active (not required at creation time)
     */
    boolean isActive();

    /**
     * @return the list of events this webhook is notifying
     */
    @NonNull
    List<String> getEvents();

    /**
     * @return Bitbucket internal ID for this webhook (not required at creation time)
     */
    String getUuid();

    /**
     * Returns the secret used as the key to generate a HMAC digest value sent
     * in the X-Hub-Signature header at delivery time.
     *
     * @return a secret
     */
    @Nullable
    default String getSecret() {
        return null;
    }

}
