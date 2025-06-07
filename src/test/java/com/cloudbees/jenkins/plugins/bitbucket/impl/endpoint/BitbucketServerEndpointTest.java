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

import com.cloudbees.jenkins.plugins.bitbucket.impl.util.URLUtils;
import hudson.Util;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;

class BitbucketServerEndpointTest {

    @Test
    void smokes() {
        BitbucketServerEndpoint endpoint = new BitbucketServerEndpoint("Dummy", "http://dummy.example.com", false, null, false, null);

        assertThat(endpoint.getDisplayName()).isEqualTo("Dummy");
        assertThat(endpoint.getServerUrl()).isEqualTo("http://dummy.example.com");
        assertThat(endpoint.getServerURL()).isEqualTo("http://dummy.example.com");

        /* The endpoints should set (literally, not normalized) and return
         * the bitbucketJenkinsRootUrl if the management of hooks is enabled */
        assertThat(endpoint.getBitbucketJenkinsRootUrl()).isNull();
        endpoint.setBitbucketJenkinsRootUrl("http://jenkins:8080");
        assertThat(endpoint.getBitbucketJenkinsRootUrl()).isNull();

        // No credentials - webhook still not managed, even with a checkbox
        endpoint = new BitbucketServerEndpoint("Dummy", "http://dummy.example.com", true, null, false, null);
        endpoint.setBitbucketJenkinsRootUrl("http://jenkins:8080");
        assertThat(endpoint.getBitbucketJenkinsRootUrl()).isNull();

        // With flag and with credentials, the hook is managed.
        // getBitbucketJenkinsRootUrl() is verbatim what we set
        // getEndpointJenkinsRootUrl() is normalized and ends with a slash
        endpoint = new BitbucketServerEndpoint("Dummy", "http://dummy.example.com", true, "{credid}", false, null);
        endpoint.setBitbucketJenkinsRootUrl("http://jenkins:8080");
        assertThat(endpoint.getBitbucketJenkinsRootUrl()).isEqualTo("http://jenkins:8080/");
        assertThat(endpoint.getEndpointJenkinsRootUrl()).isEqualTo("http://jenkins:8080/");
        assertThat(endpoint.getEndpointJenkinsRootURL()).isEqualTo("http://jenkins:8080/");

        // Make sure several invokations with same arguments do not conflict:
        endpoint.setBitbucketJenkinsRootUrl("https://jenkins:443/");
        assertThat(endpoint.getBitbucketJenkinsRootUrl()).isEqualTo("https://jenkins/");
        assertThat(endpoint.getEndpointJenkinsRootUrl()).isEqualTo("https://jenkins/");
        assertThat(endpoint.getEndpointJenkinsRootURL()).isEqualTo("https://jenkins/");
    }

    @WithJenkins
    @Test
    void getUnmanagedDefaultRootUrl(JenkinsRule rule) {
        String jenkinsRootURL = Util.ensureEndsWith(URLUtils.normalizeURL(Jenkins.get().getRootUrl()), "/");
        assertThat(new BitbucketServerEndpoint("Dummy", "http://dummy.example.com", true, null, false, null).getEndpointJenkinsRootUrl())
            .isEqualTo(jenkinsRootURL);
        assertThat(new BitbucketServerEndpoint("Dummy", "http://dummy.example.com", false, "{cred}", false, null).getEndpointJenkinsRootURL())
            .isEqualTo(jenkinsRootURL);
    }

    @Test
    void getRepositoryUrl() {
        BitbucketServerEndpoint endpoint = new BitbucketServerEndpoint("Dummy", "http://dummy.example.com", false, null, false, null);

        assertThat(endpoint.getRepositoryUrl("TST", "test-repo")).isEqualTo("http://dummy.example.com/projects/TST/repos/test-repo");
        assertThat(endpoint.getRepositoryUrl("~tester", "test-repo")).isEqualTo("http://dummy.example.com/users/tester/repos/test-repo");
    }

    @Test
    void given__badUrl__when__check__then__fail() {
        assertThat(BitbucketServerEndpoint.DescriptorImpl.doCheckServerUrl("").kind).isEqualTo(FormValidation.Kind.ERROR);
    }

    @Test
    void given__goodUrl__when__check__then__ok() {
        assertThat(BitbucketServerEndpoint.DescriptorImpl.doCheckServerUrl("http://dummy.example.com").kind).isEqualTo(FormValidation.Kind.OK);
    }
}
