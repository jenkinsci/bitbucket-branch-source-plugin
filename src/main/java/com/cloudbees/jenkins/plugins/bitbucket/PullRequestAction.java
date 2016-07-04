package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import jenkins.scm.api.actions.ChangeRequestAction;

final class PullRequestAction extends ChangeRequestAction {

    private static final long serialVersionUID = 1L;

    private final String number;
    private final String title;

    PullRequestAction(BitbucketPullRequest pr) {
        number = pr.getId();
        title = pr.getTitle();
    }

    @Override
    public String getId() {
        return number;
    }

    @Override
    public String getTitle() {
        return title;
    }
}