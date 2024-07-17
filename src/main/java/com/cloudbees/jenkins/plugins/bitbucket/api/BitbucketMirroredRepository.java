package com.cloudbees.jenkins.plugins.bitbucket.api;

import java.util.List;
import java.util.Map;

public class BitbucketMirroredRepository {

    private boolean available;

    private Map<String, List<BitbucketHref>> links;

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public Map<String, List<BitbucketHref>> getLinks() {
        return links;
    }

    public void setLinks(Map<String, List<BitbucketHref>> links) {
        this.links = links;
    }

}
