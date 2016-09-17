package com.cloudbees.jenkins.plugins.bitbucket.api;

/**
 * Represents a push event coming from Bitbucket (webhooks).
 */
public interface BitbucketPushEvent {
    /**
     * @return the destination repository that the push points to.
     */
    BitbucketRepository getRepository();
}
