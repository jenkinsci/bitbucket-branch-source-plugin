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
package com.cloudbees.jenkins.plugins.bitbucket.server.client.branch;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import org.codehaus.jackson.annotate.JsonProperty;

public class BitbucketServerCommit implements BitbucketCommit {

    private String message;

    private String date;

    private String hash;

    @JsonProperty("authorTimestamp")
    private long dateMillis;

    public BitbucketServerCommit() {
    }

    public BitbucketServerCommit(String message, String date, String hash, long dateMillis) {
        this.message = message;
        this.date = date;
        this.hash = hash;
        this.dateMillis = dateMillis;
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

}
