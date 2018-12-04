package com.cloudbees.jenkins.plugins.bitbucket.server.client.pullrequest;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BitbucketServerPullRequestCanMerge {
    private boolean canMerge;

    public boolean isCanMerge() {
        return canMerge;
    }

    @JsonProperty
    public void setCanMerge(boolean canMerge) {
        this.canMerge = canMerge;
    }

}
