/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc., Nikolas Falco
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
package com.cloudbees.jenkins.plugins.bitbucket.client.branch;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBranch;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

public class BitbucketCloudBranch implements BitbucketBranch {
    private static final Logger LOGGER = Logger.getLogger(BitbucketCloudBranch.class.getName());

    private final String name;
    private long dateInMillis;
    private String hash;
    private String author;
    private String message;
    private Callable<BitbucketCommit> commitClosure;
    private boolean callableInitialised;

    @JsonCreator
    public BitbucketCloudBranch(@NonNull @JsonProperty("name") String name,
                                @Nullable @JsonProperty("target") BitbucketCloudBranch.Target target,
                                @Nullable @JsonProperty("heads") List<Head> heads) { // TODO delete heads arg if possible
        this.name = name;
        if (target != null) {
            this.dateInMillis = target.date.getTime();
            this.hash = target.hash;
            this.author = target.author.getRaw();
            this.message = target.message;
        }
    }

    public BitbucketCloudBranch(@NonNull String name, String hash, long dateInMillis) {
        this.name = name;
        this.dateInMillis = dateInMillis;
        this.hash = hash;
    }

    @Override
    public String getRawNode() {
        return hash;
    }

    public void setDateMillis(long dateInMillis) {
        if (dateInMillis == 0) {
            initHeadCommitInfo();
        }
        this.dateInMillis = dateInMillis;
    }

    public void setRawNode(String hash) {
        this.hash = hash;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getDateMillis() {
        return dateInMillis;
    }

    // TODO deprecate
    public boolean isActive() {
        return true;
    }

    @Override
    public String getMessage() {
        if (message == null) {
            initHeadCommitInfo();
        }
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String getAuthor() {
        if (author == null) {
            initHeadCommitInfo();
        }
        return author;
    }

    public void setAuthor(String authorName) {
        this.author = authorName;
    }

    @Restricted(NoExternalUse.class)
    public void setCommitClosure(Callable<BitbucketCommit> commitClosure) {
        this.commitClosure = commitClosure;
    }

    private void initHeadCommitInfo() {
        if (callableInitialised || commitClosure == null) {
            return;
        }

        callableInitialised = true;
        try {
            BitbucketCommit commit = commitClosure.call();

            this.dateInMillis = commit.getDateMillis();
            this.message = commit.getMessage();
            this.author = commit.getAuthor();
        } catch (Exception e) {
            LOGGER.log(Level.FINER, "Could not determine head commit details", e);
            // fallback on default values
            this.dateInMillis = 0L;
        }
    }

    public static class Target {
        private final String hash;
        private final String message;
        private final Date date;
        private final BitbucketCloudAuthor author;

        @JsonCreator
        public Target(@NonNull @JsonProperty("hash") String hash, //
                      @NonNull @JsonProperty("message") String message, //
                      @NonNull @JsonProperty("date") Date date, //
                      @NonNull @JsonProperty("author") BitbucketCloudAuthor author) {
            this.hash = hash;
            this.message = message;
            this.author = author;
            this.date = (Date) date.clone();
        }
    }

    @Deprecated
    public static class Head {
        private final String hash;

        @JsonCreator
        public Head(@NonNull @JsonProperty("hash") String hash) {
            this.hash = hash;
        }
    }

}
