/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint;

import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.server.ServerWebhookConfiguration;
import hudson.util.FormValidation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;

@WithJenkins
class BitbucketServerEndpointTest {

    private static JenkinsRule rule;

    @BeforeAll
    static void init(JenkinsRule j) {
        rule = j;
    }

    @Test
    void getUnmanagedDefaultRootUrl() {
        BitbucketServerEndpoint endpoint = new BitbucketServerEndpoint("Dummy", "http://dummy.example.com", new ServerWebhookConfiguration(false, "{cred}", false, null));

        assertThat(endpoint.getEndpointJenkinsRootURL()).isEqualTo(BitbucketWebhookConfiguration.getDefaultJenkinsRootURL());
    }

    @Test
    void getRepositoryUrl() {
        BitbucketServerEndpoint endpoint = new BitbucketServerEndpoint("Dummy", "http://dummy.example.com");

        assertThat(endpoint.getRepositoryURL("TST", "test-repo")).isEqualTo("http://dummy.example.com/projects/TST/repos/test-repo");
        assertThat(endpoint.getRepositoryURL("~tester", "test-repo")).isEqualTo("http://dummy.example.com/users/tester/repos/test-repo");
    }

    @Test
    void given__badUrl__when__check__then__fail() {
        assertThat(new BitbucketServerEndpoint.DescriptorImpl().doCheckServerURL("").kind).isEqualTo(FormValidation.Kind.ERROR);
    }

    @Test
    void given__goodUrl__when__check__then__ok() {
        assertThat(new BitbucketServerEndpoint.DescriptorImpl().doCheckServerURL("http://dummy.example.com").kind).isEqualTo(FormValidation.Kind.OK);
    }
}
