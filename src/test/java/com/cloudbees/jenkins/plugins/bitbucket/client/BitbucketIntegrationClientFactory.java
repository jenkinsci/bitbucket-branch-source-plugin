/*
 * The MIT License
 *
 * Copyright (c) 2018, Nikolas Falco
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
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerAPIClient;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.tools.ant.filters.StringInputStream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BitbucketIntegrationClientFactory {

    public interface IRequestAudit {
        default void request(String url) {
            // mockito audit
        };

        IRequestAudit getAudit();

        default CloseableHttpResponse loadResponseFromResources(Class<?> resourceBase, String path, String payloadPath) throws IOException {
            try (InputStream json = resourceBase.getResourceAsStream(payloadPath)) {
                if (json == null) {
                    throw new IllegalStateException("Payload for the REST path " + path + " could not be found: " + payloadPath);
                }
                HttpEntity entity = mock(HttpEntity.class);
                String jsonString = IOUtils.toString(json);
                when(entity.getContentLength()).thenReturn((long)jsonString.getBytes(StandardCharsets.UTF_8).length);
                when(entity.getContent()).thenReturn(new StringInputStream(jsonString));

                StatusLine statusLine = mock(StatusLine.class);
                when(statusLine.getStatusCode()).thenReturn(200);

                CloseableHttpResponse response = mock(CloseableHttpResponse.class);
                when(response.getEntity()).thenReturn(entity);
                when(response.getStatusLine()).thenReturn(statusLine);

                return response;
            }
        }
    }

    public static BitbucketApi getClient(String payloadRootPath, String serverURL, String owner, String repositoryName) {
        if (BitbucketCloudEndpoint.SERVER_URL.equals(serverURL) ||
                BitbucketCloudEndpoint.BAD_SERVER_URL.equals(serverURL)) {
            return new BitbucketClouldIntegrationClient(payloadRootPath, owner, repositoryName);
        } else {
            return new BitbucketServerIntegrationClient(payloadRootPath, serverURL, owner, repositoryName);
        }
    }

    public static BitbucketApi getClient(String serverURL, String owner, String repositoryName) {
        return getClient(null, serverURL, owner, repositoryName);
    }

    public static class BitbucketServerIntegrationClient extends BitbucketServerAPIClient implements IRequestAudit {
        private static final String PAYLOAD_RESOURCE_ROOTPATH = "/com/cloudbees/jenkins/plugins/bitbucket/server/payload/";

        private final String payloadRootPath;
        private final IRequestAudit audit;
        private boolean rateLimitNextRequest; // TODO: Would be nice to have a better way to mock non-200 responses.

        private BitbucketServerIntegrationClient(String payloadRootPath, String baseURL, String owner, String repositoryName) {
            super(baseURL, owner, repositoryName, (BitbucketAuthenticator) null, false);

            if (payloadRootPath == null) {
                this.payloadRootPath = PAYLOAD_RESOURCE_ROOTPATH;
            } else if (!payloadRootPath.startsWith("/")) {
                this.payloadRootPath = '/' + payloadRootPath;
            } else {
                this.payloadRootPath = payloadRootPath;
            }
            this.audit = mock(IRequestAudit.class);
        }

        public void rateLimitNextRequest() {
            rateLimitNextRequest = true;
        }

        @Override
        protected CloseableHttpResponse executeMethodNoRetry(CloseableHttpClient client, HttpRequestBase httpMethod, HttpClientContext context) throws IOException {
            if (rateLimitNextRequest) {
                rateLimitNextRequest = false;
                return createRateLimitResponse();
            }
            String path = httpMethod.getURI().toString();
            path = path.substring(path.indexOf("/rest/api/"));
            audit.request(path);

            String payloadPath = path.replace("/rest/api/", "").replace('/', '-').replaceAll("[=%&?]", "_");
            payloadPath = payloadRootPath + payloadPath + ".json";

            return loadResponseFromResources(getClass(), path, payloadPath);
        }

        @Override
        public IRequestAudit getAudit() {
            return audit;
        }
    }

    private static class BitbucketClouldIntegrationClient extends BitbucketCloudApiClient implements IRequestAudit {
        private static final String PAYLOAD_RESOURCE_ROOTPATH = "/com/cloudbees/jenkins/plugins/bitbucket/client/payload/";
        private static final String API_ENDPOINT = "https://api.bitbucket.org/";

        private final String payloadRootPath;
        private final IRequestAudit audit;

        private BitbucketClouldIntegrationClient(String payloadRootPath, String owner, String repositoryName) {
            super(false, 0, 0, owner, repositoryName, (BitbucketAuthenticator) null);

            if (payloadRootPath == null) {
                this.payloadRootPath = PAYLOAD_RESOURCE_ROOTPATH;
            } else if (!payloadRootPath.startsWith("/")) {
                if (!payloadRootPath.endsWith("/")) {
                    this.payloadRootPath = '/' + payloadRootPath + '/';
                } else {
                    this.payloadRootPath = '/' + payloadRootPath;
                }
            } else {
                this.payloadRootPath = payloadRootPath;
            }
            this.audit = mock(IRequestAudit.class);
        }

        @Override
        protected CloseableHttpResponse executeMethod(HttpHost host, HttpRequestBase httpMethod) throws InterruptedException, IOException {
            String path = httpMethod.getURI().toString();
            audit.request(path);

            String payloadPath = path.replace(API_ENDPOINT, "").replace('/', '-').replaceAll("[=%&?]", "_");
            payloadPath = payloadRootPath + payloadPath + ".json";

            return loadResponseFromResources(getClass(), path, payloadPath);
        }

        @Override
        public IRequestAudit getAudit() {
            return audit;
        }
    }

    public static BitbucketApi getApiMockClient(String serverURL) {
        return BitbucketIntegrationClientFactory.getClient(null, serverURL, "amuniz", "test-repos");
    }

    private static CloseableHttpResponse createRateLimitResponse() {
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(429);

        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(response.getStatusLine()).thenReturn(statusLine);

        return response;
    }
}
