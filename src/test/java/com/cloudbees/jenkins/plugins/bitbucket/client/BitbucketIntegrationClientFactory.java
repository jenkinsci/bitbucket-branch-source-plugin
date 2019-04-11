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
import org.apache.commons.io.IOUtils;

public class BitbucketIntegrationClientFactory {

    public static BitbucketApi getClient(String serverURL, String owner, String repositoryName) {
        if (BitbucketCloudEndpoint.SERVER_URL.equals(serverURL) ||
                BitbucketCloudEndpoint.BAD_SERVER_URL.equals(serverURL)) {
            BitbucketCloudIntegrationClient cloudClient = new BitbucketCloudIntegrationClient();
            return cloudClient.getBitbucketCloudApiClient(owner, repositoryName);
        } else {
            BitbucketServerIntegrationClient serverClient = new BitbucketServerIntegrationClient();
            return serverClient.getBitbucketServerAPIClient(serverURL,owner, repositoryName);
        }
    }

    private static class BitbucketServerIntegrationClient {
        private static final String PAYLOAD_RESOURCE_ROOTPATH = "/com/cloudbees/jenkins/plugins/bitbucket/server/payload/";

        BitbucketApi getBitbucketServerAPIClient(String baseURL, String owner, String repositoryName) {
            return new BitbucketServerAPIClient(baseURL, owner, repositoryName, (BitbucketAuthenticator) null, false) {
                @Override
                protected String getRequest(String path) throws IOException {
                    String payloadPath = path.replace("/rest/api/", "").replace('/', '-').replaceAll("[=%&?]", "_");
                    payloadPath = PAYLOAD_RESOURCE_ROOTPATH + payloadPath + ".json";

                    try (InputStream json = this.getClass().getResourceAsStream(payloadPath)) {
                        if (json == null) {
                            throw new IllegalStateException("Payload for the REST path: " + payloadPath);
                        }
                        return IOUtils.toString(json);
                    }
                }
            };
        }
    }

    private static class BitbucketCloudIntegrationClient {
        private static final String PAYLOAD_RESOURCE_ROOTPATH = "/com/cloudbees/jenkins/plugins/bitbucket/client/payload/";
        private static final String API_ENDPOINT = "https://api.bitbucket.org/";

        BitbucketApi getBitbucketCloudApiClient(String owner, String repositoryName) {
            return new BitbucketCloudApiClient(false, 0, 0, owner, repositoryName, (BitbucketAuthenticator) null) {
                @Override
                protected String getRequest(String path) throws IOException, InterruptedException {
                    String payloadPath = path.replace(API_ENDPOINT, "").replace('/', '-').replaceAll("[=%&?]", "_");
                    payloadPath = PAYLOAD_RESOURCE_ROOTPATH + payloadPath + ".json";

                    try (InputStream json = this.getClass().getResourceAsStream(payloadPath)) {
                        if (json == null) {
                            throw new IllegalStateException("Payload for the REST path: " + payloadPath);
                        }
                        return IOUtils.toString(json);
                    }
                }
            };
        }
    }

    public static BitbucketApi getApiMockClient(String serverURL) {
        return BitbucketIntegrationClientFactory.getClient(serverURL, "amuniz", "test-repos");
    }
}
