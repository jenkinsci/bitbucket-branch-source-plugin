package com.cloudbees.jenkins.plugins.bitbucket.credentials;


import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.NameWith;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.Util;
import hudson.util.Secret;
import java.io.IOException;

@NameWith(
        value = BitbucketOAuthCredentials.NameProvider.class,
        priority = 32
)


public interface BitbucketOAuthCredentials extends StandardCredentials {
    Secret getSecret() throws IOException, InterruptedException;
    String getKey() throws IOException, InterruptedException;
    class NameProvider extends CredentialsNameProvider<BitbucketOAuthCredentials> {
        @Override
        public String getName(BitbucketOAuthCredentials c) {
            String description = Util.fixEmptyAndTrim(c.getDescription());
            return "*bitbucket*"
            + (description != null ? " (" + description + ")" : "");
        }
    }
}
