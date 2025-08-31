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

import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AccessDeniedException3;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.net.HttpURLConnection;
import jenkins.model.Jenkins;
import org.htmlunit.Page;
import org.htmlunit.WebResponse;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WithJenkins
class Security2033Test {

    private static final String PROJECT_NAME = "p";
    private static final String NOT_AUTHORIZED_USER = "userNoPermission";
    private static final String NO_ITEM_READ_USER = "userNoReadPermission";
    private static final String SERVER_URL = "server.url";

    private JenkinsRule j;

    private WorkflowMultiBranchProject pr;

    @BeforeEach
    void setup(JenkinsRule rule) throws Exception {
        j = rule;
        pr = j.jenkins.createProject(WorkflowMultiBranchProject.class, PROJECT_NAME);
        setUpAuthorization();
        initCredentials();
    }

    @Issue("SECURITY-2033")
    @Test
    void doFillCredentialsIdItemsSCMSourceWhenUserWithoutCredentialsViewPermissionThenListNotPopulated() {
        BitbucketSCMSource.DescriptorImpl descriptor = (BitbucketSCMSource.DescriptorImpl) Jenkins.get().getDescriptorOrDie(BitbucketSCMSource.class);
        try (ACLContext aclContext = ACL.as(User.getOrCreateByIdOrFullName(NOT_AUTHORIZED_USER))) {
            ListBoxModel actual = descriptor.doFillCredentialsIdItems(pr, SERVER_URL);
            ListBoxModel expected = new ListBoxModel(new ListBoxModel.Option("- none -", ""));
            assertListBoxModel(actual, expected);
        }
    }

    @Issue("SECURITY-2033")
    @Test
    void doFillCredentialsIdItemsSCMNavigatorWhenUserWithoutCredentialsViewPermissionThenListNotPopulated() {
        BitbucketSCMNavigator.DescriptorImpl descriptor = (BitbucketSCMNavigator.DescriptorImpl) Jenkins.get().getDescriptorOrDie(BitbucketSCMNavigator.class);
        try (ACLContext aclContext = ACL.as(User.getOrCreateByIdOrFullName(NOT_AUTHORIZED_USER))) {
            ListBoxModel actual = descriptor.doFillCredentialsIdItems(pr, SERVER_URL);
            ListBoxModel expected = new ListBoxModel(new ListBoxModel.Option("- none -", ""));
            assertListBoxModel(actual, expected);
        }
    }

    @Issue("SECURITY-2033")
    @Test
    void doCheckCredentialsIdSCMNavigatorWhenUserWithoutCredentialsViewPermissionThenReturnForbiddenStatus() {
        try (ACLContext aclContext = ACL.as(User.getOrCreateByIdOrFullName(NOT_AUTHORIZED_USER))) {
            assertThatThrownBy(() -> BitbucketSCMNavigator.DescriptorImpl.doCheckCredentialsId(pr, SERVER_URL, "nonEmpty"))
                .isInstanceOf(AccessDeniedException3.class)
                .hasMessage(NOT_AUTHORIZED_USER + " is missing the Credentials/View permission");
        }
    }

    @Issue("SECURITY-2033")
    @Test
    void doCheckCredentialsIdSCMSourceWhenUserWithoutCredentialsViewPermissionThenReturnForbiddenStatus() {
        BitbucketSCMSource.DescriptorImpl descriptor = (BitbucketSCMSource.DescriptorImpl) Jenkins.get().getDescriptorOrDie(BitbucketSCMSource.class);
        try (ACLContext aclContext = ACL.as(User.getOrCreateByIdOrFullName(NOT_AUTHORIZED_USER))) {
            assertThatThrownBy(() -> descriptor.doCheckCredentialsId(pr, SERVER_URL, "nonEmpty"))
                .isInstanceOf(AccessDeniedException3.class)
                .hasMessage(NOT_AUTHORIZED_USER + " is missing the Credentials/View permission");
        }
    }

    @Issue("SECURITY-2033")
    @Test
    void doCheckServerUrlWhenUserWithoutPermissionThenReturnForbiddenMessage() {
        ((MockAuthorizationStrategy) j.jenkins.getAuthorizationStrategy())
            .grant(Jenkins.READ, Item.READ).everywhere().to(NOT_AUTHORIZED_USER);
        try (ACLContext aclContext = ACL.as(User.getOrCreateByIdOrFullName(NO_ITEM_READ_USER))) {
            FormValidation formValidation = BitbucketSCMSource.DescriptorImpl.doCheckServerUrl(pr, SERVER_URL);
            assertThat(formValidation.getMessage()).isEqualTo("Unauthorized to validate Server URL");
        }
    }

