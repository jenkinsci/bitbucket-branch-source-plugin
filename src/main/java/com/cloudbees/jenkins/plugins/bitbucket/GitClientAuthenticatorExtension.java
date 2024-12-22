package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import org.jenkinsci.plugins.gitclient.GitClient;

// TODO be attention serialized in config.xml of the job as extension child of hudson.plugins.git.GitSCM. Provide a xml alias when move to package com.cloudbees.jenkins.plugins.bitbucket.impl.extension
public class GitClientAuthenticatorExtension extends GitSCMExtension {

    // TODO remove this because it is serialized in config.xml with username and secret (password or token could change/expiry specially with OAuth2)
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
