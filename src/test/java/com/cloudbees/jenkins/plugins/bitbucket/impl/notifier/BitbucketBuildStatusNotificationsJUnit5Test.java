/*
 * The MIT License
 *
 * Copyright 2024 Nikolas Falco, CloudBees, Inc.
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

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketBuildStatusNotificationsTrait;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketMockApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.BranchSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus.Status;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketServerEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.notifier.BitbucketBuildStatusNotifications;
import com.cloudbees.jenkins.plugins.bitbucket.impl.notifier.BitbucketBuildStatusNotifications.JobCheckoutListener;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerAPIClient;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.model.Result;
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
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.ArgumentCaptor;

import static com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory.getApiMockClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WithJenkins
class BitbucketBuildStatusNotificationsJUnit5Test {
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

        BitbucketBuildStatusNotificationsTrait trait = traitCustomizer.apply(new BitbucketBuildStatusNotificationsTrait());
        WorkflowRun build = prepareBuildForNotification(r, trait);
        doReturn(buildResult).when(build).getResult();

        FilePath workspace = r.jenkins.getWorkspaceFor(build.getParent());

        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, apiClient);

        JobCheckoutListener listener = new JobCheckoutListener();
        listener.onCheckout(build, null, workspace, taskListener, null, SCMRevisionState.NONE);

        ArgumentCaptor<BitbucketBuildStatus> captor = ArgumentCaptor.forClass(BitbucketBuildStatus.class);
        verify(apiClient).postBuildStatus(captor.capture());
        assertThat(captor.getValue().getState(), is(expectedStatus.name()));
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

    private WorkflowRun prepareBuildForNotification(@NonNull JenkinsRule r, @NonNull SCMSourceTrait trait) throws Exception {
        BitbucketSCMSource scmSource = new BitbucketSCMSource("repoOwner", "repository");
        scmSource.setTraits(List.of(trait));

        WorkflowMultiBranchProject project = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        project.setSourcesList(List.of(new BranchSource(scmSource)));
        scmSource.setOwner(project);

        WorkflowJob job = new WorkflowJob(project, "branch-job");

        SCMHead scmHead = new BranchSCMHead("master");
        SCMRevision scmRevision = new SCMRevisionImpl(scmHead, "c341232342311");
        SCM scm = mock(SCM.class);

        WorkflowRun build = mock(WorkflowRun.class);
        doReturn(List.of(new SCMRevisionAction(scmSource, scmRevision))).when(build).getActions(SCMRevisionAction.class);
        doReturn(job).when(build).getParent();
        doReturn("builds/1/").when(build).getUrl();
        @SuppressWarnings("unchecked")
        BranchProjectFactory<WorkflowJob, WorkflowRun> projectFactory = mock(BranchProjectFactory.class);
        when(projectFactory.isProject(job)).thenReturn(true);
        when(projectFactory.asProject(job)).thenReturn(job);
        Branch branch = new Branch(scmSource.getId(), scmHead, scm, Collections.emptyList());
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
        BitbucketServerEndpoint endpoint = new BitbucketServerEndpoint("Bitbucket Server", "https://bitbucket.server", true, "dummy");
        BitbucketEndpointConfiguration.get().setEndpoints(List.of(endpoint));

        BitbucketApi client = getApiMockClient(endpoint.getServerUrl());
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
        BitbucketCloudEndpoint endpoint = new BitbucketCloudEndpoint(true, "second");
        BitbucketEndpointConfiguration.get().setEndpoints(List.of(endpoint));

        BitbucketApi client = getApiMockClient(endpoint.getServerUrl());
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
