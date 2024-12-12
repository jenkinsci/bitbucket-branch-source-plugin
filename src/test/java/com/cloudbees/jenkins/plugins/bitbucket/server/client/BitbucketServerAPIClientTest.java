package com.cloudbees.jenkins.plugins.bitbucket.server.client;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
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
import java.util.logging.Level;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BitbucketServerAPIClientTest {

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();
    @Rule
    public LoggerRule logger = new LoggerRule().record(BitbucketServerIntegrationClient.class, Level.FINE);

    @Test
    @WithoutJenkins
    public void verify_status_notitication_name_max_length() throws Exception {
        BitbucketApi client = BitbucketIntegrationClientFactory.getApiMockClient("https://acme.bitbucket.org");
        BitbucketBuildStatus status = new BitbucketBuildStatus();
        status.setName(RandomStringUtils.randomAlphanumeric(300));
        status.setState(Status.INPROGRESS);
        status.setHash("046d9a3c1532acf4cf08fe93235c00e4d673c1d3");

        client.postBuildStatus(status);

        IRequestAudit clientAudit = ((IRequestAudit) client).getAudit();
        HttpRequestBase request = extractRequest(clientAudit);
        assertThat(request).isNotNull()
            .isInstanceOf(HttpPost.class);
        try (InputStream content = ((HttpPost) request).getEntity().getContent()) {
            String json = IOUtils.toString(content, StandardCharsets.UTF_8);
            assertThatJson(json).node("name").isString().hasSize(255);
        }
    }

    private HttpRequestBase extractRequest(IRequestAudit clientAudit) {
        ArgumentCaptor<HttpRequestBase> captor = ArgumentCaptor.forClass(HttpRequestBase.class);
        verify(clientAudit).request(captor.capture());
        return captor.getValue();
    }

    @Test
    @WithoutJenkins
    public void verify_checkPathExists_given_a_path() throws Exception {
        BitbucketApi client = BitbucketIntegrationClientFactory.getApiMockClient("https://acme.bitbucket.org");
        assertThat(client.checkPathExists("feature/pipeline", "folder/Jenkinsfile")).isTrue();

        IRequestAudit clientAudit = ((IRequestAudit) client).getAudit();
        HttpRequestBase request = extractRequest(clientAudit);
        assertThat(request).isNotNull()
            .isInstanceOfSatisfying(HttpHead.class, head -> {
                    assertThat(head.getURI())
                        .hasPath("/rest/api/1.0/projects/amuniz/repos/test-repos/browse/folder/Jenkinsfile")
                        .hasQuery("at=feature/pipeline");
            });
    }

    @Test
    @WithoutJenkins
    public void verify_checkPathExists_given_file() throws Exception {
        BitbucketApi client = BitbucketIntegrationClientFactory.getApiMockClient("https://acme.bitbucket.org");
        assertThat(client.checkPathExists("feature/pipeline", "Jenkinsfile")).isTrue();

        IRequestAudit clientAudit = ((IRequestAudit) client).getAudit();
        HttpRequestBase request = extractRequest(clientAudit);
        assertThat(request).isNotNull()
            .isInstanceOfSatisfying(HttpHead.class, head ->
                assertThat(head.getURI()).hasPath("/rest/api/1.0/projects/amuniz/repos/test-repos/browse/Jenkinsfile"));
    }

    @Test
    public void filterArchivedRepositories() throws Exception {
        BitbucketApi client = BitbucketIntegrationClientFactory.getClient("localhost", "foo", "test-repos");
        List<? extends BitbucketRepository> repos = client.getRepositories();
        List<String> names = repos.stream().map(BitbucketRepository::getRepositoryName).toList();
        assertThat(names, not(hasItem("bar-archived")));
        assertThat(names, is(List.of("bar-active")));
    }

    @Test
    public void sortRepositoriesByName() throws Exception {
        BitbucketApi client = BitbucketIntegrationClientFactory.getClient("localhost", "amuniz", "test-repos");
        List<? extends BitbucketRepository> repos = client.getRepositories();
        List<String> names = repos.stream().map(BitbucketRepository::getRepositoryName).toList();
        assertThat(names, is(List.of("another-repo", "dogs-repo", "test-repos")));
    }

    @Test
    public void disableCookieManager() throws Exception {
        try (MockedStatic<HttpClientBuilder> staticHttpClientBuilder = mockStatic(HttpClientBuilder.class)) {
            HttpClientBuilder httpClientBuilder = mock(HttpClientBuilder.class, RETURNS_SELF);
            staticHttpClientBuilder.when(HttpClientBuilder::create).thenReturn(httpClientBuilder);
            CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
            when(httpClientBuilder.build()).thenReturn(httpClient);
            BitbucketApi client = BitbucketIntegrationClientFactory.getClient("localhost", "amuniz", "test-repos");
            client.getRepositories();
            verify(httpClientBuilder).disableCookieManagement();
        }
    }
}
