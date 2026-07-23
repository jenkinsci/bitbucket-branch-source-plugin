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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import java.io.IOException;
import java.util.List;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BitbucketAuthenticatorPoolTest {

    @Test
    void rotatesCredentialsOnRateLimit() throws Exception {
        BitbucketAuthenticator primary = authenticator("primary");
        BitbucketAuthenticator fallback = authenticator("fallback");
        BitbucketAuthenticatorPool pool = new BitbucketAuthenticatorPool(List.of(primary, fallback));
        ClassicHttpResponse limited = response(HttpStatus.SC_TOO_MANY_REQUESTS);
        ClassicHttpResponse successful = response(HttpStatus.SC_OK);
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        when(httpClient.executeOpen(any(HttpHost.class), any(ClassicHttpRequest.class), nullable(HttpContext.class)))
                .thenReturn(limited, successful);

        TestClient client = new TestClient(pool, httpClient);
        assertThat(client.execute()).isSameAs(successful);

        var ordered = inOrder(primary, fallback);
        ordered.verify(primary).configureRequest(any());
        ordered.verify(fallback).configureRequest(any());
        verify(limited).close();
        assertThat(pool.getId()).isEqualTo("fallback");
    }

    @Test
    void nearLimitSelectsFallbackForNextRequest() throws Exception {
        BitbucketAuthenticatorPool pool = new BitbucketAuthenticatorPool(
                List.of(authenticator("primary"), authenticator("fallback")));
        ClassicHttpResponse response = response(HttpStatus.SC_OK);
        when(response.getFirstHeader("X-RateLimit-NearLimit")).thenReturn(new BasicHeader("X-RateLimit-NearLimit", "true"));
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        when(httpClient.executeOpen(any(HttpHost.class), any(ClassicHttpRequest.class), nullable(HttpContext.class)))
                .thenReturn(response);

        assertThat(new TestClient(pool, httpClient).execute()).isSameAs(response);
        assertThat(pool.getId()).isEqualTo("fallback");
    }

    @Test
    void removesDuplicateCredentialIds() {
        BitbucketAuthenticator first = authenticator("same");
        BitbucketAuthenticator duplicate = authenticator("same");
        BitbucketAuthenticatorPool pool = new BitbucketAuthenticatorPool(List.of(first, duplicate));

        assertThat(pool.size()).isOne();
        assertThat(pool.advance()).isFalse();
    }

    @Test
    void rejectsMixedAuthenticatorTypes() {
        BitbucketAuthenticator first = authenticator("first");
        BitbucketAuthenticator second = mock(OtherAuthenticator.class);
        when(second.getId()).thenReturn("second");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BitbucketAuthenticatorPool(List.of(first, second)))
                .withMessage("All authenticators must have the same type");
    }

    @Test
    void configuresBuilderOnlyOnce() {
        BitbucketAuthenticator primary = authenticator("primary");
        BitbucketAuthenticator fallback = authenticator("fallback");
        BitbucketAuthenticatorPool pool = new BitbucketAuthenticatorPool(List.of(primary, fallback));
        HttpClientBuilder builder = HttpClientBuilder.create();

        pool.configureBuilder(builder);

        verify(primary).configureBuilder(builder);
        verify(fallback, never()).configureBuilder(builder);
    }

    @Test
    void doesNotApplyBackoffAfterFallbackIsExhausted() throws Exception {
        BitbucketAuthenticatorPool pool = new BitbucketAuthenticatorPool(
                List.of(authenticator("primary"), authenticator("fallback")));
        ClassicHttpResponse limited = response(HttpStatus.SC_TOO_MANY_REQUESTS);
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        when(httpClient.executeOpen(any(HttpHost.class), any(ClassicHttpRequest.class), nullable(HttpContext.class)))
                .thenReturn(limited);

        assertThat(new TestClient(pool, httpClient).execute()).isSameAs(limited);

        verify(httpClient, times(2))
                .executeOpen(any(HttpHost.class), any(ClassicHttpRequest.class), nullable(HttpContext.class));
    }

    private abstract static class OtherAuthenticator implements BitbucketAuthenticator {}

    private static BitbucketAuthenticator authenticator(String id) {
        BitbucketAuthenticator authenticator = mock(BitbucketAuthenticator.class);
        when(authenticator.getId()).thenReturn(id);
        return authenticator;
    }

    private static ClassicHttpResponse response(int status) {
        ClassicHttpResponse response = mock(ClassicHttpResponse.class);
        when(response.getCode()).thenReturn(status);
        return response;
    }

    private static final class TestClient extends BitbucketCloudApiClient {
        private final CloseableHttpClient httpClient;

        TestClient(BitbucketAuthenticator authenticator, CloseableHttpClient httpClient) {
            super(false, 0, 0, "owner", null, "repository", authenticator);
            this.httpClient = httpClient;
        }

        ClassicHttpResponse execute() throws IOException {
            HttpUriRequest request = new HttpGet("https://api.bitbucket.org/2.0/repositories/owner/repository");
            return executeMethod(request);
        }

        @Override
        protected CloseableHttpClient getClient() {
            return httpClient;
        }
    }
}
