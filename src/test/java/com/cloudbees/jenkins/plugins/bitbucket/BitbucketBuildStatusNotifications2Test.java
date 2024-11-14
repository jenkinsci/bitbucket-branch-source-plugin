/*
 * The MIT License
 *
 * Copyright 2024 Nikolas Falco
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

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketBuildStatusNotifications.JobCheckoutListener;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus.Status;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerAPIClient;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.model.Result;
import hudson.model.StreamBuildListener;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;
import jenkins.branch.Branch;
import jenkins.branch.BranchProjectFactory;
import jenkins.branch.BranchSource;
import jenkins.plugins.git.AbstractGitSCMSource.SCMRevisionImpl;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class BitbucketBuildStatusNotifications2Test {

    @ClassRule
    public static JenkinsRule r = new JenkinsRule() {
        @Override
        public URL getURL() throws IOException {
            return new URL("http://example.com:" + localPort + contextPath + "/");
        }
    };

    private static UnaryOperator<BitbucketBuildStatusNotificationsTrait> notifyAbortAsCancelled = t -> {
        t.setSendStoppedNotificationForAbortBuild(true);
        return t;
    };

    private static UnaryOperator<BitbucketBuildStatusNotificationsTrait> notifyNotBuiltAsCancelled = t -> {
        t.setDisableNotificationForNotBuildJobs(true);
        return t;
    };

    @Parameterized.Parameters(name = "When build result is {1} expect to notify status {2}")
    public static Collection<Object[]> input() {
        return Arrays.asList(new Object[][] {
                                              { UnaryOperator.identity(), Result.ABORTED, Status.FAILED, mock(BitbucketCloudApiClient.class) },
                                              { UnaryOperator.identity(), Result.ABORTED, Status.FAILED, mock(BitbucketServerAPIClient.class) },
                                              { notifyAbortAsCancelled, Result.ABORTED, Status.STOPPED, mock(BitbucketCloudApiClient.class) },
                                              { notifyAbortAsCancelled, Result.ABORTED, Status.CANCELLED, mock(BitbucketServerAPIClient.class) },
                                              { UnaryOperator.identity(), Result.NOT_BUILT, Status.FAILED, mock(BitbucketCloudApiClient.class) },
                                              { UnaryOperator.identity(), Result.NOT_BUILT, Status.FAILED, mock(BitbucketServerAPIClient.class) },
                                              { notifyNotBuiltAsCancelled, Result.NOT_BUILT, Status.STOPPED, mock(BitbucketCloudApiClient.class) },
                                              { notifyNotBuiltAsCancelled, Result.NOT_BUILT, Status.CANCELLED, mock(BitbucketServerAPIClient.class) },
        });
    }

    private BitbucketBuildStatusNotificationsTrait trait;
    private Result buildResult;
    private Status expectedStatus;
    private BitbucketApi apiClient;

    public BitbucketBuildStatusNotifications2Test(UnaryOperator<BitbucketBuildStatusNotificationsTrait> traitCustomizer,
                                                  Result buildResult,
                                                  Status expectedStatus,
                                                  BitbucketApi apiClient) {
        this.trait = traitCustomizer.apply(new BitbucketBuildStatusNotificationsTrait());
        this.buildResult = buildResult;
        this.expectedStatus = expectedStatus;
        this.apiClient = apiClient;
    }

    @After
    public void cleanUp() {
        r.jenkins.getAllItems().forEach(item -> {
            try {
                item.delete();
            } catch (IOException | InterruptedException e) {
            }
        });
    }

    @Test
    public void test_status_notification_for_given_build_result() throws Exception {
        StreamBuildListener taskListener = new StreamBuildListener(System.out, StandardCharsets.UTF_8);

        WorkflowRun build = prepareBuildForNotification(List.of(trait));
        doReturn(buildResult).when(build).getResult();

        FilePath workspace = r.jenkins.getWorkspaceFor(build.getParent());

        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, apiClient);

        JobCheckoutListener listener = new JobCheckoutListener();
        listener.onCheckout(build, null, workspace, taskListener, null, SCMRevisionState.NONE);

        ArgumentCaptor<BitbucketBuildStatus> captor = ArgumentCaptor.forClass(BitbucketBuildStatus.class);
        verify(apiClient).postBuildStatus(captor.capture());
        assertThat(captor.getValue().getState(), is(expectedStatus.name()));
    }

    private WorkflowRun prepareBuildForNotification(@NonNull List<SCMSourceTrait> traits) throws Exception {
        BitbucketSCMSource scmSource = new BitbucketSCMSource("repoOwner", "repository");
        scmSource.setTraits(traits);

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

}
