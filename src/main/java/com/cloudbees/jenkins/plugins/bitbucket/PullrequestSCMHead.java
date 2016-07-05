package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Action;

import java.util.ArrayList;
import java.util.List;

public class PullrequestSCMHead extends SCMHeadWithOwnerAndRepo {

    private static final long serialVersionUID = 1L;
    private static final String PR_BRANCH_PREFIX = "PR-";

    private final PullRequestAction metatdata;

    public PullrequestSCMHead(BitbucketPullRequest pullRequest) {
        super(pullRequest.getSource().getRepository().getOwnerName(),
                pullRequest.getSource().getRepository().getRepositoryName(),
                pullRequest.getSource().getBranch().getName());
        this.metatdata = new PullRequestAction(pullRequest);
    }

    @NonNull
    @Override
    public String getName() {
        return PR_BRANCH_PREFIX + metatdata.getId();
    }

    @NonNull
    @Override
    public List<? extends Action> getAllActions() {
        List<Action> actions = new ArrayList<>(super.getAllActions());
        actions.add(metatdata);
        return actions;
    }
}
