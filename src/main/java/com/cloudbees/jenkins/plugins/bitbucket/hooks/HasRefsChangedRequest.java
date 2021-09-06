package com.cloudbees.jenkins.plugins.bitbucket.hooks;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBranch;

public interface HasRefsChangedRequest {
    Iterable<BitbucketBranch> getBranches(BitbucketSCMSource src) throws InterruptedException;

    Iterable<BitbucketBranch> getTags(BitbucketSCMSource src) throws InterruptedException;
}
