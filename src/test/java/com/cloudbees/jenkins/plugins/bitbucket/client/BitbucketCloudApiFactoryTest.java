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
package com.cloudbees.jenkins.plugins.bitbucket.client;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.credentials.BitbucketAccessTokenAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.impl.credentials.BitbucketOAuthAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.util.Secret;
import java.lang.reflect.Field;
import java.util.List;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BitbucketCloudApiFactoryTest {

    @Test
    @WithJenkins
    void usesFallbackWithSameAuthenticationType(JenkinsRule rule) throws Exception {
        StringCredentialsImpl primaryCredentials = token("primary");
        StringCredentialsImpl fallbackCredentials = token("fallback");
        configureFallback(fallbackCredentials);

        try (BitbucketApi api = new BitbucketCloudApiFactory().create(null,
                new BitbucketAccessTokenAuthenticator(primaryCredentials), "owner", null, "repository")) {
            assertThat(authenticator(api))
                    .isInstanceOfSatisfying(BitbucketAuthenticatorPool.class, pool -> assertThat(pool.size()).isEqualTo(2));
        }
    }

    @Test
    @WithJenkins
    void ignoresFallbackWithDifferentAuthenticationType(JenkinsRule rule) throws Exception {
        configureFallback(token("fallback"));
        BitbucketAuthenticator primary = mock(BitbucketOAuthAuthenticator.class);

        try (BitbucketApi api = new BitbucketCloudApiFactory().create(null,
                primary, "owner", null, "repository")) {
            assertThat(authenticator(api)).isSameAs(primary);
        }
    }

    private static void configureFallback(StringCredentialsImpl fallback) throws Exception {
        SystemCredentialsProvider.getInstance().getCredentials().add(fallback);
        SystemCredentialsProvider.getInstance().save();
        BitbucketCloudEndpoint endpoint = new BitbucketCloudEndpoint();
        endpoint.setRateLimitCredentialsId(fallback.getId());
        BitbucketEndpointConfiguration.get().setEndpoints(List.of(endpoint));
    }

    private static StringCredentialsImpl token(String id) {
        return new StringCredentialsImpl(CredentialsScope.SYSTEM, id, null, Secret.fromString("secret-" + id));
    }

    private static BitbucketAuthenticator authenticator(BitbucketApi api) throws Exception {
        Field field = api.getClass().getSuperclass().getDeclaredField("authenticator");
        field.setAccessible(true);
        return (BitbucketAuthenticator) field.get(api);
    }
}
