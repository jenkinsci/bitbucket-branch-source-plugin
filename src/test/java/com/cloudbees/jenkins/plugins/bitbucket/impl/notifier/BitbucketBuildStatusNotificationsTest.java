/*
 * The MIT License
 *
 * Copyright (c) 2024, Nikolas Falco, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket.impl.notifier;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.BranchSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMRevision;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticatedClient;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus.Status;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketMockApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.api.PullRequestBranchType;
import com.cloudbees.jenkins.plugins.bitbucket.api.buildstatus.BitbucketBuildStatusCustomizer;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpointProvider;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.EndpointType;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketServerEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.notifier.BitbucketBuildStatusNotifications.JobCheckoutListener;
import com.cloudbees.jenkins.plugins.bitbucket.impl.notifier.BitbucketBuildStatusNotifications.JobCompletedListener;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketApiUtils;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.cloud.CloudWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.server.ServerWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerAPIClient;
import com.cloudbees.jenkins.plugins.bitbucket.trait.BitbucketBuildStatusNotificationsTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.ForkPullRequestDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.ForkPullRequestDiscoveryTrait.TrustEveryone;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StreamBuildListener;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import jenkins.branch.Branch;
import jenkins.branch.BranchProjectFactory;
import jenkins.branch.BranchSource;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.plugins.git.AbstractGitSCMSource.SCMRevisionImpl;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMSourceTrait;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import org.apache.commons.codec.digest.DigestUtils;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.ArgumentCaptor;

import static com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory.getApiMockClient;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WithJenkins
class BitbucketBuildStatusNotificationsTest {
    @TestExtension
    public static class BuildStatusTestCustomizer implements BitbucketBuildStatusCustomizer {
        @Override
        public void customize(Run<?, ?> build, BitbucketBuildStatus buildStatus) {
            if (buildStatus.getState() != null && buildStatus.getState() != Status.INPROGRESS) {
                buildStatus.addOptionalData("testResults", new TestResults(5, 2, 1));
            }
        }
        @Override
        public boolean isApplicable(EndpointType type) {
            return type == EndpointType.SERVER;
        }
    }

    @ParameterizedTest(name = "When build result is {1} expect to notify status {2}")
    @MethodSource("buildStatusProvider")
    void test_status_notification_for_given_build_result(UnaryOperator<BitbucketBuildStatusNotificationsTrait> traitCustomizer,
                                                         Result buildResult,
                                                         Status expectedStatus,
                                                         BitbucketApi apiClient,
                                                         @NonNull JenkinsRule r) throws Exception {
        StreamBuildListener taskListener = new StreamBuildListener(System.out, StandardCharsets.UTF_8);
        URL localJenkinsURL = new URL("http://example.com:" + r.getURL().getPort() + r.contextPath + "/");
        JenkinsLocationConfiguration.get().setUrl(localJenkinsURL.toString());

        String serverURL = BitbucketCloudEndpoint.SERVER_URL;

        BitbucketBuildStatusNotificationsTrait trait = traitCustomizer.apply(new BitbucketBuildStatusNotificationsTrait());
        WorkflowRun build = prepareBuildForNotification(r, trait, serverURL);
        doReturn(buildResult).when(build).getResult();

        FilePath workspace = r.jenkins.getWorkspaceFor(build.getParent());

        BitbucketAuthenticatedClient client = mock(BitbucketAuthenticatedClient.class);
        when(apiClient.adapt(BitbucketAuthenticatedClient.class)).thenReturn(client);
        BitbucketMockApiFactory.add(serverURL, apiClient);

        JobCheckoutListener listener = new JobCheckoutListener();
        listener.onCheckout(build, null, workspace, taskListener, null, SCMRevisionState.NONE);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(client).post(anyString(), captor.capture());
        assertThatJson(captor.getValue()).isObject().containsEntry("state", expectedStatus.toString());
    }

    @Issue("JENKINS-76027")
    @Test
    void test_server_customizer(@NonNull JenkinsRule r) throws Exception {
        StreamBuildListener taskListener = new StreamBuildListener(System.out, StandardCharsets.UTF_8);
        URL jenkinsURL = new URL("http://example.com:" + r.getURL().getPort() + r.contextPath + "/");
        JenkinsLocationConfiguration.get().setUrl(jenkinsURL.toString());

        String serverURL = "https://acme.bitbucket.org";
        BitbucketEndpointProvider.registerEndpoint("JENKINS-76027", serverURL, null);

        BitbucketBuildStatusNotificationsTrait trait = new BitbucketBuildStatusNotificationsTrait();
        trait.setUseReadableNotificationIds(true);
        WorkflowRun build = prepareBuildForNotification(r, trait, serverURL);
        doReturn(Result.SUCCESS).when(build).getResult();

        BitbucketAuthenticatedClient client = buildAuthenticatedClient(serverURL);

        JobCompletedListener listener = new JobCompletedListener();
        listener.onCompleted(build, taskListener);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(client).post(endsWith("/rest/api/1.0/projects/repoOwner/repos/repository/commits/c341232342311/builds"), captor.capture());
        assertThatJson(captor.getValue())
            .isObject()
            .containsEntry("testResults", JsonAssertions.json("{\"successful\":5,\"failed\":2,\"skipped\":1}"));
    }

    @Issue("JENKINS-72780")
    @Test
    void test_status_notification_name_when_UseReadableNotificationIds_is_true(@NonNull JenkinsRule r) throws Exception {
        StreamBuildListener taskListener = new StreamBuildListener(System.out, StandardCharsets.UTF_8);
        URL jenkinsURL = new URL("http://example.com:" + r.getURL().getPort() + r.contextPath + "/");
        JenkinsLocationConfiguration.get().setUrl(jenkinsURL.toString());

        String serverURL = "https://acme.bitbucket.org";
        BitbucketEndpointProvider.registerEndpoint("JENKINS-72780", serverURL, null);

        BitbucketBuildStatusNotificationsTrait trait = new BitbucketBuildStatusNotificationsTrait();
        trait.setUseReadableNotificationIds(true);
        WorkflowRun build = prepareBuildForNotification(r, trait, serverURL);
        doReturn(Result.SUCCESS).when(build).getResult();

        FilePath workspace = r.jenkins.getWorkspaceFor(build.getParent());

        BitbucketAuthenticatedClient client = buildAuthenticatedClient(serverURL);

        JobCheckoutListener listener = new JobCheckoutListener();
        listener.onCheckout(build, null, workspace, taskListener, null, SCMRevisionState.NONE);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(client).post(anyString(), captor.capture());
        assertThatJson(captor.getValue())
            .isObject()
            .containsEntry("key", "P/BRANCH-JOB")
            .containsEntry("parent", "P")
            .containsEntry("testResults", JsonAssertions.json("{\"successful\":5,\"failed\":2,\"skipped\":1}"));
    }

    @Issue("JENKINS-75203")
    @Test
    void test_status_notification_parent_key_null_if_cloud_is_true(@NonNull JenkinsRule r) throws Exception {
        StreamBuildListener taskListener = new StreamBuildListener(System.out, StandardCharsets.UTF_8);
        URL jenkinsURL = new URL("http://example.com:" + r.getURL().getPort() + r.contextPath + "/");
        JenkinsLocationConfiguration.get().setUrl(jenkinsURL.toString());

        String serverURL = BitbucketCloudEndpoint.SERVER_URL;

        BitbucketBuildStatusNotificationsTrait trait = new BitbucketBuildStatusNotificationsTrait();

        WorkflowRun build = prepareBuildForNotification(r, trait, serverURL);
        doReturn(Result.SUCCESS).when(build).getResult();

        FilePath workspace = r.jenkins.getWorkspaceFor(build.getParent());

        BitbucketAuthenticatedClient client = buildAuthenticatedClient(serverURL);

        JobCheckoutListener listener = new JobCheckoutListener();
        listener.onCheckout(build, null, workspace, taskListener, null, SCMRevisionState.NONE);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(client).post(endsWith("/2.0/repositories/repoOwner/repository/commit/c341232342311/statuses/build"), captor.capture());
        assertThatJson(captor.getValue())
            .isObject()
            .containsKey("key").isNotNull()
            .doesNotContainKey("parent");
    }

    @Issue("JENKINS-74970")
    @Test
    void test_status_notification_on_fork(@NonNull JenkinsRule r) throws Exception {
        StreamBuildListener taskListener = new StreamBuildListener(System.out, StandardCharsets.UTF_8);
        URL jenkinsURL = new URL("http://example.com:" + r.getURL().getPort() + r.contextPath + "/");
        JenkinsLocationConfiguration.get().setUrl(jenkinsURL.toString());

        String serverURL = "https://acme.bitbucket.org";
        BitbucketEndpointProvider.registerEndpoint("JENKINS-74970", serverURL, null);

        ForkPullRequestDiscoveryTrait trait = new ForkPullRequestDiscoveryTrait(2, new TrustEveryone());
        BranchSCMHead targetHead = new BranchSCMHead("master");
        PullRequestSCMHead scmHead = new PullRequestSCMHead("name", "repoOwner", "repository1", "feature1",
                PullRequestBranchType.BRANCH, "1", "title", targetHead, new SCMHeadOrigin.Fork("repository1"), ChangeRequestCheckoutStrategy.HEAD);
        SCMRevisionImpl prRevision = new SCMRevisionImpl(scmHead, "cff417db");
        SCMRevisionImpl targetRevision = new SCMRevisionImpl(targetHead, "c341232342311");
        SCMRevision scmRevision = new PullRequestSCMRevision(scmHead, targetRevision, prRevision);
        WorkflowRun build = prepareBuildForNotification(r, trait, serverURL, scmRevision);
        doReturn(Result.SUCCESS).when(build).getResult();

        FilePath workspace = r.jenkins.getWorkspaceFor(build.getParent());

        BitbucketAuthenticatedClient client = buildAuthenticatedClient(serverURL);

        JobCheckoutListener listener = new JobCheckoutListener();
        listener.onCheckout(build, null, workspace, taskListener, null, SCMRevisionState.NONE);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(client).post(endsWith("/rest/api/1.0/projects/repoOwner/repos/repository/commits/" + prRevision.getHash() + "/builds"), captor.capture());
        assertThatJson(captor.getValue())
            .isObject()
            .containsEntry("key", DigestUtils.md5Hex("p/branch-job"))
            .containsEntry("ref", "refs/heads/" + scmHead.getBranchName())
            .doesNotContainKey("parent");
    }

    private BitbucketAuthenticatedClient buildAuthenticatedClient(String serverURL) {
        Class<? extends BitbucketApi> apiClientClass = BitbucketApiUtils.isCloud(serverURL) ? BitbucketCloudApiClient.class : BitbucketServerAPIClient.class;
        BitbucketApi apiClient = mock(apiClientClass);
        BitbucketAuthenticatedClient authClient = mock(BitbucketAuthenticatedClient.class);
        when(authClient.getRepositoryOwner()).thenReturn("repoOwner");
        when(authClient.getRepositoryName()).thenReturn("repository");
        when(apiClient.adapt(BitbucketAuthenticatedClient.class)).thenReturn(authClient);
        BitbucketMockApiFactory.add(serverURL, apiClient);
        return authClient;
    }

    private static Stream<Arguments> buildStatusProvider() {
        UnaryOperator<BitbucketBuildStatusNotificationsTrait> notifyAbortAsCancelled = t -> {
            t.setSendStoppedNotificationForAbortBuild(true);
            return t;
        };
        UnaryOperator<BitbucketBuildStatusNotificationsTrait> notifyNotBuiltAsCancelled = t -> {
            t.setDisableNotificationForNotBuildJobs(true);
            return t;
        };

        return Stream.of(
                Arguments.of(UnaryOperator.identity(), Result.ABORTED, Status.FAILED, mock(BitbucketCloudApiClient.class)),
                Arguments.of(UnaryOperator.identity(), Result.ABORTED, Status.FAILED, mock(BitbucketServerAPIClient.class)),
                Arguments.of(notifyAbortAsCancelled, Result.ABORTED, Status.STOPPED, mock(BitbucketCloudApiClient.class)),
                Arguments.of(notifyAbortAsCancelled, Result.ABORTED, Status.CANCELLED, mock(BitbucketServerAPIClient.class)),
                Arguments.of(UnaryOperator.identity(), Result.NOT_BUILT, Status.FAILED, mock(BitbucketCloudApiClient.class)),
                Arguments.of(UnaryOperator.identity(), Result.NOT_BUILT, Status.FAILED, mock(BitbucketServerAPIClient.class)),
                Arguments.of(notifyNotBuiltAsCancelled, Result.NOT_BUILT, Status.STOPPED, mock(BitbucketCloudApiClient.class)),
                Arguments.of(notifyNotBuiltAsCancelled, Result.NOT_BUILT, Status.CANCELLED, mock(BitbucketServerAPIClient.class))
        );
    }

    private WorkflowRun prepareBuildForNotification(@NonNull JenkinsRule r, @NonNull SCMSourceTrait trait, @NonNull String serverURL) throws Exception {
        SCMHead scmHead = new BranchSCMHead("master");
        SCMRevision scmRevision = new SCMRevisionImpl(scmHead, "c341232342311");
        return prepareBuildForNotification(r, trait, serverURL, scmRevision);
    }

    private WorkflowRun prepareBuildForNotification(@NonNull JenkinsRule r, @NonNull SCMSourceTrait trait, @NonNull String serverURL, SCMRevision scmRevision) throws Exception {
        BitbucketSCMSource scmSource = new BitbucketSCMSource("repoOwner", "repository");
        scmSource.setServerUrl(serverURL);
        scmSource.setTraits(List.of(trait));

        WorkflowMultiBranchProject project = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        project.setSourcesList(List.of(new BranchSource(scmSource)));
        scmSource.setOwner(project);

        WorkflowJob job = new WorkflowJob(project, "branch-job");

        SCM scm = mock(SCM.class);

        WorkflowRun build = mock(WorkflowRun.class);
        doReturn(List.of(new SCMRevisionAction(scmSource, scmRevision))).when(build).getActions(SCMRevisionAction.class);
        doReturn(job).when(build).getParent();
        doReturn("builds/1/").when(build).getUrl();
        @SuppressWarnings("unchecked")
        BranchProjectFactory<WorkflowJob, WorkflowRun> projectFactory = mock(BranchProjectFactory.class);
        when(projectFactory.isProject(job)).thenReturn(true);
        when(projectFactory.asProject(job)).thenReturn(job);
        Branch branch = new Branch(scmSource.getId(), scmRevision.getHead(), scm, Collections.emptyList());
        when(projectFactory.getBranch(job)).thenReturn(branch);
        project.setProjectFactory(projectFactory);

        return build;
    }

    public static Stream<Arguments> buildServerURLsProvider() {
        return Stream.of(
            Arguments.of("localhost", "Jenkins URL cannot start with http://localhost"),
            Arguments.of("unconfigured-jenkins-location", "Could not determine Jenkins URL."),
            Arguments.of("localhost.local", null),
            Arguments.of("intranet.local:8080", null),
            Arguments.of("www.mydomain.com:8000", null),
            Arguments.of("www.mydomain.com", null)
        );
    }

    @ParameterizedTest(name = "checkURL {0} against Bitbucket Server")
    @MethodSource("buildServerURLsProvider")
    void test_checkURL_for_Bitbucket_server(String jenkinsURL, String expectedExceptionMsg, @NonNull JenkinsRule r) {
        BitbucketServerEndpoint endpoint = new BitbucketServerEndpoint("Bitbucket Server", "https://bitbucket.server", new ServerWebhookConfiguration(true, "dummy"));
        BitbucketEndpointConfiguration.get().setEndpoints(List.of(endpoint));

        BitbucketApi client = getApiMockClient(endpoint.getServerURL());
        if (expectedExceptionMsg != null) {
            assertThatIllegalStateException()
                .isThrownBy(() -> BitbucketBuildStatusNotifications.checkURL("http://" + jenkinsURL + "/build/sample", client))
                .withMessage(expectedExceptionMsg);
            assertThatIllegalStateException()
                .isThrownBy(() -> BitbucketBuildStatusNotifications.checkURL("https://" + jenkinsURL + "/build/sample", client))
                .withMessage(expectedExceptionMsg);
        } else {
            assertThat(BitbucketBuildStatusNotifications.checkURL("http://" + jenkinsURL + "/build/sample", client)).isNotNull();
            assertThat(BitbucketBuildStatusNotifications.checkURL("https://" + jenkinsURL + "/build/sample", client)).isNotNull();
        }
    }

    public static Stream<Arguments> buildCloudURLsProvider() {
        String fqdn = "Please use a fully qualified name or an IP address for Jenkins URL, this is required by Bitbucket cloud";

        return Stream.of(
            Arguments.of("localhost", "Jenkins URL cannot start with http://localhost"),
            Arguments.of("unconfigured-jenkins-location", "Could not determine Jenkins URL."),
            Arguments.of("intranet", fqdn),
            Arguments.of("intranet:8080", fqdn),
            Arguments.of("localhost.local", null),
            Arguments.of("intranet.local:8080", null),
            Arguments.of("www.mydomain.com:8000", null),
            Arguments.of("www.mydomain.com", null)
        );
    }

    @ParameterizedTest(name = "checkURL {0} against Bitbucket Cloud")
    @MethodSource("buildCloudURLsProvider")
    void test_checkURL_for_Bitbucket_cloud(String jenkinsURL, String expectedExceptionMsg, @NonNull JenkinsRule r) {
        BitbucketCloudEndpoint endpoint = new BitbucketCloudEndpoint(false, 0, 0, new CloudWebhookConfiguration(true, "second"));
        BitbucketEndpointConfiguration.get().setEndpoints(List.of(endpoint));

        BitbucketApi client = getApiMockClient(endpoint.getServerURL());
        if (expectedExceptionMsg != null) {
            assertThatIllegalStateException()
                .isThrownBy(() -> BitbucketBuildStatusNotifications.checkURL("http://" + jenkinsURL + "/build/sample", client))
                .withMessage(expectedExceptionMsg);
            assertThatIllegalStateException()
                .isThrownBy(() -> BitbucketBuildStatusNotifications.checkURL("https://" + jenkinsURL + "/build/sample", client))
                .withMessage(expectedExceptionMsg);
        } else {
            assertThat(BitbucketBuildStatusNotifications.checkURL("http://" + jenkinsURL + "/build/sample", client)).isNotNull();
            assertThat(BitbucketBuildStatusNotifications.checkURL("https://" + jenkinsURL + "/build/sample", client)).isNotNull();
        }
    }

}
