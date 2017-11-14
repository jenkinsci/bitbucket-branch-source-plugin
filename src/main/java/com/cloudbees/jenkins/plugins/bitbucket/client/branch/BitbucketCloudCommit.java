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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class BitbucketCloudCommit implements BitbucketCommit {

    private String message;

    private String date;

    private String hash;

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
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    @Override
    public long getDateMillis() {
        // 2013-10-21T07:21:51+00:00
        final SimpleDateFormat dateParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        dateParser.setTimeZone(TimeZone.getTimeZone("GMT"));
        try {
            return dateParser.parse(date).getTime();
        } catch (ParseException e) {
            return 0;
        }
    }

}
