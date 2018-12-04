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
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.text.MessageFormat;
import java.util.Date;

public class BitbucketServerCommit implements BitbucketCommit {
    private static final String GIT_COMMIT_AUTHOR = "{0} <{1}>";

    private String message;

    private String date;

    private String hash;

    private long dateMillis;

    private String author;

    @JsonCreator
    public BitbucketServerCommit(@NonNull @JsonProperty("message") String message, //
                                 @NonNull @JsonProperty("id") String hash, //
                                 @NonNull @JsonProperty("authorTimestamp") long dateMillis, //
                                 @Nullable @JsonProperty("author") BitbucketServerAuthor author) {
        // date it is not in the payload
        this(message, hash, dateMillis, author != null ? MessageFormat.format(GIT_COMMIT_AUTHOR, author.getName(), author.getEmail()) : null);
    }

    public BitbucketServerCommit(String message, String hash, long dateMillis, String author) {
        this.message = message;
        this.hash = hash;
        this.dateMillis = dateMillis;
        this.date = new StdDateFormat().format(new Date(dateMillis));
        this.author = author;
    }

    public BitbucketServerCommit(String hash) {
        this.hash = hash;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String getDate() {
        return date;
    }

    @Override
    public String getHash() {
        return hash;
    }

    @Override
    public long getDateMillis() {
        return dateMillis;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public void setDateMillis(long dateMillis) {
        this.dateMillis = dateMillis;
    }

    @Override
    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

}
