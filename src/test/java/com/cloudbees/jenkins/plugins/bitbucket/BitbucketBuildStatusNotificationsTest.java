/*
 * The MIT License
 *
 * Copyright 2020 CloudBees, Inc.
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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBranch;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryProtocol;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.filesystem.BitbucketSCMFile;
import hudson.model.Action;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.TaskListener;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import jenkins.branch.BranchSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.AbstractWorkflowBranchProjectFactory;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.Returns;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class BitbucketBuildStatusNotificationsTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public JenkinsRule r = new JenkinsRule() {
        @Override
        public URL getURL() throws IOException {
            return new URL("http://example.com:" + localPort + contextPath + "/");
        }
    };
    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    // TODO test of effectiveness of FirstCheckoutCompletedInvisibleAction (#130)

    @Test
    public void noInappropriateFirstCheckoutCompletedInvisibleAction() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.setScm(new SingleFileSCM("file", "contents"));
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        assertThat((List<Action>) b.getAllActions(), not(hasItem(instanceOf(FirstCheckoutCompletedInvisibleAction.class))));
    }

    private WorkflowMultiBranchProject prepareFirstCheckoutCompletedInvisibleActionTest(String dsl) throws Exception {
        String repoOwner = "bob";
        String repositoryName = "foo";
        String branchName = "master";
        String jenkinsfile = "Jenkinsfile";
        sampleRepo.init();
        sampleRepo.write(jenkinsfile, dsl);
        sampleRepo.git("add", jenkinsfile);
        sampleRepo.git("commit", "--all", "--message=defined");

        BitbucketApi api = Mockito.mock(BitbucketApi.class);
        BitbucketBranch branch = Mockito.mock(BitbucketBranch.class);
        List<? extends BitbucketBranch> branchList = Collections.singletonList(branch);
        when(api.getBranches()).thenAnswer(new Returns(branchList));
        when(branch.getName()).thenReturn(branchName);
        when(branch.getRawNode()).thenReturn(sampleRepo.head());
        BitbucketCommit commit = Mockito.mock(BitbucketCommit.class);
        when(api.resolveCommit(sampleRepo.head())).thenReturn(commit);
        when(commit.getDateMillis()).thenReturn(System.currentTimeMillis());
        when(api.checkPathExists(Mockito.anyString(), eq(jenkinsfile))).thenReturn(true);
        when(api.getRepositoryUri(any(BitbucketRepositoryProtocol.class),
                anyString(),
                eq(repoOwner),
                eq(repositoryName)))
                .thenReturn(sampleRepo.fileUrl());
        BitbucketRepository repository = Mockito.mock(BitbucketRepository.class);
        when(api.getRepository()).thenReturn(repository);
        when(repository.getOwnerName()).thenReturn(repoOwner);
        when(repository.getRepositoryName()).thenReturn(repositoryName);
        when(repository.getScm()).thenReturn("git");
        when(repository.getLinks()).thenReturn(
                Collections.singletonMap("clone",
                        Collections.singletonList(new BitbucketHref("http", sampleRepo.toString()))
                )
        );
        when(api.getRepository()).thenReturn(repository);
        when(api.getFileContent(any(BitbucketSCMFile.class))).thenReturn(
                new ByteArrayInputStream(dsl.getBytes()));
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, api);

        BitbucketSCMSource source = new BitbucketSCMSource(repoOwner, repositoryName);
        WorkflowMultiBranchProject owner = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        source.setTraits(Collections.singletonList(
                new BranchDiscoveryTrait(true, true)
        ));
        owner.setSourcesList(Collections.singletonList(new BranchSource(source)));
        source.setOwner(owner);
        return owner;
    }

    @Test
    public void firstCheckoutCompletedInvisibleAction() throws Exception {
        String dsl = "node { checkout scm }";
        WorkflowMultiBranchProject owner = prepareFirstCheckoutCompletedInvisibleActionTest(dsl);

        owner.scheduleBuild2(0).getFuture().get();
        owner.getComputation().writeWholeLogTo(System.out);
        assertThat(owner.getIndexing().getResult(), is(Result.SUCCESS));
        r.waitUntilNoActivity();
        WorkflowJob master = owner.getItem("master");
        WorkflowRun run = master.getLastBuild();
        run.writeWholeLogTo(System.out);
        assertThat(run.getResult(), is(Result.SUCCESS));
        assertThat((List<Action>) run.getAllActions(), hasItem(instanceOf(FirstCheckoutCompletedInvisibleAction.class)));
    }

    @Issue("JENKINS-66040")
    @Test
    public void shouldNotSetFirstCheckoutCompletedInvisibleActionOnOtherCheckoutWithNonDefaultFactory() throws Exception {
        String dsl = "node { checkout(scm: [$class: 'GitSCM', userRemoteConfigs: [[url: 'https://github.com/jenkinsci/bitbucket-branch-source-plugin.git']], branches: [[name: 'master']]]) }";
        WorkflowMultiBranchProject owner = prepareFirstCheckoutCompletedInvisibleActionTest(dsl);
        owner.setProjectFactory(new DummyWorkflowBranchProjectFactory(dsl));

        owner.scheduleBuild2(0).getFuture().get();
        owner.getComputation().writeWholeLogTo(System.out);
        assertThat(owner.getIndexing().getResult(), is(Result.SUCCESS));
        r.waitUntilNoActivity();
        WorkflowJob master = owner.getItem("master");
        WorkflowRun run = master.getLastBuild();
        run.writeWholeLogTo(System.out);
        assertThat(run.getResult(), is(Result.SUCCESS));
        assertThat((List<Action>) run.getAllActions(), not(hasItem(instanceOf(FirstCheckoutCompletedInvisibleAction.class))));
    }

    private static class DummyWorkflowBranchProjectFactory extends AbstractWorkflowBranchProjectFactory {
        private final String dsl;

        public DummyWorkflowBranchProjectFactory(String dsl) {
            this.dsl = dsl;
        }

        @Override
        protected FlowDefinition createDefinition() {
            return new CpsFlowDefinition(dsl, true);
        }

        @Override
        protected SCMSourceCriteria getSCMSourceCriteria(SCMSource source) {
            return new SCMSourceCriteria() {
                @Override
                public boolean isHead(Probe probe, TaskListener listener) throws IOException {
                    return true;
                }
            };
        }
    }
}
