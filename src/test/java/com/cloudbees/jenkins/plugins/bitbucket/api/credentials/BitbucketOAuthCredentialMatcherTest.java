package com.cloudbees.jenkins.plugins.bitbucket.api.credentials;

import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.util.Secret;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import static org.junit.Assert.assertFalse;

public class BitbucketOAuthCredentialMatcherTest {

    /**
     * Some plugins do remote work when getPassword is called and aren't expecting to just be randomly looked up
     * One example is GitHubAppCredentials
     */
    @Test
    @Issue("JENKINS-63401")
    public void matches_returns_false_when_exception_getting_password() {
        assertFalse(new BitbucketOAuthCredentialMatcher().matches(new ExceptionalCredentials()));
    }

    private static class ExceptionalCredentials implements UsernamePasswordCredentials {

        @NonNull
        @Override
        public Secret getPassword() {
            throw new IllegalArgumentException("Failed authentication");
        }

        @NonNull
        @Override
        public String getUsername() {
            return "dummy-username";
        }

        @Override
        public CredentialsScope getScope() {
            return null;
        }

        @NonNull
        @Override
        public CredentialsDescriptor getDescriptor() {
            return null;
        }
    }
}
