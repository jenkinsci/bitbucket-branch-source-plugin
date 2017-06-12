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
package com.cloudbees.jenkins.plugins.bitbucket.server.client.pullrequest;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestSource;
import org.codehaus.jackson.map.annotate.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketServerPullRequest implements BitbucketPullRequest {

    private String id;

    @JsonProperty("fromRef")
    private BitbucketServerPullRequestSource source;

    @JsonProperty("toRef")
    private BitbucketServerPullRequestDestination destination;

    private String title;

    private String link;

    private String authorLogin;

    @JsonProperty
    @JsonDeserialize(keyAs = String.class, contentUsing = BitbucketHref.Deserializer.class)
    private Map<String, List<BitbucketHref>> links;

    @Override
    public BitbucketPullRequestSource getSource() {
        return source;
    }

    public void setSource(BitbucketServerPullRequestSource source) {
        this.source = source;
    }

    @Override
    public BitbucketServerPullRequestDestination getDestination() {
        return destination;
    }

    public void setDestination(BitbucketServerPullRequestDestination destination) {
        this.destination = destination;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getTitle() {
        return this.title;
    }

    // TODO: unmapped, need proper JsonProperty in the field
    @Override
    public String getLink() {
        if (link == null) {
            Map<String, BitbucketHref> links = getLinks();
            BitbucketHref self = links.get("self");
            if (self != null) {
                return self.getHref();
            }
        }
        return link;
    }

    // TODO: unmapped, need proper JsonProperty in the field
    @Override
    public String getAuthorLogin() {
        return authorLogin;
    }

    @JsonProperty
    public void setAuthor(Author author) {
        if (author != null && author.getUser() != null) {
            authorLogin = author.getUser().getDisplayName();
        } else {
            authorLogin = null;
        }
    }


    @JsonIgnore
    public Map<String, BitbucketHref> getLinks() {
        if (links == null) {
            return null;
        }
        Map<String, BitbucketHref> result = new HashMap<>();
        for (Map.Entry<String, List<BitbucketHref>> entry : this.links.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                result.put(entry.getKey(), entry.getValue().get(0));
            }
        }
        return result;
    }

    @JsonIgnore
    public void setLinks(Map<String, BitbucketHref> links) {
        if (links == null) {
            this.links = null;
        } else {
            this.links = new HashMap<>();
            for (Map.Entry<String, BitbucketHref> entry : links.entrySet()) {
                this.links.put(entry.getKey(), Collections.singletonList(entry.getValue()));
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Author {
        private User user;

        public User getUser() {
            return user;
        }

        public void setUser(User user) {
            this.user = user;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class User {
        private String name;
        private String displayName;
        private String emailAddress;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getEmailAddress() {
            return emailAddress;
        }

        public void setEmailAddress(String emailAddress) {
            this.emailAddress = emailAddress;
        }
    }


}
