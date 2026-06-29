/*
 * The MIT License
 *
 * Copyright (c) 2026, CloudBees, Inc.
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

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Descriptor.FormException;
import hudson.util.Secret;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.jupiter.api.Test;

import static org.apache.commons.lang3.RandomStringUtils.insecure;
import static org.assertj.core.api.Assertions.assertThat;

class BitbucketOAuthCredentialMatcherTest {

    private final BitbucketOAuthCredentialMatcher matcher = new BitbucketOAuthCredentialMatcher();

    @Test
    void matchesCurrentBitbucketCloudOAuthConsumerCredentials() throws FormException {
        assertThat(matcher.matches(usernamePassword(insecure().nextAlphanumeric(32), insecure().nextAlphanumeric(76))))
                .isTrue();
    }

    @Test
    void matchesLegacyBitbucketCloudOAuthConsumerCredentials() throws FormException {
        assertThat(matcher.matches(usernamePassword(insecure().nextAlphanumeric(18), insecure().nextAlphanumeric(32))))
                .isTrue();
    }

    @Test
    void doesNotMatchUserApiTokenCredentials() throws FormException {
        assertThat(matcher.matches(usernamePassword("jenkins@example.com", insecure().nextAlphanumeric(76))))
                .isFalse();
    }

    @Test
    void doesNotMatchUnexpectedConsumerLengths() throws FormException {
        assertThat(matcher.matches(usernamePassword(insecure().nextAlphanumeric(32), insecure().nextAlphanumeric(75))))
                .isFalse();
    }

    @Test
    void doesNotMatchSecretTextCredentials() {
        StringCredentialsImpl credentials = new StringCredentialsImpl(
                CredentialsScope.SYSTEM,
                "bitbucket-token",
                "Bitbucket access token",
                Secret.fromString(insecure().nextAlphanumeric(76)));

        assertThat(matcher.matches(credentials)).isFalse();
    }

    private UsernamePasswordCredentialsImpl usernamePassword(String username, String password) throws FormException {
        return new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM,
                "bitbucket-oauth",
                "Bitbucket Cloud OAuth consumer",
                username,
                password);
    }
}
