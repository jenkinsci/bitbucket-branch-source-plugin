package com.cloudbees.jenkins.plugins.bitbucket;

import java.net.MalformedURLException;
import java.net.URL;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.actions.ChangeRequestAction;

public class PullRequestAction extends ChangeRequestAction {
    private final String number;
    private URL url;
    private final String title;
    private final String userLogin;

    public PullRequestAction(BitbucketPullRequest pr) {
        number = pr.getId();
        try {
            url = new URL(pr.getLink());
        } catch (MalformedURLException e) {
            url = null;
        }
        title = pr.getTitle();
        userLogin = pr.getAuthorLogin();
    }

    @Override
    @NonNull
    public String getId() {
        return number;
    }

    @Override
    public URL getURL() {
        return url;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getAuthor() {
        return userLogin;
    }

    private static final long serialVersionUID = 1L;

}
