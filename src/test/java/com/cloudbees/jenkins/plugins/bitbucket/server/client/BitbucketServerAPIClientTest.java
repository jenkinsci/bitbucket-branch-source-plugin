/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket.server.client;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus.Status;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory.BitbucketServerIntegrationClient;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory.IRequestAudit;
import hudson.ProxyConfiguration;
import io.jenkins.cli.shaded.org.apache.commons.lang.RandomStringUtils;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@WithJenkins
class BitbucketServerAPIClientTest {

    private static JenkinsRule j;

    @BeforeAll
    static void init(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void verify_status_notitication_name_max_length() throws Exception {
        String serverURL = "https://acme.bitbucket.org";
        BitbucketApi client = BitbucketIntegrationClientFactory.getApiMockClient(serverURL);
        BitbucketBuildStatus status = new BitbucketBuildStatus();
        status.setName(RandomStringUtils.randomAlphanumeric(300));
        status.setState(Status.INPROGRESS);
        status.setHash("046d9a3c1532acf4cf08fe93235c00e4d673c1d3");

        client.postBuildStatus(status);

        HttpRequestBase request = extractRequest(client);
        assertThat(request).isNotNull()
            .isInstanceOf(HttpPost.class);
        try (InputStream content = ((HttpPost) request).getEntity().getContent()) {
            String json = IOUtils.toString(content, StandardCharsets.UTF_8);
            assertThatJson(json).node("name").isString().hasSize(255);
        }
    }

    private HttpRequestBase extractRequest(BitbucketApi client) {
        assertThat(client).isInstanceOf(IRequestAudit.class);
        IRequestAudit clientAudit = ((IRequestAudit) client).getAudit();

        ArgumentCaptor<HttpRequestBase> captor = ArgumentCaptor.forClass(HttpRequestBase.class);
        verify(clientAudit).request(captor.capture());
        return captor.getValue();
    }

    private BitbucketAuthenticator extractAuthenticator(BitbucketApi client) {
        assertThat(client).isInstanceOf(BitbucketServerIntegrationClient.class);
        return ((BitbucketServerIntegrationClient) client).getAuthenticator();
    }

    @Test
    void verify_checkPathExists_given_a_path() throws Exception {
        BitbucketApi client = BitbucketIntegrationClientFactory.getApiMockClient("https://acme.bitbucket.org");
        assertThat(client.checkPathExists("feature/pipeline", "folder/Jenkinsfile")).isTrue();

        HttpRequestBase request = extractRequest(client);
        assertThat(request).isNotNull()
            .isInstanceOfSatisfying(HttpHead.class, head -> {
                    assertThat(head.getURI())
                        .hasScheme("https")
                        .hasHost("acme.bitbucket.org")
                        .hasPath("/rest/api/1.0/projects/amuniz/repos/test-repos/browse/folder/Jenkinsfile")
                        .hasQuery("at=feature/pipeline");
            });
    }

    @Test
    void verify_checkPathExists_given_file() throws Exception {
        BitbucketApi client = BitbucketIntegrationClientFactory.getApiMockClient("https://acme.bitbucket.org");
        assertThat(client.checkPathExists("feature/pipeline", "Jenkinsfile")).isTrue();

        HttpRequestBase request = extractRequest(client);
        assertThat(request).isNotNull()
            .isInstanceOfSatisfying(HttpHead.class, head ->
                assertThat(head.getURI())
                    .hasScheme("https")
                    .hasHost("acme.bitbucket.org")
                    .hasPath("/rest/api/1.0/projects/amuniz/repos/test-repos/browse/Jenkinsfile"));
    }

    @Test
    void filterArchivedRepositories() throws Exception {
        BitbucketApi client = BitbucketIntegrationClientFactory.getClient("localhost", "foo", "test-repos");
        List<? extends BitbucketRepository> repos = client.getRepositories();
        List<String> names = repos.stream().map(BitbucketRepository::getRepositoryName).toList();
        assertThat(names, not(hasItem("bar-archived")));
        assertThat(names, is(List.of("bar-active")));
    }

    @Test
    void sortRepositoriesByName() throws Exception {
        BitbucketApi client = BitbucketIntegrationClientFactory.getClient("localhost", "amuniz", "test-repos");
        List<? extends BitbucketRepository> repos = client.getRepositories();
        List<String> names = repos.stream().map(BitbucketRepository::getRepositoryName).toList();
        assertThat(names, is(List.of("another-repo", "dogs-repo", "test-repos")));
    }

    @Test
    void disableCookieManager() throws Exception {
        try (MockedStatic<HttpClientBuilder> staticHttpClientBuilder = mockStatic(HttpClientBuilder.class)) {
            HttpClientBuilder httpClientBuilder = mock(HttpClientBuilder.class, RETURNS_SELF);
            staticHttpClientBuilder.when(HttpClientBuilder::create).thenReturn(httpClientBuilder);
            BitbucketIntegrationClientFactory.getClient("localhost", "amuniz", "test-repos");
            verify(httpClientBuilder).disableCookieManagement();
        }
    }

    @Test
    void verify_mirroredRepository_does_not_authenticate_request() throws Exception {
        String serverURL = "https://acme.bitbucket.org";
        BitbucketServerAPIClient client = (BitbucketServerAPIClient) BitbucketIntegrationClientFactory.getClient(serverURL, "amuniz", "test-repos");

        BitbucketAuthenticator authenticator = extractAuthenticator(client);
        String url = serverURL + "/rest/mirroring/latest/upstreamServers/1/repos/1?jwt=TOKEN";
        client.getMirroredRepository(url);
        verify(authenticator, never()).configureRequest(any(HttpRequest.class));
    }

    @Issue("JENKINS-64418")
    @Test
    void verify_getBranch_request_URL() throws Exception {
        String serverURL = "https://acme.bitbucket.org";
        BitbucketServerAPIClient client = (BitbucketServerAPIClient) BitbucketIntegrationClientFactory.getClient(serverURL, "amuniz", "test-repos");

        client.getBranch("feature/BB-1");
        HttpRequestBase request = extractRequest(client);
        assertThat(request).isNotNull()
            .isInstanceOfSatisfying(HttpGet.class, head ->
                assertThat(head.getURI())
                    .hasScheme("https")
                    .hasHost("acme.bitbucket.org")
                    .hasPath("/rest/api/1.0/projects/amuniz/repos/test-repos/branches"));
    }

    @Issue("JENKINS-64418")
    @Test
    void verify_getTag_request_URL() throws Exception {
        String serverURL = "https://acme.bitbucket.org";
        BitbucketServerAPIClient client = (BitbucketServerAPIClient) BitbucketIntegrationClientFactory.getClient(serverURL, "amuniz", "test-repos");

        client.getTag("v0.0.0");
        HttpRequestBase request = extractRequest(client);
        assertThat(request).isNotNull()
            .isInstanceOfSatisfying(HttpGet.class, head ->
                assertThat(head.getURI())
                    .hasScheme("https")
                    .hasHost("acme.bitbucket.org")
                    .hasPath("/rest/api/1.0/projects/amuniz/repos/test-repos/tags/v0.0.0"));
    }

    @Issue("JENKINS-75119")
    @Test
    void verify_HttpHost_built_when_server_has_context_root() throws Exception {
        String serverURL = "https://acme.bitbucket.org/bitbucket";
        BitbucketServerAPIClient client = (BitbucketServerAPIClient) BitbucketIntegrationClientFactory.getClient(serverURL, "amuniz", "test-repos");

        BitbucketAuthenticator authenticator = extractAuthenticator(client);
        client.getRepository();

        HttpHost expectedHost = HttpHost.create("https://acme.bitbucket.org");
        verify(authenticator).configureContext(any(HttpClientContext.class), eq(expectedHost));
    }

    @Issue("JENKINS-75160")
    @Test
    void test_no_proxy_configurations() throws Exception {
        ProxyConfiguration proxyConfiguration = spy(new ProxyConfiguration("proxy.lan", 8080, null, null, "*.internaldomain.com"));

        j.jenkins.setProxy(proxyConfiguration);

        AtomicReference<HttpClientBuilder> builderReference = new AtomicReference<>();
        try(BitbucketApi client = new BitbucketServerAPIClient("https://git.internaldomain.com:7990/bitbucket", "amuniz", "test-repos", mock(BitbucketAuthenticator.class), false) {
            @Override
            protected void setClientProxyParams(HttpClientBuilder builder) {
                builderReference.set(spy(builder));
                super.setClientProxyParams(builderReference.get());
            }
        }) {}

        verify(proxyConfiguration).createProxy("git.internaldomain.com");
        verify(builderReference.get(), never()).setProxy(any(HttpHost.class));
    }

}
