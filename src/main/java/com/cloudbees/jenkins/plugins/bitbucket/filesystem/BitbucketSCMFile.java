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
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import jenkins.scm.api.SCMFile;

public class BitbucketSCMFile extends SCMFile {

    private final BitbucketApi api;
    private  String ref;
    private final String hash;
    private boolean resolved;

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public BitbucketSCMFile(BitbucketApi api, String ref, String hash) {
        this.api = api;
        this.ref = ref;
        this.hash = hash;
        this.resolved = false;
    }

    public BitbucketSCMFile(BitbucketSCMFile parent, String name, @CheckForNull Type type, String hash) {
        super(parent, name);
        this.api = parent.api;
        this.ref = parent.ref;
        this.hash = hash;
        if (type != null) {
            type(type);
        }
        this.resolved = type != null;
    }

    public String getHash() {
        return hash;
    }

    @Override
    @NonNull
    public Iterable<SCMFile> children() throws IOException, InterruptedException {
        if (this.isDirectory()) {
            return api.getDirectoryContent(this);
        } else {
            // respect the interface javadoc
            return Collections.emptyList();
        }
    }

    @Override
    @NonNull
    public InputStream content() throws IOException, InterruptedException {
        if (this.isFile()) {
            return api.getFileContent(this);
        } else {
            throw new IOException("Cannot get raw content from a directory");
        }
    }

    @Override
    public long lastModified() throws IOException, InterruptedException {
        return 0L;
    }

    @Override
    @NonNull
    protected SCMFile newChild(String name, boolean assumeIsDirectory) {
        return new BitbucketSCMFile(this, name, null, hash);
    }

    @Override
    @NonNull
    protected Type type() throws IOException, InterruptedException {
        if (!resolved) {
            try {
                SCMFile metadata = api.getFile(this);
                type(metadata.getType());
            } catch(IOException e) {
                type(Type.NONEXISTENT);
            }
            resolved = true;
        }
        return this.getType();
    }

}
