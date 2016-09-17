package com.cloudbees.jenkins.plugins.bitbucket.hooks;

import edu.umd.cs.findbugs.annotations.CheckForNull;

public enum BitbucketType {

    CLOUD("cloud"),

    SERVER("server");

    private String key;

    BitbucketType(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    @CheckForNull
    public static BitbucketType fromString(String key) {
        for (BitbucketType value : BitbucketType.values()) {
            if (value.getKey().equals(key)) {
                return value;
            }
        }
        return null;
    }
}
