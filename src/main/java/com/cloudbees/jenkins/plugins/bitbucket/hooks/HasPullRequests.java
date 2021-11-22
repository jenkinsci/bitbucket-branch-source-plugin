package com.cloudbees.jenkins.plugins.bitbucket.hooks;


import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;

public interface HasPullRequests {

    Iterable<BitbucketPullRequest> getPullRequests(BitbucketSCMSource src) throws InterruptedException;

}
