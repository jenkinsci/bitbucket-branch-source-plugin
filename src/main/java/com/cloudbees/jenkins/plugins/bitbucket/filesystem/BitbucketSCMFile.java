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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.InputStream;
import jenkins.scm.api.SCMFile;

public class BitbucketSCMFile  extends SCMFile {

    private final BitbucketApi api;
    private  String ref;
    private final String hash;

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    @Deprecated
    public BitbucketSCMFile(BitbucketSCMFileSystem bitBucketSCMFileSystem,
                            BitbucketApi api,
                            String ref) {
        this(bitBucketSCMFileSystem, api, ref, null);
    }

    public BitbucketSCMFile(BitbucketSCMFileSystem bitBucketSCMFileSystem,
                            BitbucketApi api,
                            String ref, String hash) {
        super();
        type(Type.DIRECTORY);
        this.api = api;
        this.ref = ref;
        this.hash = hash;
    }

    @Deprecated
    public BitbucketSCMFile(@NonNull BitbucketSCMFile parent, String name, Type type) {
        this(parent, name, type, null);
    }

    public BitbucketSCMFile(@NonNull BitbucketSCMFile parent, String name, Type type, String hash) {
        super(parent, name);
        this.api = parent.api;
        this.ref = parent.ref;
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
        } else {
            return api.getFileContent(this);
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
