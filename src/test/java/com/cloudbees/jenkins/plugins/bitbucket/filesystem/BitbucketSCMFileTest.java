/*
 * The MIT License
 *
 * Copyright (c) 2025, Nikolas Falco
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
package com.cloudbees.jenkins.plugins.bitbucket.filesystem;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory;
import java.io.FileNotFoundException;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFile.Type;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class BitbucketSCMFileTest {

    @SuppressWarnings("unused")
    private static JenkinsRule j;

    @Issue("JENKINS-75157")
    @Test
    void verify_content_throws_FileNotFoundException_when_file_does_not_exists() {
        BitbucketApi client = BitbucketIntegrationClientFactory.getApiMockClient("https://acme.bitbucket.com");

        BitbucketSCMFile parent = new BitbucketSCMFile(client, "master", "51af66dad14c38d7c69874c57a65f77f688e3f33");
        BitbucketSCMFile file = new BitbucketSCMFile(parent, "pipeline_config.groovy", Type.REGULAR_FILE, "046d9a3c1532acf4cf08fe93235c00e4d673c1d2");
        assertThatThrownBy(file::content).isInstanceOf(FileNotFoundException.class);
    }

    @Issue("JENKINS-75208")
    @Test
    void test_SCMFile_does_not_contains_illegal_chars_in_the_name() throws Exception {
        BitbucketApi client = BitbucketIntegrationClientFactory.getApiMockClient("https://acme.bitbucket.com");

        BitbucketSCMFile parent = new BitbucketSCMFile(client, "feature/pipeline", null);
        BitbucketSCMFile file = new BitbucketSCMFile(parent, "Jenkinsfile", null, "2c130d767a38ac4ef511797f221315f35a2aea55");
        SCMFile scmFile = assertDoesNotThrow(() -> client.getFile(file));

        assertThat(scmFile.isFile()).isTrue();
        assertThat(scmFile.getName()).isEqualTo("Jenkinsfile");
        assertThat(scmFile).isInstanceOfSatisfying(BitbucketSCMFile.class,
                f -> {
                    assertThat(f.getRef()).isEqualTo("feature/pipeline");
                    assertThat(f.getHash()).isEqualTo("2c130d767a38ac4ef511797f221315f35a2aea55");
                });
    }

    @Issue("JENKINS-75208")
    @Test
    void test_SCMFile_when_client_return_path_attribute_with_folder_separator_on_cloud() throws Exception {
        BitbucketApi client = BitbucketIntegrationClientFactory.getApiMockClient("https://bitbucket.org");

        BitbucketSCMFile root = new BitbucketSCMFile(client, "master", null);
        SCMFile file = root.child("folder/file.properties");
        String content = file.contentAsString();
        assertThat(content).isEqualTo("message=This is a test for metadata");
    }

    @Issue("JENKINS-75157")
    @Test
    void test_SCMBinder_behavior_when_discover_the_Jenkinsfile_for_a_given_branch_on_cloud() throws Exception {
        BitbucketApi client = BitbucketIntegrationClientFactory.getApiMockClient("https://bitbucket.org");

        BitbucketSCMFile root = new BitbucketSCMFile(client, "feature/BB1", "fb522a6f08c7c7df337312e4e65ec1b57710672e");
        SCMFile jenkinsfile = root.child("script.bat");
        assertThat(jenkinsfile.content()).hasContent("@echo off\necho \"Hello world\"");
    }
}
