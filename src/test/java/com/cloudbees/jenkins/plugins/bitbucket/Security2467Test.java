package com.cloudbees.jenkins.plugins.bitbucket;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import java.net.HttpURLConnection;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

public class Security2467Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Issue("SECURITY-2467")
    @Test
    public void doFillRepositoryItemsWhenInvokedUsingGetMethodThenReturnMethodNotAllowed() throws Exception {
        String admin = "Admin";
        String projectName = "p";
        WorkflowMultiBranchProject pr = j.jenkins.createProject(WorkflowMultiBranchProject.class, projectName);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
                grant(Jenkins.ADMINISTER).everywhere().to(admin));

        JenkinsRule.WebClient webClient = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        webClient.login(admin);
        HtmlPage htmlPage = webClient.goTo("job/" + projectName +"/descriptorByName/com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource/fillRepositoryItems?serverUrl=http://hacker:9000&credentialsId=ID_Admin&repoOwner=admin");

        assertThat(htmlPage.getWebResponse().getStatusCode(), is(HttpURLConnection.HTTP_BAD_METHOD));
        assertThat(htmlPage.getWebResponse().getContentAsString(), containsString("This URL requires POST"));
    }
}
