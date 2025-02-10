/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.jenkins.plugins.bitbucket.impl.credentials;

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

    @Test
    @Issue("JENKINS-75225")
    void matches_returns_false_when_getting_password_takes_longer() {
        assertThat(sut.matches(new TakeLongCredentials())).isFalse();
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

    @SuppressWarnings("serial")
    private static class TakeLongCredentials implements UsernamePasswordCredentials {

        @NonNull
        @Override
        public Secret getPassword() {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return Secret.fromString("password");
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
