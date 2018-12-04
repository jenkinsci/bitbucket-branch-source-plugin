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

    public static BitbucketApi getClient(String payloadRootPath, String serverURL, String owner, String repositoryName) {
        if (BitbucketCloudEndpoint.SERVER_URL.equals(serverURL) ||
                BitbucketCloudEndpoint.BAD_SERVER_URL.equals(serverURL)) {
            return new BitbucketCouldIntegrationClient(payloadRootPath, owner, repositoryName);
        } else {
            return new BitbucketServerIntegrationClient(payloadRootPath, serverURL, owner, repositoryName);
        }
    }

    public static BitbucketApi getClient(String serverURL, String owner, String repositoryName) {
        return getClient(null, serverURL, owner, repositoryName);
    }

    private static class BitbucketServerIntegrationClient extends BitbucketServerAPIClient {
        private static final String PAYLOAD_RESOURCE_ROOTPATH = "/com/cloudbees/jenkins/plugins/bitbucket/server/payload/";

        private final String payloadRootPath;

        public BitbucketServerIntegrationClient(String payloadRootPath, String baseURL, String owner, String repositoryName) {
            super(baseURL, owner, repositoryName, (BitbucketAuthenticator) null, false);

            if (payloadRootPath == null) {
                this.payloadRootPath = PAYLOAD_RESOURCE_ROOTPATH;
            } else if (!payloadRootPath.startsWith("/")) {
                this.payloadRootPath = '/' + payloadRootPath;
            } else {
                this.payloadRootPath = payloadRootPath;
            }
        }

        @Override
        protected String getRequest(String path) throws IOException {
            String payloadPath = path.replace("/rest/api/", "").replace('/', '-').replaceAll("[=%&?]", "_");
            payloadPath = payloadRootPath + payloadPath + ".json";

            try (InputStream json = this.getClass().getResourceAsStream(payloadPath)) {
                if (json == null) {
                    throw new IllegalStateException("Payload for the REST path " + path + " could be found");
                }
                return IOUtils.toString(json);
            }
        }
    }

    private static class BitbucketCouldIntegrationClient extends BitbucketCloudApiClient {
        private static final String PAYLOAD_RESOURCE_ROOTPATH = "/com/cloudbees/jenkins/plugins/bitbucket/client/payload/";
        private static final String API_ENDPOINT = "https://api.bitbucket.org/";

        private final String payloadRootPath;

        public BitbucketCouldIntegrationClient(String payloadRootPath, String owner, String repositoryName) {
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
        }

        @Override
        protected String getRequest(String path) throws IOException, InterruptedException {
            String payloadPath = path.replace(API_ENDPOINT, "").replace('/', '-').replaceAll("[=%&?]", "_");
            payloadPath = payloadRootPath + payloadPath + ".json";

            try (InputStream json = this.getClass().getResourceAsStream(payloadPath)) {
                if (json == null) {
                    throw new IllegalStateException("Payload for the REST path " + path + " could be found");
                }
                return IOUtils.toString(json);
            }
        }
    }
}
