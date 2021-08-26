package com.cloudbees.jenkins.plugins.bitbucket.api;

public interface BitbucketWorkspace extends BitbucketTeam {
    String getUuid();

    String getSlug();

    boolean isPrivate();
}
