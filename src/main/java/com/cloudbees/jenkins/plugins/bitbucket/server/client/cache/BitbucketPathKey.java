package com.cloudbees.jenkins.plugins.bitbucket.server.client.cache;

import java.util.Objects;

public class BitbucketPathKey {
    private BitbucketApiKey apiKey;
    private String hash;
    private String path;

    public BitbucketPathKey(BitbucketApiKey apiKey, String hash, String path) {
        this.apiKey = apiKey;
        this.hash = hash;
        this.path = path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BitbucketPathKey)) return false;
        BitbucketPathKey that = (BitbucketPathKey) o;
        return Objects.equals(apiKey, that.apiKey) &&
                Objects.equals(hash, that.hash) &&
                Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {

        return Objects.hash(apiKey, hash, path);
    }

    public BitbucketApiKey getApiKey() {
        return apiKey;
    }
}
