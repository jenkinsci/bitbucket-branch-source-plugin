/*
 * The MIT License
 *
 * Copyright (c) 2016-2017, CloudBees, Inc.
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

package com.cloudbees.jenkins.plugins.bitbucket.filesystem;

import com.cloudbees.jenkins.plugins.bitbucket.BranchSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.JsonParser;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMHead;
import java.util.Map;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.InputStream;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;

public class BitbucketSCMFile  extends SCMFile {

    private final BitbucketApi api;
    private SCMHead head;
    private final String hash;

    public String getRef() {
        if (head instanceof BranchSCMHead) {
            return head.getName();
        }
        if (head instanceof PullRequestSCMHead) {
            // working on a pull request - can be either "HEAD" or "MERGE"
            PullRequestSCMHead pr = (PullRequestSCMHead) head;
            if (pr.getRepository() == null) { // not clear when this happens
                return null;
            }

            // else build the bitbucket API compatible ref spec:
            if (pr.getCheckoutStrategy() == ChangeRequestCheckoutStrategy.HEAD) {
                return "pull-requests/" + pr.getId() + "/from";
            } else if (pr.getCheckoutStrategy() == ChangeRequestCheckoutStrategy.MERGE) {
                return "pull-requests/" + pr.getId() + "/merge";
            }
        }
        return null;

    }

    @Deprecated
    public BitbucketSCMFile(BitbucketSCMFileSystem bitBucketSCMFileSystem,
                            BitbucketApi api,
                            SCMHead head) {
        this(bitBucketSCMFileSystem, api, head, null);
    }

    public BitbucketSCMFile(BitbucketSCMFileSystem bitBucketSCMFileSystem,
                            BitbucketApi api,
                            SCMHead head, String hash) {
        super();
        type(Type.DIRECTORY);
        this.api = api;
        this.head = head;
        this.hash = hash;
    }

    @Deprecated
    public BitbucketSCMFile(@NonNull BitbucketSCMFile parent, String name, Type type) {
        this(parent, name, type, null);
    }

    public BitbucketSCMFile(@NonNull BitbucketSCMFile parent, String name, Type type, String hash) {
        super(parent, name);
        this.api = parent.api;
        this.head = parent.head;
        this.hash = hash;
        type(type);
    }

    public String getHash() {
        return hash;
    }

    @Override
    @NonNull
    public Iterable<SCMFile> children() throws IOException,
            InterruptedException {
        if (this.isDirectory()) {
            return api.getDirectoryContent(this);
        } else {
            throw new IOException("Cannot get children from a regular file");
        }
    }

    @Override
    @NonNull
    public InputStream content() throws IOException, InterruptedException {
        if (this.isDirectory()) {
            throw new IOException("Cannot get raw content from a directory");
        }
        try {
            return api.getFileContent(this);
        } catch (IOException e)
        {
            // TODO: Disable light-weight fallback to full checkout on merge conflicts
            throw e;
        }
    }

    @Override
    public long lastModified() throws IOException, InterruptedException {
        // TODO: Return valid value when Tag support is implemented
        return 0;
    }

    @Override
    @NonNull
    protected SCMFile newChild(String name, boolean assumeIsDirectory) {
        return new BitbucketSCMFile(this, name, assumeIsDirectory?Type.DIRECTORY:Type.REGULAR_FILE, hash);
    }

    @Override
    @NonNull
    protected Type type() throws IOException, InterruptedException {
        return this.getType();
    }

}
