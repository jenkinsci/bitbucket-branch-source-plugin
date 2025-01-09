package com.cloudbees.jenkins.plugins.bitbucket.server.client;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus.Status;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory.BitbucketServerIntegrationClient;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory.IRequestAudit;
import io.jenkins.cli.shaded.org.apache.commons.lang.RandomStringUtils;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpRequest;
import org.assertj.core.api.InstanceOfAssertFactories;
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
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@WithJenkins
class BitbucketServerAPIClientTest {

    @SuppressWarnings("unused")
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

        HttpRequest request = extractRequest(client);
        assertThat(request).isNotNull()
            .isInstanceOf(HttpPost.class);
        try (InputStream content = ((HttpPost) request).getEntity().getContent()) {
            String json = IOUtils.toString(content, StandardCharsets.UTF_8);
            assertThatJson(json).node("name").isString().hasSize(255);
        }
    }

    private HttpRequest extractRequest(BitbucketApi client) {
        assertThat(client).isInstanceOf(IRequestAudit.class);
        IRequestAudit clientAudit = ((IRequestAudit) client).getAudit();

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
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

        HttpRequest request = extractRequest(client);
        assertThat(request).isNotNull()
            .isInstanceOf(HttpHead.class)
            .asInstanceOf(InstanceOfAssertFactories.type(HttpHead.class))
            .satisfies(head ->
                assertThat(head.getUri())
                    .hasScheme("https")
                    .hasHost("acme.bitbucket.org")
                    .hasPath("/rest/api/1.0/projects/amuniz/repos/test-repos/browse/folder/Jenkinsfile")
                    .hasQuery("at=feature/pipeline")
            );
    }

    @Test
    void verify_checkPathExists_given_file() throws Exception {
        BitbucketApi client = BitbucketIntegrationClientFactory.getApiMockClient("https://acme.bitbucket.org");
        assertThat(client.checkPathExists("feature/pipeline", "Jenkinsfile")).isTrue();

        HttpRequest request = extractRequest(client);
        assertThat(request).isNotNull()
            .isInstanceOf(HttpHead.class)
            .asInstanceOf(InstanceOfAssertFactories.type(HttpHead.class))
            .satisfies(head ->
                assertThat(head.getUri())
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
        HttpRequest request = extractRequest(client);
        assertThat(request).isNotNull()
            .isInstanceOf(HttpHead.class)
            .asInstanceOf(InstanceOfAssertFactories.type(HttpHead.class))
            .satisfies(head ->
                assertThat(head.getUri())
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
        HttpRequest request = extractRequest(client);
        assertThat(request).isNotNull()
            .isInstanceOf(HttpHead.class)
            .asInstanceOf(InstanceOfAssertFactories.type(HttpHead.class))
            .satisfies(head ->
                assertThat(head.getUri())
                    .hasScheme("https")
                    .hasHost("acme.bitbucket.org")
                    .hasPath("/rest/api/1.0/projects/amuniz/repos/test-repos/tags"));
    }

}
