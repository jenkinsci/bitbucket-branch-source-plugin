/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket;

import java.net.HttpURLConnection;
import jenkins.model.Jenkins;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlPage;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;

class Security2467Test {

    @Issue("SECURITY-2467")
    @Test
    @WithJenkins
    void doFillRepositoryItemsWhenInvokedUsingGetMethodThenReturnMethodNotAllowed(JenkinsRule rule) throws Exception {
        String admin = "Admin";
        String projectName = "p";
        rule.jenkins.createProject(WorkflowMultiBranchProject.class, projectName);
        rule.jenkins.setSecurityRealm(rule.createDummySecurityRealm());
        rule.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
                grant(Jenkins.ADMINISTER).everywhere().to(admin));

        JenkinsRule.WebClient webClient = rule.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        webClient.login(admin);
        HtmlPage htmlPage = webClient.goTo("job/" + projectName +"/descriptorByName/com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource/fillRepositoryItems?serverUrl=http://hacker:9000&credentialsId=ID_Admin&repoOwner=admin");

        WebResponse webResponse = htmlPage.getWebResponse();
        assertThat(webResponse.getStatusCode()).isEqualTo(HttpURLConnection.HTTP_BAD_METHOD);
        assertThat(webResponse.getContentAsString()).contains("This URL requires POST");
    }
}
