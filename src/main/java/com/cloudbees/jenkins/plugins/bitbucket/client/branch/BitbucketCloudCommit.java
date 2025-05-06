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
package com.cloudbees.jenkins.plugins.bitbucket.client.branch;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.DateUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class BitbucketCloudCommit implements BitbucketCommit {

    public static class Parent {
        private final String hash;

        @JsonCreator
        public Parent(@NonNull @JsonProperty("hash") String hash) {
            this.hash = hash;
        }

        public String getHash() {
            return hash;
        }
    }

    private String message;
    private String hash;
    private String author;
    private String committer;
    private Date committerDate;
    private List<String> parents;

    @JsonCreator
    public BitbucketCloudCommit(@Nullable @JsonProperty("message") String message,
                                @Nullable @JsonProperty("date") String date,
                                @NonNull @JsonProperty("hash") String hash,
                                @Nullable @JsonProperty("author") BitbucketCloudAuthor author,
                                @Nullable @JsonProperty("committer") BitbucketCloudAuthor committer,
                                @Nullable @JsonProperty("parents") List<Parent> parents) {
        this.message = message;
        if (date != null) {
            this.committerDate = DateUtils.parseISODate(date);
        }
        this.hash = hash;
        if (author != null) {
            this.author = author.getRaw();
        }
        if (committer != null) {
            this.committer = committer.getRaw();
        }
        if (parents != null) {
            this.parents = parents.stream().map(Parent::getHash).toList();
        }
    }

    @Override
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    @Deprecated(since = "936.1.0", forRemoval = true)
    @Override
    public String getDate() {
        return DateUtils.formatToISO(committerDate);
    }

    @Deprecated(since = "936.1.0", forRemoval = true)
    @Override
    public long getDateMillis() {
        return committerDate != null ? committerDate.getTime() : 0;
    }

    @Override
    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    @Override
    public Date getAuthorDate() {
        return getCommitterDate(); // is it better than null?
    }

    @Override
    public String getCommitter() {
        return committer;
    }

    public void setCommitter(String committer) {
        this.committer = committer;
    }

    @Override
    public Date getCommitterDate() {
        return committerDate;
    }

    public void setCommitterDate(Date committerDate) {
        this.committerDate = committerDate;
    }

    @Override
    public Collection<String> getParents() {
        return Collections.unmodifiableCollection(parents);
    }
}
