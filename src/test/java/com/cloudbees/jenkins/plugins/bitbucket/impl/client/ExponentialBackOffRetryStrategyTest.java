/*
 * The MIT License
 *
 * Copyright (c) 2024, Nikolas Falco
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
package com.cloudbees.jenkins.plugins.bitbucket.impl.client;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.server.BitbucketServerWebhookImplementation;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerAPIClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.model.HttpRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.mockito.Mockito.mock;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@ExtendWith(MockServerExtension.class)
class ExponentialBackOffRetryStrategyTest {

    @Test
    void test_retry(ClientAndServer mockServer) throws Exception {
        HttpRequest request = request() //
                .withMethod("GET") //
                .withPath("/rest/api/1.0/projects/test/repos/testRepos/tags");
        mockServer.when(request)
            .respond( //
                response() //
                    .withStatusCode(429) //
        );

        final RetryInterceptor counterInterceptor = new RetryInterceptor();
        try (BitbucketApi client = new BitbucketServerAPIClient("http://localhost:" + mockServer.getPort(),
                "test",
                "testRepos",
            null,
                false,
                mock(BitbucketServerWebhookImplementation.class)) {
            @Override
            protected HttpClientBuilder setupClientBuilder() {
                return super.setupClientBuilder()
                        .setRetryStrategy(new ExponentialBackoffRetryStrategy(2, 5, 100))
                        .addResponseInterceptorFirst(counterInterceptor);
            }
        }) {

            assertThatIOException().isThrownBy(client::getTags);
            assertThat(counterInterceptor.getRetry()).isEqualTo(6); // retry after 5ms, 10ms, 20ms, 40ms, 80ms, 100ms
        }
    }

    private static class RetryInterceptor implements HttpResponseInterceptor {
        private int retry = 0;

        @Override
        public void process(HttpResponse response, EntityDetails entity, HttpContext context) {
            if (response.getCode() == 429) {
                retry += 1;
            }
        }

        public int getRetry() {
            return retry;
        }

    }
}
