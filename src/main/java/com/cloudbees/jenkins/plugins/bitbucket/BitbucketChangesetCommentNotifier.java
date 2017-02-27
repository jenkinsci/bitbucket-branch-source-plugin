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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

/**
 * Bitbucket notifier implementation that sends notifications as commit comments.
 */
public class BitbucketChangesetCommentNotifier extends BitbucketNotifier {

    private BitbucketApi bitbucket;

    public BitbucketChangesetCommentNotifier(@NonNull BitbucketApi bitbucket) {
        this.bitbucket = bitbucket;
    }

    @Override
    public void notify(String repoOwner, String repoName, String hash, String content)
            throws IOException, InterruptedException {
        bitbucket.postCommitComment(hash, content);
    }

    @Override
    public void buildStatus(BitbucketBuildStatus status) throws IOException, InterruptedException {
        bitbucket.postBuildStatus(status);
    }

}
