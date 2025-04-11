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
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketApiUtils;
import com.cloudbees.jenkins.plugins.bitbucket.server.BitbucketServerWebhookImplementation;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerAPIClient;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.tools.ant.filters.StringInputStream;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BitbucketIntegrationClientFactory {

    public interface IAuditable {
        IRequestAudit getAudit();

        default ClassicHttpResponse loadResponseFromResources(Class<?> resourceBase, String path, String payloadPath) throws IOException {
            try (InputStream json = resourceBase.getResourceAsStream(payloadPath)) {
                if (json == null) {
                    throw new FileNotFoundException("Payload for the REST path " + path + " could not be found: " + payloadPath);
                }
                HttpEntity entity = mock(HttpEntity.class);
                String jsonString = IOUtils.toString(json, StandardCharsets.UTF_8);
                when(entity.getContentLength()).thenReturn((long)jsonString.getBytes(StandardCharsets.UTF_8).length);
                when(entity.getContent()).thenReturn(new StringInputStream(jsonString));

                ClassicHttpResponse response = mock(ClassicHttpResponse.class);
                when(response.getEntity()).thenReturn(entity);
                when(response.getCode()).thenReturn(200);

                return response;
            }
        }
    }

    public interface IRequestAudit {
        default void request(HttpRequest request) {
            // mockito audit
        }

        IRequestAudit getAudit();
    }

    public static BitbucketApi getClient(String payloadRootPath, String serverURL, String owner, String repositoryName) {
        if (BitbucketApiUtils.isCloud(serverURL)) {
            return new BitbucketClouldIntegrationClient(payloadRootPath, owner, repositoryName);
        } else {
            return new BitbucketServerIntegrationClient(payloadRootPath, serverURL, owner, repositoryName);
        }
    }

    public static BitbucketApi getClient(String serverURL, String owner, String repositoryName) {
        return getClient(null, serverURL, owner, repositoryName);
    }

    private static class BitbucketServerIntegrationClient extends BitbucketServerAPIClient implements IAuditable {
        private static final String PAYLOAD_RESOURCE_ROOTPATH = "/com/cloudbees/jenkins/plugins/bitbucket/server/payload/";

        private final String payloadRootPath;
        private final IRequestAudit audit;

        private BitbucketServerIntegrationClient(String payloadRootPath, String baseURL, String owner, String repositoryName) {
            super(baseURL, owner, repositoryName, mock(BitbucketAuthenticator.class), false, BitbucketServerWebhookImplementation.NATIVE);

            if (payloadRootPath == null) {
                this.payloadRootPath = PAYLOAD_RESOURCE_ROOTPATH;
            } else if (!payloadRootPath.startsWith("/")) {
                this.payloadRootPath = '/' + payloadRootPath;
            } else {
                this.payloadRootPath = payloadRootPath;
            }
            this.audit = mock(IRequestAudit.class);
        }

        @Override
        protected ClassicHttpResponse executeMethod(HttpUriRequest httpMethod) throws IOException {
            String requestURI = httpMethod.getRequestUri();
            audit.request(httpMethod);

            String payloadPath = requestURI.substring(requestURI.indexOf("/rest/"))
                    .replace("/rest/api/", "")
                    .replace("/rest/", "")
                    .replace('/', '-').replaceAll("[=%&?]", "_");
            payloadPath = payloadRootPath + payloadPath + ".json";

            return loadResponseFromResources(getClass(), requestURI, payloadPath);
        }

        @Override
        public BitbucketAuthenticator getAuthenticator() {
            return super.getAuthenticator();
        }

        @Override
        public IRequestAudit getAudit() {
            return audit;
        }
    }

    private static class BitbucketClouldIntegrationClient extends BitbucketCloudApiClient implements IAuditable {
        private static final String PAYLOAD_RESOURCE_ROOTPATH = "/com/cloudbees/jenkins/plugins/bitbucket/client/payload/";
        private static final String API_ENDPOINT = "https://api.bitbucket.org/";

        private final String payloadRootPath;
        private final IRequestAudit audit;

        private BitbucketClouldIntegrationClient(String payloadRootPath, String owner, String repositoryName) {
            super(false, 0, 0, owner, null, repositoryName, mock(BitbucketAuthenticator.class));

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
        protected CloseableHttpClient getClient() {
            CloseableHttpClient client = mock(CloseableHttpClient.class);
            try {
                when(client.executeOpen(any(HttpHost.class), any(ClassicHttpRequest.class), any(HttpContext.class))).thenAnswer(new Answer<ClassicHttpResponse>() {
                    @Override
                    public ClassicHttpResponse answer(InvocationOnMock invocation) throws Throwable {
                        HttpRequest httpMethod = invocation.getArgument(1);
                        String uri = httpMethod.getRequestUri();
                        audit.request(httpMethod);

                        String path = uri.replace(API_ENDPOINT, "");
                        if (path.startsWith("/")) {
                            path = path.replaceFirst("/", "");
                        }
                        String payloadPath = path.replace('/', '-').replaceAll("[=%&?]", "_");
                        payloadPath = payloadRootPath + payloadPath + ".json";

                        return loadResponseFromResources(getClass(), uri, payloadPath);
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return client;
        }

        @Override
        public BitbucketAuthenticator getAuthenticator() {
            return super.getAuthenticator();
        }

        @Override
        public IRequestAudit getAudit() {
            return audit;
        }
    }

    public static BitbucketApi getApiMockClient(String serverURL) {
        return BitbucketIntegrationClientFactory.getClient(null, serverURL, "amuniz", "test-repos");
    }

    public static BitbucketAuthenticator extractAuthenticator(BitbucketApi client) {
        if (client instanceof BitbucketClouldIntegrationClient cloudClient) {
            return cloudClient.getAuthenticator();
        } else if (client instanceof BitbucketServerIntegrationClient serverClient) {
            return serverClient.getAuthenticator();
        } else {
            throw new IllegalArgumentException("client class not supported");
        }
    }
}
