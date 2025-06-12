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
import com.damnhandy.uri.template.UriTemplate;
import hudson.Util;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;

class BitbucketCloudEndpointTest {

    private static final String V2_API_BASE_URL = "https://api.bitbucket.org/2.0/repositories";

    @Test
    void smokes() {
        BitbucketCloudEndpoint endpoint = new BitbucketCloudEndpoint();

        assertThat(endpoint.getDisplayName()).isNotNull();
        assertThat(endpoint.getServerUrl()).isEqualTo(BitbucketCloudEndpoint.SERVER_URL);

        /* The endpoints should set (literally, not normalized) and return
         * the bitbucketJenkinsRootUrl if the management of hooks is enabled */
        assertThat(endpoint.getBitbucketJenkinsRootUrl()).isNull();

        endpoint.setBitbucketJenkinsRootUrl("http://jenkins:8080");
        assertThat(endpoint.getBitbucketJenkinsRootUrl()).isNull();

        // No credentials - webhook still not managed, even with a checkbox
        endpoint = new BitbucketCloudEndpoint(false, 0, 0, true, null, false, null);
        endpoint.setBitbucketJenkinsRootUrl("http://jenkins:8080");
        assertThat(endpoint.getBitbucketJenkinsRootUrl()).isNull();

        // With flag and with credentials, the hook is managed.
        // getBitbucketJenkinsRootUrl() is verbatim what we set
        // getEndpointJenkinsRootUrl() is normalized and ends with a slash
        endpoint = new BitbucketCloudEndpoint(false, 0, 0, true, "{credid}", false, null);
        endpoint.setBitbucketJenkinsRootUrl("http://jenkins:8080");
        assertThat(endpoint.getBitbucketJenkinsRootUrl()).isEqualTo("http://jenkins:8080/");
        assertThat(endpoint.getEndpointJenkinsRootUrl()).isEqualTo("http://jenkins:8080/");

        // Make sure several invokations with same arguments do not conflict:
        endpoint = new BitbucketCloudEndpoint(false, 0, 0, true, "{credid}", false, null);
        endpoint.setBitbucketJenkinsRootUrl("https://jenkins:443/");
        assertThat(endpoint.getBitbucketJenkinsRootUrl()).isEqualTo("https://jenkins/");
        assertThat(endpoint.getEndpointJenkinsRootUrl()).isEqualTo("https://jenkins/");
    }

    @WithJenkins
    @Test
    void getUnmanagedDefaultRootUrl(JenkinsRule rule) {
        String jenkinsRootURL = Util.ensureEndsWith(URLUtils.normalizeURL(Jenkins.get().getRootUrl()), "/");
        assertThat(new BitbucketCloudEndpoint().getEndpointJenkinsRootUrl())
            .isEqualTo(jenkinsRootURL);
        assertThat(new BitbucketCloudEndpoint(false, 0, 0, false, "{cred}", false, null).getEndpointJenkinsRootURL())
            .isEqualTo(jenkinsRootURL);
    }

    @Test
    void getRepositoryUrl() {
        BitbucketCloudEndpoint endpoint = new BitbucketCloudEndpoint();

        assertThat(endpoint.getRepositoryUrl("tester", "test-repo")).isEqualTo("https://bitbucket.org/tester/test-repo");
    }

    @Test
    void repositoryTemplate() {
        String owner = "bob";
        String repositoryName = "yetAnotherRepo";
        UriTemplate template = UriTemplate
                .buildFromTemplate("{+base}")
                .path("owner", "repo")
                .literal("/pullrequests")
                .query("page", "pagelen")
                .build();
        String urlTemplate = V2_API_BASE_URL + "/" + owner + "/" + repositoryName + "/pullrequests?page=%d&pagelen=50";
        int page = 1;
        String url = String.format(urlTemplate, page);
        String betterUrl = template
                .set("base", V2_API_BASE_URL)
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("page", page)
                .set("pagelen", 50)
                .expand();
        assertThat(url).isEqualTo(betterUrl);
    }
}
