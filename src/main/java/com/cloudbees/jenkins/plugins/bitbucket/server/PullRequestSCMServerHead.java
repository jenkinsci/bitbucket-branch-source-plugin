package com.cloudbees.jenkins.plugins.bitbucket.server;

import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryType;

public class PullRequestSCMServerHead extends PullRequestSCMHead {

    private static final long serialVersionUID = 1L;

    public PullRequestSCMServerHead(String repoOwner, String repository, BitbucketRepositoryType repositoryType,
                                    String branchName, BitbucketPullRequest pr) {
        super(repoOwner, repository, repositoryType, branchName, pr);
    }

    public String getRefSpec() {
        return "+refs/pull-requests/" + this.getId() + "/from:" + this.getBranchName();
    }

}
