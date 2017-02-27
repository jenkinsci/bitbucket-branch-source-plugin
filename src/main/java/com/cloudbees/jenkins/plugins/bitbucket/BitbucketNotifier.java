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
package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.io.IOException;

/**
 * Abstract Bitbucket commit notifier implementation.
 */
public abstract class BitbucketNotifier {

    /**
     * Notify bitbucket about a new build status on a concrete commit.
     *
     * @param repoOwner repository owner name (username)
     * @param repoName repository name
     * @param hash commit hash
     * @param content notification content
     * @throws IOException if there was a communictaion error during notification.
     * @throws InterruptedException if interrupted during notification.
     */
    public abstract void notify(@CheckForNull String repoOwner, @CheckForNull String repoName, String hash, String content)
            throws IOException, InterruptedException;

    /**
     * Notify bitbucket through the build status API.
     *
     * @param status the status object to serialize
     * @throws IOException if there was a communictaion error during notification.
     * @throws InterruptedException if interrupted during notification.
     */
    public abstract void buildStatus(BitbucketBuildStatus status) throws IOException, InterruptedException;

    /**
     * Convenience method that calls {@link #notify(String, String, String, String)} without owner
     * and repository name.
     *
     * @param hash commit hash
     * @param content notification content 
     * @throws IOException if there was a communictaion error during notification.
     * @throws InterruptedException if interrupted during notification.
     */
    public void notify(String hash, String content) throws IOException, InterruptedException {
        notify(null, null, hash, content);
    }

}
