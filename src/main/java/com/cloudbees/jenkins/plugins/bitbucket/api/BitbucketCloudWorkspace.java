package com.cloudbees.jenkins.plugins.bitbucket.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;
import java.util.Map;

public class BitbucketCloudWorkspace implements BitbucketWorkspace {
    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("name")
    private String name;

    @JsonProperty("slug")
    private String slug;

    @JsonProperty("is_private")
    private boolean isPrivate;

    @JsonProperty("links")
    @JsonDeserialize(keyAs = String.class, contentUsing = BitbucketHref.Deserializer.class)
    private Map<String, List<BitbucketHref>> links;

    @Override
    public String getUuid() {
        return uuid;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return name;
    }

    @Override
    public String getSlug() {
        return slug;
    }

    @Override
    public boolean isPrivate() {
        return isPrivate;
    }

    @Override
    public Map<String, List<BitbucketHref>> getLinks() {
        return links;
    }

    @Override
    public String getLink(String name) {
        if (links == null) {
            return null;
        }
        List<BitbucketHref> hrefs = links.get(name);
        if (hrefs == null || hrefs.isEmpty()) {
            return null;
        }
        BitbucketHref href = hrefs.get(0);
        return href == null ? null : href.getHref();
    }
}
