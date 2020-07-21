package com.cloudbees.jenkins.plugins.bitbucket.credentials;


import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import hudson.Extension;
import hudson.util.Secret;
import javax.annotation.CheckForNull;
import org.kohsuke.stapler.DataBoundConstructor;


public class BitbucketOAuthCredentialsImpl extends BaseStandardCredentials implements BitbucketOAuthCredentials {

    private final String key;
    private final Secret secret;
    @DataBoundConstructor
    public BitbucketOAuthCredentialsImpl(@CheckForNull CredentialsScope scope, @CheckForNull String id, String key,
            String secret, @CheckForNull String description) {
        super(scope, id, description);
        this.key = key;
        this.secret = Secret.fromString(secret);
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public Secret getSecret() {
        return secret;
    }

    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {
        @Override
        public String getDisplayName() {
            return "Bitbucket OAuth credentials";
        }
    }
}
