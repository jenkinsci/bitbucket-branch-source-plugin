package com.cloudbees.jenkins.plugins.bitbucket.server.client;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory.BitbucketServerIntegrationClient;
import com.damnhandy.uri.template.UriTemplate;
import com.damnhandy.uri.template.impl.Operator;
import java.util.logging.Level;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.WithoutJenkins;

import static com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerAPIClient.API_BROWSE_PATH;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

public class BitbucketServerAPIClientTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();
    @Rule
    public LoggerRule logger = new LoggerRule().record(BitbucketServerAPIClient.class, Level.FINE);

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

}
