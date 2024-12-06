package com.cloudbees.jenkins.plugins.bitbucket.credentials;

import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.util.Secret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

import static org.assertj.core.api.Assertions.assertThat;

class BitbucketOAuthCredentialMatcherTest {

    private BitbucketOAuthCredentialMatcher sut;

    @BeforeEach
    void setup() {
        sut = new BitbucketOAuthCredentialMatcher();
    }

    /**
     * Some plugins do remote work when getPassword is called and aren't expecting to just be randomly looked up
     * One example is GitHubAppCredentials
     */
    @Test
    @Issue("JENKINS-63401")
    void matches_returns_false_when_exception_getting_password() {
        assertThat(sut.matches(new ExceptionalCredentials())).isFalse();
    }

    @SuppressWarnings("serial")
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
