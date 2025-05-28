/*
 * The MIT License
 *
 * Copyright (c) 2025, Nikolas Falco
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
package com.cloudbees.jenkins.plugins.bitbucket.test.util;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory.IAuditable;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.util.Secret;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import org.apache.hc.core5.http.HttpRequest;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jvnet.hudson.test.JenkinsRule;

import static org.assertj.core.api.Assertions.assertThat;

public final class BitbucketTestUtil {

    private BitbucketTestUtil() {
    }

    public static StringCredentials registerHookCredentials(String secret, JenkinsRule rule) throws IOException {
        StringCredentials secretCredentials = new StringCredentialsImpl(CredentialsScope.GLOBAL, "hmac", null, Secret.fromString(secret));
        CredentialsProvider.lookupStores(rule.jenkins)
            .iterator()
            .next()
            .addCredentials(Domain.global(), secretCredentials);
        return secretCredentials;
    }

    public static HttpRequest extractRequest(BitbucketApi client) {
        assertThat(client).isInstanceOf(IAuditable.class);

        return ((IAuditable) client).getAudit().lastRequest();
    }

    public static CompletableFuture<HttpRequest> waitForRequest(BitbucketApi client, Predicate<HttpRequest> predicate) {
        assertThat(client).isInstanceOf(IAuditable.class);

        return ((IAuditable) client).getAudit().waitRequest(predicate);
    }
}
