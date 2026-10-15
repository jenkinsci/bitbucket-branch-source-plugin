/*
 * The MIT License
 *
 * Copyright (c) 2026, Falco Nikolas
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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Descriptor.FormException;
import java.util.List;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BitbucketMultiCredentialsAuthenticatorTest {

    @BeforeEach
    void setup() {
        BitbucketMultiCredentialsAuthenticator.clearCache();
    }

    @Test
    void given_two_credentials_when_current_is_rate_limited_then_it_uses_the_other_one() throws Exception {
        BitbucketAuthenticator first = buildAuthenticaction("1");
        BitbucketAuthenticator second = buildAuthenticaction("2");
        BitbucketMultiCredentialsAuthenticator auth = new BitbucketMultiCredentialsAuthenticator(List.of(first, second));

        HttpRequest firstRequest = new BasicHttpRequest("GET", "/first");
        auth.configureRequest(firstRequest);
        assertThat(auth.getId()).isEqualTo(first.getId());

        auth.markLimitReached();

        HttpRequest secondRequest = new BasicHttpRequest("GET", "/second");
        auth.configureRequest(secondRequest);
        assertThat(auth.getId()).isEqualTo(second.getId());
    }

    private BitbucketAuthenticator buildAuthenticaction(String credentialId) throws FormException {
        return new BitbucketUsernamePasswordAuthenticator(new UsernamePasswordCredentialsImpl(null, credentialId, null, "user1", "password1"));
    }

    @Test
    void when_all_credentials_reach_rate_limited_then_throw_illegal_state_exception() throws Exception {
        BitbucketAuthenticator first = buildAuthenticaction("1");
        BitbucketAuthenticator second = buildAuthenticaction("2");
        BitbucketMultiCredentialsAuthenticator auth = new BitbucketMultiCredentialsAuthenticator(List.of(first, second));

        auth.markLimitReached();

        assertThatThrownBy(auth::markLimitReached)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("All credentials reach the rate API limit.");
    }

    @Test
    void when_first_instance_marks_credential_1_expired_then_second_instance_skips_1_and_uses_3() throws Exception {
        BitbucketMultiCredentialsAuthenticator firstInstance = new BitbucketMultiCredentialsAuthenticator(List.of(buildAuthenticaction("1"), buildAuthenticaction("2")));
        BitbucketMultiCredentialsAuthenticator secondInstance = new BitbucketMultiCredentialsAuthenticator(List.of(buildAuthenticaction("1"), buildAuthenticaction("3")));

        HttpRequest firstRequest = new BasicHttpRequest("GET", "/first-instance");
        firstInstance.configureRequest(firstRequest);
        assertThat(firstInstance.getId()).isEqualTo("1");

        firstInstance.markLimitReached();

        HttpRequest secondRequest = new BasicHttpRequest("GET", "/second-instance");
        secondInstance.configureRequest(secondRequest);
        assertThat(secondInstance.getId()).isEqualTo("3");
    }

}