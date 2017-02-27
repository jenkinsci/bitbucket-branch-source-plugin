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
package com.cloudbees.jenkins.plugins.bitbucket.server.client.repository;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketTeam;
import org.codehaus.jackson.map.annotate.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketServerProject implements BitbucketTeam {

    @JsonProperty("key")
    private String name;

    @JsonProperty("name")
    private String displayName;

    @JsonProperty("links")
    @JsonDeserialize(keyAs = String.class, contentAs = BitbucketHref[].class)
    private Map<String,BitbucketHref[]> links;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public Map<String, BitbucketHref> getLinks() {
        Map<String,BitbucketHref> result = new LinkedHashMap<>(links.size());
        for (Map.Entry<String,BitbucketHref[]> entry: links.entrySet()) {
            if (entry.getValue().length == 0) {
                continue;
            }
            result.put(entry.getKey(), entry.getValue()[0]);
        }
        return result;
    }

    // Do not call this setLinks or Jackson will have issues
    public void links(Map<String, BitbucketHref> links) {
        Map<String, BitbucketHref[]> result = new LinkedHashMap<>();
        for (Map.Entry<String,BitbucketHref> entry: links.entrySet()) {
            BitbucketHref[] value = entry.getValue() == null ? new BitbucketHref[0] : new BitbucketHref[]{entry.getValue()};
            result.put(entry.getKey(), value);
        }
        this.links = result;
    }
}
