package com.cloudbees.jenkins.plugins.bitbucket.api;

/**
 * Represents a Bitbucket mirror.
 */
public class BitbucketMirrorServer {

    private String id;

    private String name;

    private boolean enabled;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}
