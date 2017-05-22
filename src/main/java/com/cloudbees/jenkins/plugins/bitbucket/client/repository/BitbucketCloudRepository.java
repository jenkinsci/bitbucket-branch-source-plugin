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
package com.cloudbees.jenkins.plugins.bitbucket.client.repository;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryOwner;
import org.codehaus.jackson.map.annotate.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketCloudRepository implements BitbucketRepository {

    private String scm;

    @JsonProperty("full_name")
    private String fullName;

    private BitbucketCloudRepositoryOwner owner;

    @JsonProperty("updated_on")
    private String updatedOn;

    // JSON mapping added in setter because the field can not be called "private"
    private Boolean priv;

    @JsonProperty
    @JsonDeserialize(keyAs = String.class, contentUsing = BitbucketHref.Deserializer.class)
    private Map<String,List<BitbucketHref>> links;

    @Override
    public String getScm() {
        return scm;
    }

    @Override
    public String getFullName() {
        return fullName;
    }

    @Override
    public BitbucketCloudRepositoryOwner getOwner() {
        return owner;
    }

    public void setScm(String scm) {
        this.scm = scm;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setOwner(BitbucketCloudRepositoryOwner owner) {
        this.owner = owner;
    }

    public void setUpdatedOn(String updatedOn) {
        this.updatedOn = updatedOn;
    }

    @Override
    public String getOwnerName() {
        if (this.fullName != null) {
            return this.fullName.split("/")[0];
        }
        return null;
    }

    @Override
    public String getRepositoryName() {
        if (this.fullName != null) {
            return this.fullName.split("/")[1];
        }
        return null;
    }

    @Override
    public boolean isPrivate() {
        return priv;
    }

    @JsonProperty("is_private")
    public void setPrivate(Boolean priv) {
        this.priv = priv;
    }

    @JsonIgnore
    public Map<String, List<BitbucketHref>> getLinks() {
        if (links == null) {
            return null;
        }
        Map<String, List<BitbucketHref>> result = new HashMap<>();
        for (Map.Entry<String, List<BitbucketHref>> entry : this.links.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        return result;
    }

    @JsonIgnore
    public void setLinks(Map<String, List<BitbucketHref>> links) {
        if (links == null) {
            this.links = null;
        } else {
            this.links = new HashMap<>();
            for (Map.Entry<String, List<BitbucketHref>> entry : links.entrySet()) {
                this.links.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
    }
}
