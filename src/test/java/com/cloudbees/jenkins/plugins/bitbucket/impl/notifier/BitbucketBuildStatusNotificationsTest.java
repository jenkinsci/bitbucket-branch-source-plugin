/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketMockApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.FirstCheckoutCompletedInvisibleAction;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBranch;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.filesystem.BitbucketSCMFile;
import com.cloudbees.jenkins.plugins.bitbucket.trait.BranchDiscoveryTrait;
import hudson.model.Descriptor.FormException;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.TaskListener;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import jenkins.branch.BranchSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.junit.jupiter.WithGitSampleRepo;
import jenkins.scm.api.SCMFile.Type;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.AbstractWorkflowBranchProjectFactory;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.internal.stubbing.answers.Returns;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@WithGitSampleRepo
@WithJenkins
class BitbucketBuildStatusNotificationsTest {

    private GitSampleRepoRule sampleRepo;
    private JenkinsRule r;

    @BeforeEach
    void setup(GitSampleRepoRule sampleRepo, JenkinsRule rule) throws Exception {
        this.sampleRepo = sampleRepo;
        this.r = spy(rule);
        doReturn(new URL("http://example.com:" + extractJenkinsHttpPort(rule) + rule.contextPath + "/")).when(r).getURL();
    }

    private Integer extractJenkinsHttpPort(JenkinsRule rule) {
        Field field = ReflectionUtils.findField(JenkinsRule.class, "localPort");
        field.setAccessible(true);
        Integer localPort = (Integer) ReflectionUtils.getField(field, rule);
        return localPort;
    }

    @Test
    void noInappropriateFirstCheckoutCompletedInvisibleAction() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.setScm(new SingleFileSCM("file", "contents"));
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        assertThat(b.getAllActions()).doesNotHaveAnyElementsOfTypes(FirstCheckoutCompletedInvisibleAction.class);
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

        BitbucketApi api = mock(BitbucketApi.class);
        BitbucketBranch branch = mock(BitbucketBranch.class);
        List<? extends BitbucketBranch> branchList = Collections.singletonList(branch);
        when(api.getBranches()).thenAnswer(new Returns(branchList));
        when(api.getBranch("master")).thenAnswer(new Returns(branch));
        when(branch.getName()).thenReturn(branchName);
        when(branch.getRawNode()).thenReturn(sampleRepo.head());
        BitbucketCommit commit = mock(BitbucketCommit.class);
        when(api.resolveCommit(sampleRepo.head())).thenReturn(commit);
        when(commit.getDateMillis()).thenReturn(System.currentTimeMillis());
        BitbucketRepository repository = mock(BitbucketRepository.class);
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
        when(api.getFile(any(BitbucketSCMFile.class))).thenReturn(new BitbucketSCMFile(mock(BitbucketSCMFile.class), "master", Type.REGULAR_FILE, "hash"));
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
    void firstCheckoutCompletedInvisibleAction() throws Exception {
        String dsl = "node { checkout scm }";
        WorkflowMultiBranchProject owner = prepareFirstCheckoutCompletedInvisibleActionTest(dsl);

        owner.scheduleBuild2(0).getFuture().get();
        owner.getComputation().writeWholeLogTo(System.out);
        assertThat(owner.getIndexing().getResult()).isEqualTo(Result.SUCCESS);
        r.waitUntilNoActivity();
        WorkflowJob master = owner.getItem("master");
        WorkflowRun run = master.getLastBuild();
        run.writeWholeLogTo(System.out);
        assertThat(run.getResult()).isEqualTo(Result.SUCCESS);
        assertThat(run.getAllActions()).hasAtLeastOneElementOfType(FirstCheckoutCompletedInvisibleAction.class);
    }

    @Issue("JENKINS-66040")
    @Test
    void shouldNotSetFirstCheckoutCompletedInvisibleActionOnOtherCheckoutWithNonDefaultFactory() throws Exception {
        String dsl = "node { checkout(scm: [$class: 'GitSCM', userRemoteConfigs: [[url: 'https://github.com/jenkinsci/bitbucket-branch-source-plugin.git']], branches: [[name: 'master']]]) }";
        WorkflowMultiBranchProject owner = prepareFirstCheckoutCompletedInvisibleActionTest(dsl);
        owner.setProjectFactory(new DummyWorkflowBranchProjectFactory(dsl));

        owner.scheduleBuild2(0).getFuture().get();
        owner.getComputation().writeWholeLogTo(System.out);
        assertThat(owner.getIndexing().getResult()).isEqualTo(Result.SUCCESS);
        r.waitUntilNoActivity();
        WorkflowJob master = owner.getItem("master");
        WorkflowRun run = master.getLastBuild();
        run.writeWholeLogTo(System.out);
        assertThat(run.getResult()).isEqualTo(Result.SUCCESS);
        assertThat(run.getAllActions()).doesNotHaveAnyElementsOfTypes(FirstCheckoutCompletedInvisibleAction.class);
    }

    private static class DummyWorkflowBranchProjectFactory extends AbstractWorkflowBranchProjectFactory {
        private final String dsl;

        public DummyWorkflowBranchProjectFactory(String dsl) {
            this.dsl = dsl;
        }

        @Override
        protected FlowDefinition createDefinition() {
            try {
                return new CpsFlowDefinition(dsl, true);
            } catch (FormException e) {
                throw new RuntimeException(e);
            }
        }

        @SuppressWarnings("serial")
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
