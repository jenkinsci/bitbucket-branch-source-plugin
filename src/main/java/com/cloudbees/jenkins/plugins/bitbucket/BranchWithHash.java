package com.cloudbees.jenkins.plugins.bitbucket;

public class BranchWithHash {
    private final String branch;
    private final String hash;

    public BranchWithHash(String branch, String hash) {
        this.branch = branch;
        this.hash = hash;
    }

    public String getBranch() {
        return branch;
    }

    public String getHash() {
        return hash;
    }
}
