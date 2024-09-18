package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import org.jenkinsci.plugins.gitclient.GitClient;

public class GitClientAuthenticatorExtension extends GitSCMExtension {

    private final StandardUsernameCredentials credentials;

    public GitClientAuthenticatorExtension(StandardUsernameCredentials credentials) {
        this.credentials = credentials;
    }

    @Override
    public GitClient decorate(GitSCM scm, GitClient git) throws GitException {
        if (credentials != null) {
            git.setCredentials(credentials);
        }

        return git;
    }
}
