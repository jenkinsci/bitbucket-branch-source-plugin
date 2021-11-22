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
package com.cloudbees.jenkins.plugins.bitbucket.endpoints;

import com.damnhandy.uri.template.UriTemplate;
import jenkins.model.Jenkins;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class BitbucketCloudEndpointTest {

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    private static final String V2_API_BASE_URL = "https://api.bitbucket.org/2.0/repositories";
    private static final String V2_TEAMS_API_BASE_URL = "https://api.bitbucket.org/2.0/teams/";

    @Test
    public void smokes() {
        BitbucketCloudEndpoint endpoint1 = new BitbucketCloudEndpoint(false, null);

        assertThat(endpoint1.getDisplayName(), notNullValue());
        assertThat(endpoint1.getServerUrl(), is(BitbucketCloudEndpoint.SERVER_URL));

        /* The endpoints should set (literally, not normalized) and return
         * the bitbucketJenkinsRootUrl if the management of hooks is enabled */
        assertThat(new BitbucketCloudEndpoint(false, null).getBitbucketJenkinsRootUrl(), nullValue());
        assertThat(new BitbucketCloudEndpoint(false, null, "http://jenkins:8080").getBitbucketJenkinsRootUrl(), nullValue());
        // No credentials - webhook still not managed, even with a checkbox
        assertThat(new BitbucketCloudEndpoint(true,  null, "http://jenkins:8080").getBitbucketJenkinsRootUrl(), nullValue());

        // With flag and with credentials, the hook is managed.
        // getBitbucketJenkinsRootUrl() is verbatim what we set
        // getEndpointJenkinsRootUrl() is normalized and ends with a slash
        BitbucketCloudEndpoint endpoint2 = new BitbucketCloudEndpoint(true,  "{credid}", "http://jenkins:8080");
        assertThat(endpoint2.getBitbucketJenkinsRootUrl(), is("http://jenkins:8080/"));
        assertThat(endpoint2.getEndpointJenkinsRootUrl(), is("http://jenkins:8080/"));

        // Make sure several invokations with same arguments do not conflict:
        assertThat(new BitbucketCloudEndpoint(true,  "{credid}", "https://jenkins:443/").getBitbucketJenkinsRootUrl(), is("https://jenkins/"));
        assertThat(new BitbucketCloudEndpoint(true,  "{credid}", "https://jenkins:443/").getEndpointJenkinsRootUrl(), is("https://jenkins/"));
    }

    @Test
    public void getUnmanagedDefaultRootUrl() {
        assertThat(new BitbucketCloudEndpoint(true,  null).getEndpointJenkinsRootUrl(),
                is(AbstractBitbucketEndpoint.normalizeJenkinsRootUrl(Jenkins.get().getRootUrl())));
        assertThat(new BitbucketCloudEndpoint(false, "{cred}").getEndpointJenkinsRootUrl(),
                is(AbstractBitbucketEndpoint.normalizeJenkinsRootUrl(Jenkins.get().getRootUrl())));
    }

    @Test
    public void getRepositoryUrl() {
        BitbucketCloudEndpoint endpoint = new BitbucketCloudEndpoint(false, null);

        assertThat(endpoint.getRepositoryUrl("tester", "test-repo"), is("https://bitbucket.org/tester/test-repo"));
    }

    @Test
    public void repositoryTemplate() {
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
        assertThat(url, is(betterUrl));
    }
}
