package com.cloudbees.jenkins.plugins.bitbucket.api;

import java.util.List;
import java.util.Map;

/**
 * Represents a Bitbucket mirror descriptor.
 */
public class BitbucketMirroredRepositoryDescriptor {

    private BitbucketMirrorServer mirrorServer;

    private Map<String, List<BitbucketHref>> links;

    public BitbucketMirrorServer getMirrorServer() {
        return mirrorServer;
    }

    public void setMirrorServer(BitbucketMirrorServer mirrorServer) {
        this.mirrorServer = mirrorServer;
    }

    public Map<String, List<BitbucketHref>> getLinks() {
        return links;
    }

    public void setLinks(Map<String, List<BitbucketHref>> links) {
        this.links = links;
    }
}
