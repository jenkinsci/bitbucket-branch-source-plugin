package com.cloudbees.jenkins.plugins.bitbucket.server.client;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory.BitbucketServerIntegrationClient;
import com.damnhandy.uri.template.UriTemplate;
import com.damnhandy.uri.template.impl.Operator;
import java.util.List;
import java.util.logging.Level;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.mockito.MockedStatic;

import static com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerAPIClient.API_BROWSE_PATH;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BitbucketServerAPIClientTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();
    @Rule
    public LoggerRule logger = new LoggerRule().record(BitbucketServerIntegrationClient.class, Level.FINE);

    @Test
    @WithoutJenkins
    public void repoBrowsePathFolder() {
        String expand = UriTemplate
            .fromTemplate(API_BROWSE_PATH)
            .set("owner", "test")
            .set("repo", "test")
            .set("path", "folder/Jenkinsfile".split(Operator.PATH.getSeparator()))
            .set("at", "fix/test")
            .expand();
        Assert.assertEquals("/rest/api/1.0/projects/test/repos/test/browse/folder/Jenkinsfile?at=fix%2Ftest", expand);
    }

    @Test
    @WithoutJenkins
    public void repoBrowsePathFile() {
        String expand = UriTemplate
            .fromTemplate(API_BROWSE_PATH)
            .set("owner", "test")
            .set("repo", "test")
            .set("path", "Jenkinsfile".split(Operator.PATH.getSeparator()))
            .expand();
        Assert.assertEquals("/rest/api/1.0/projects/test/repos/test/browse/Jenkinsfile", expand);
    }

    @Test
    public void retryWhenRateLimited() throws Exception {
        logger.capture(50);
        BitbucketApi client = BitbucketIntegrationClientFactory.getClient("localhost", "amuniz", "test-repos");
        ((BitbucketServerIntegrationClient)client).rateLimitNextRequest();
        assertThat(client.getRepository().getProject().getKey(), equalTo("AMUNIZ"));
        assertThat(logger.getMessages(), hasItem(containsString("Bitbucket server API rate limit reached")));
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
        try(MockedStatic<HttpClientBuilder> staticHttpClientBuilder = mockStatic(HttpClientBuilder.class)) {
            HttpClientBuilder httpClientBuilder = mock(HttpClientBuilder.class);
            CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
            staticHttpClientBuilder.when(HttpClientBuilder::create).thenReturn(httpClientBuilder);
            when(httpClientBuilder.build()).thenReturn(httpClient);
            BitbucketApi client = BitbucketIntegrationClientFactory.getClient("localhost", "amuniz", "test-repos");
            client.getRepositories();
            verify(httpClientBuilder).disableCookieManagement();
        }
    }
}
