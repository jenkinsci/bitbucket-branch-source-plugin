package com.cloudbees.jenkins.plugins.bitbucket.server.client.branch;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BitbucketServerBuildStatus extends BitbucketBuildStatus {

    /**
     * Copy constructor.
     *
     * @param other from copy to.
     */
    public BitbucketServerBuildStatus(BitbucketBuildStatus other) {
        super(other);
    }

    @JsonProperty("ref")
    @Override
    public String getRefname() {
        return super.getRefname();
    }

    @JsonProperty("duration")
    @Override
    public long getBuildDuration() {
        return super.getBuildDuration();
    }
}
