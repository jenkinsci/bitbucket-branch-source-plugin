package com.cloudbees.jenkins.plugins.bitbucket.filesystem;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory;
import java.io.FileNotFoundException;
import jenkins.scm.api.SCMFile.Type;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class BitbucketSCMFileTest {

    @WithJenkins
    @Issue("JENKINS-75157")
    @Test
    void test(JenkinsRule r) {
        BitbucketApi client = BitbucketIntegrationClientFactory.getApiMockClient("https://acme.bitbucket.com");

        BitbucketSCMFile parent = new BitbucketSCMFile(mock(BitbucketSCMFileSystem.class), client, "ref", "hash");
        BitbucketSCMFile file = new BitbucketSCMFile(parent, "pipeline_config.groovy", Type.REGULAR_FILE, "046d9a3c1532acf4cf08fe93235c00e4d673c1d2");
        assertThatThrownBy(file::content).isInstanceOf(FileNotFoundException.class);
    }
}
