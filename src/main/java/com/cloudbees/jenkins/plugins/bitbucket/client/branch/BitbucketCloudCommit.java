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

import com.cloudbees.jenkins.plugins.bitbucket.JsonParser.BitbucketDateFormat;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.text.ParseException;

public class BitbucketCloudCommit implements BitbucketCommit {

    private String message;
    private String date;
    private transient long dateInMillis;
    private String hash;
    private String author;

    @JsonCreator
    public BitbucketCloudCommit(@Nullable @JsonProperty("message") String message,
                                @Nullable @JsonProperty("date") String date,
                                @NonNull @JsonProperty("hash") String hash,
                                @Nullable @JsonProperty("author") BitbucketCloudAuthor author) {
        this.message = message;
        this.date = date;
        this.hash = hash;
        if (author != null) {
            this.author = author.getRaw();
        }
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

    public void setMessage(String message) {
        this.message = message;
    }

    public void setDate(String date) {
        this.date = date;
        // calculated on demand
        this.dateInMillis = 0;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    @Override
    public long getDateMillis() {
        try {
            if (dateInMillis == 0 && date != null) {
                dateInMillis = new BitbucketDateFormat().parse(date).getTime();
            }
        } catch (ParseException e) {
            dateInMillis = 0;
        }
        return dateInMillis;
    }

    @Override
    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

}