    @Issue("SECURITY-2033")
    @Test
    void doFillServerUrlItemsSCMNavigatorWhenUserWithoutPermissionThenReturnEmptyList() {
        BitbucketSCMNavigator.DescriptorImpl descriptor = (BitbucketSCMNavigator.DescriptorImpl) Jenkins.get().getDescriptorOrDie(BitbucketSCMNavigator.class);
        try (ACLContext aclContext = ACL.as(User.getOrCreateByIdOrFullName(NOT_AUTHORIZED_USER))) {
            ListBoxModel actual = descriptor.doFillServerUrlItems(pr);
            assertThat(actual).isEmpty();
        }
    }

    @Issue("SECURITY-2033")
    @Test
    void doFillServerUrlItemsSCMSourceWhenUserWithoutPermissionThenReturnEmptyList() {
        BitbucketSCMSource.DescriptorImpl descriptor = (BitbucketSCMSource.DescriptorImpl) Jenkins.get().getDescriptorOrDie(BitbucketSCMSource.class);
        try (ACLContext aclContext = ACL.as(User.getOrCreateByIdOrFullName(NOT_AUTHORIZED_USER))) {
            ListBoxModel actual = descriptor.doFillServerUrlItems(pr);
            assertThat(actual).isEmpty();
        }
    }

    @Issue("SECURITY-2033")
    @Test
    void doShowStatsWhenUserWithoutAdminPermissionThenReturnForbiddenStatus() {
        BitbucketCloudEndpoint.DescriptorImpl descriptor = (BitbucketCloudEndpoint.DescriptorImpl) Jenkins.get().getDescriptorOrDie(BitbucketCloudEndpoint.class);
        try (ACLContext aclContext = ACL.as(User.getOrCreateByIdOrFullName(NOT_AUTHORIZED_USER))) {
            assertThatThrownBy(() -> descriptor.doShowStats())
                .isInstanceOf(AccessDeniedException3.class)
                .hasMessage(NOT_AUTHORIZED_USER + " is missing the Overall/Administer permission");
        }
    }

    @Issue("SECURITY-2033")
    @Test
    void doClearWhenUserWithoutAdminPermissionThenReturnForbiddenStatus() {
        BitbucketCloudEndpoint.DescriptorImpl descriptor = (BitbucketCloudEndpoint.DescriptorImpl) Jenkins.get().getDescriptorOrDie(BitbucketCloudEndpoint.class);
        try (ACLContext aclContext = ACL.as(User.getOrCreateByIdOrFullName(NOT_AUTHORIZED_USER))) {
            assertThatThrownBy(() -> descriptor.doClear())
                .isInstanceOf(AccessDeniedException3.class)
                .hasMessage(NOT_AUTHORIZED_USER + " is missing the Overall/Administer permission");
        }
    }

    @Issue("SECURITY-2033")
    @Test
    void doClearWhenInvokedUsingGetMethodThenResourceNotFound() throws Exception {
        JenkinsRule.WebClient webClient = j .createWebClient().withThrowExceptionOnFailingStatusCode(false);
        webClient.login(NOT_AUTHORIZED_USER);
        Page page = webClient.goTo("job/" + PROJECT_NAME +"/descriptorByName/com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint/clear");

        WebResponse webResponse = page.getWebResponse();
        assertThat(webResponse.getStatusCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
        assertThat(webResponse.getContentAsString())
            .contains("Stapler processed this HTTP request as follows, but couldn't find the resource to consume the request");
    }

    private void initCredentials() throws Exception {
        StandardUsernamePasswordCredentials key = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "id", "desc", "username", "pass");
        SystemCredentialsProvider.getInstance().getCredentials().add(key);

        SystemCredentialsProvider.getInstance().save();
    }

    private void setUpAuthorization() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
            .grant(Jenkins.READ, Item.READ).everywhere().to(NOT_AUTHORIZED_USER));
    }

    private static void assertListBoxModel(ListBoxModel actual, ListBoxModel expected) {
        assertThat(actual).isNotEmpty();
        assertThat(actual).hasSize(expected.size());
        assertThat(actual.get(0).name).isEqualTo(expected.get(0).name);
        assertThat(actual.get(0).value).isEqualTo(expected.get(0).value);
    }
}
