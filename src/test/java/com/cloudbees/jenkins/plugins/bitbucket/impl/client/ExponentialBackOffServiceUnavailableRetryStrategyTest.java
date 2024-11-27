package com.cloudbees.jenkins.plugins.bitbucket.impl.client;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.server.BitbucketServerWebhookImplementation;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerAPIClient;
import java.io.IOException;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
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
class ExponentialBackOffServiceUnavailableRetryStrategyTest {

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

        final RetryInterceptor interceptor = new RetryInterceptor();
        BitbucketApi client = new BitbucketServerAPIClient("http://localhost:" + mockServer.getPort(),
                "test",
                "testRepos",
                (BitbucketAuthenticator) null,
                false,
                mock(BitbucketServerWebhookImplementation.class)) {
            @Override
            protected HttpClientBuilder setupClientBuilder(String host) {
                return super.setupClientBuilder(host)
                        .disableAutomaticRetries()
                        .setServiceUnavailableRetryStrategy(new ExponentialBackOffServiceUnavailableRetryStrategy(2, 5, 100))
                        .addInterceptorFirst(interceptor);
            }
        };

        assertThatIOException().isThrownBy(() -> client.getTags());
        String logMessages = mockServer.retrieveLogMessages(request);
        System.out.println(logMessages);
        assertThat(interceptor.getRetry()).isEqualTo(6); // retry after 5ms, 10ms, 20ms, 40ms, 80ms, 100ms
    }

    private static class RetryInterceptor implements HttpResponseInterceptor {
        private int retry = 0;

        @Override
        public void process(HttpResponse response, HttpContext context) throws HttpException, IOException {
            if (response.getStatusLine().getStatusCode() == 429) {
                retry += 1;
            }
        }

        public int getRetry() {
            return retry;
        }
    }
}
