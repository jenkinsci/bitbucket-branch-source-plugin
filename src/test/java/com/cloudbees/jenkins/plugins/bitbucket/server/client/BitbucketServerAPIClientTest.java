package com.cloudbees.jenkins.plugins.bitbucket.server.client;

import com.damnhandy.uri.template.UriTemplate;
import org.junit.Assert;
import org.junit.Test;

import static com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerAPIClient.API_BROWSE_PATH;

public class BitbucketServerAPIClientTest {

    @Test
    public void repoBrowsePathFolder() {
        String expand = UriTemplate
            .fromTemplate(API_BROWSE_PATH)
            .set("owner", "test")
            .set("repo", "test")
            .set("path", "folder/Jenkinsfile")
            .set("at", "fix/test")
            .expand();
        Assert.assertEquals("/rest/api/1.0/projects/test/repos/test/browse/folder/Jenkinsfile?at=fix%2Ftest", expand);
    }

    @Test
    public void repoBrowsePathFile() {
        String expand = UriTemplate
            .fromTemplate(API_BROWSE_PATH)
            .set("owner", "test")
            .set("repo", "test")
            .set("path", "Jenkinsfile")
            .expand();
        Assert.assertEquals("/rest/api/1.0/projects/test/repos/test/browse/Jenkinsfile", expand);
    }

}
