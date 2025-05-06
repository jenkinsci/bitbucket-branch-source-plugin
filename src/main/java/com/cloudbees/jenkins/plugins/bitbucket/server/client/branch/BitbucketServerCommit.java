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
package com.cloudbees.jenkins.plugins.bitbucket.server.client.branch;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.DateUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class BitbucketServerCommit implements BitbucketCommit {
    private static final String GIT_COMMIT_AUTHOR = "{0} <{1}>";

    public static class Parent {
        private final String hash;

        @JsonCreator
        public Parent(@NonNull @JsonProperty("id") String hash) {
            this.hash = hash;
        }

        public String getHash() {
            return hash;
        }
    }

    private String message;
    private String hash;
    private String author;
    private Date authorDate;
    private String committer;
    private Date committerDate;
    private List<String> parents;

    @JsonCreator
    public BitbucketServerCommit(@NonNull @JsonProperty("message") String message,
                                 @NonNull @JsonProperty("id") String hash,
                                 @Nullable @JsonProperty("committer") BitbucketServerAuthor committer,
                                 @NonNull @JsonProperty("committerTimestamp") long committerDateMillis,
                                 @Nullable @JsonProperty("author") BitbucketServerAuthor author,
                                 @NonNull @JsonProperty("authorTimestamp") long authorDateMillis,
                                 @Nullable @JsonProperty("parents") List<Parent> parents) {
        // date it is not in the payload
        this(message, hash, committerDateMillis, author != null ? MessageFormat.format(GIT_COMMIT_AUTHOR, author.getName(), author.getEmail()) : null);
        if (committer != null) {
            this.committer = MessageFormat.format(GIT_COMMIT_AUTHOR, committer.getName(), committer.getEmail());
        }
        if (authorDateMillis > 0) {
            this.authorDate = new Date(authorDateMillis);
        }
        if (parents != null) {
            this.parents = parents.stream().map(Parent::getHash).toList();
        }
    }

    public BitbucketServerCommit(String message, String hash, long dateMillis, String author) {
        this.message = message;
        this.hash = hash;
        this.committerDate = new Date(dateMillis);
        this.author = author;
    }

    public BitbucketServerCommit(String hash) {
        this.hash = hash;
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
        return committerDate != null ? committerDate.getTime(): 0;
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
        return authorDate;
    }

    public void setAuthorDate(Date authorDate) {
        this.authorDate = authorDate;
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

    public void setCommitter(Date committerDate) {
        this.committerDate = committerDate;
    }

    @Override
    public Collection<String> getParents() {
        return Collections.unmodifiableCollection(parents);
    }
}
