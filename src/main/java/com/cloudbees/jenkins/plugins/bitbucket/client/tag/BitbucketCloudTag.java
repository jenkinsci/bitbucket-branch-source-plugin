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
package com.cloudbees.jenkins.plugins.bitbucket.client.tag;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketTag;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudRepository;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

public class BitbucketCloudTag implements BitbucketTag {
    private final String name;
    private long dateInMillis;
    private String hash;

    @JsonCreator
    public BitbucketCloudTag(@Nonnull @JsonProperty("name") String name, @Nullable @JsonProperty("target") BitbucketCloudTag.Target target) {
        this.name = name;
        if(target != null) {
            this.dateInMillis = target.repo.getUpdatedOn() != null ? target.repo.getUpdatedOn().getTime() : 0;
            this.hash = target.hash;
        }
    }

    public BitbucketCloudTag(@Nonnull String name, String hash, long dateInMillis) {
        this.name = name;
        this.dateInMillis = dateInMillis;
        this.hash = hash;
    }

    public String getRawNode() {
        return hash;
    }

    public void setDateMillis(long dateInMillis) {
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

    public static class Target {
        private final String hash;
        private final BitbucketCloudRepository repo;

        @JsonCreator
        public Target(@Nonnull @JsonProperty("hash") String hash, @Nonnull @JsonProperty("repository") BitbucketCloudRepository repo) {
            this.hash = hash;
            this.repo = repo;
        }
    }
}
