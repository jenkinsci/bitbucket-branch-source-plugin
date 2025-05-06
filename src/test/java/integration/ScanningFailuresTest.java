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
package integration;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketMockApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBranch;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.filesystem.BitbucketSCMFile;
import com.cloudbees.jenkins.plugins.bitbucket.trait.BranchDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.ForkPullRequestDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.OriginPullRequestDiscoveryTrait;
import hudson.model.Result;
import hudson.model.TopLevelItem;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import jenkins.branch.Branch;
import jenkins.branch.BranchSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFile.Type;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.Returns;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Issue("JENKINS-36029")
public class ScanningFailuresTest {

    private static final Map<String, List<BitbucketHref>> REPOSITORY_LINKS = Map.of(
        "clone",
        List.of(
            new BitbucketHref("http", "https://bitbucket.org/tester/test-repo.git"),
            new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        )
    );

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
    private static final Random entropy = new Random();

    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    private String message;

    @Before
    public void resetEnvironment() throws Exception {
        for (TopLevelItem i : j.getInstance().getItems()) {
            i.delete();
        }
        BitbucketMockApiFactory.clear();
        message = "We gonna Boom Boom Boom 'til the break of Boom. " + Long.toHexString(entropy.nextLong()) +
            " Who's the Boom King? Who? I'm the Boom King!" ;
    }

    @Test
    public void getBranchesFailsWithIOException() throws Exception {
        getBranchesFails(() -> new IOException(message), Result.FAILURE);
    }

    @Test
    public void getBranchesFailsWithInterruptedException() throws Exception {
        getBranchesFails(() -> new InterruptedException(message), Result.ABORTED);
    }

    @Test
    public void getBranchesFailsWithRuntimeException() throws Exception {
        getBranchesFails(() -> new RuntimeException(message), Result.FAILURE);
    }

    @Test
    public void getBranchesFailsWithError() throws Exception {
        getBranchesFails(() -> new Error(message), Result.NOT_BUILT);
    }

    // We just need to verify the different types of exception being propagated for one source of exceptions
    // the others should all propagate likewise if one type succeeds.
    private void getBranchesFails(Callable<Throwable> exception, Result expectedResult) throws Exception {
        // we are going to set up just enough fake bitbucket
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file')}");
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=InitialCommit");
        BitbucketApi api = Mockito.mock(BitbucketApi.class);
        when(api.getFile(any())).thenReturn(new BitbucketSCMFile(mock(BitbucketSCMFile.class), "Jenkinsfile", Type.REGULAR_FILE, "hash"));

        BitbucketBranch branch = Mockito.mock(BitbucketBranch.class);
        List<? extends BitbucketBranch> branchList = Collections.singletonList(branch);
        when(api.getBranches()).thenAnswer(new Returns(branchList));
        when(branch.getName()).thenReturn("main");
        when(branch.getRawNode()).thenReturn(sampleRepo.head());

        BitbucketCommit commit = Mockito.mock(BitbucketCommit.class);
        when(api.resolveCommit(sampleRepo.head())).thenReturn(commit);
        when(commit.getDateMillis()).thenReturn(System.currentTimeMillis());

        BitbucketRepository repository = Mockito.mock(BitbucketRepository.class);
        when(api.getRepository()).thenReturn(repository);
        when(repository.getOwnerName()).thenReturn("bob");
        when(repository.getRepositoryName()).thenReturn("foo");
        when(repository.getScm()).thenReturn("git");
        when(repository.getLinks()).thenReturn(REPOSITORY_LINKS);

        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, api);
        WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "smokes");
        BitbucketSCMSource source = new BitbucketSCMSource("bob", "foo");
        source.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, true),
                new OriginPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD)),
                new ForkPullRequestDiscoveryTrait(
                        EnumSet.of(ChangeRequestCheckoutStrategy.HEAD),
                        new ForkPullRequestDiscoveryTrait.TrustTeamForks()
                )
        ));
        mp.getSourcesList().add(new BranchSource(source));

        mp.scheduleBuild2(0).getFuture().get();
        assertThat(mp.getIndexing().getResult(), is(Result.SUCCESS));
        assertThat(FileUtils.readFileToString(mp.getIndexing().getLogFile()), not(containsString(message)));
        j.waitUntilNoActivity();
        WorkflowJob master = mp.getItem("main");
        assertThat(master, notNullValue());

        // an error in getBranches()

        when(api.getBranches()).thenThrow(exception.call());

        if (Result.NOT_BUILT.equals(expectedResult) || Result.ABORTED.equals(expectedResult)) {
            // when not built or aborted the future will never complete and the log may not contain the exception stack trace
            mp.scheduleBuild2(0);
            j.waitUntilNoActivity();
            assertThat(mp.getIndexing().getResult(), is(expectedResult));
        } else {
            mp.scheduleBuild2(0).getFuture().get(10, TimeUnit.SECONDS);
            assertThat(mp.getIndexing().getResult(), is(expectedResult));
            assertThat(FileUtils.readFileToString(mp.getIndexing().getLogFile()), containsString(message));
        }
        master = mp.getItem("main");
        assertThat(master, notNullValue());
        assertThat(mp.getProjectFactory().getBranch(master), not(instanceOf(Branch.Dead.class)));
    }

    @Test
    public void checkPathExistsFails() throws Exception {
        // we are going to set up just enough fake bitbucket
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file')}");
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=InitialCommit");
        BitbucketApi api = Mockito.mock(BitbucketApi.class);
        when(api.getFile(any())).thenAnswer(new Answer<SCMFile>() {
            @Override
            public SCMFile answer(InvocationOnMock invocation) throws Throwable {
                BitbucketSCMFile scmFile = invocation.getArgument(0);
                return new BitbucketSCMFile((BitbucketSCMFile) scmFile.parent(), scmFile.getName(), Type.REGULAR_FILE, scmFile.getHash());
            }
        });

        BitbucketBranch branch = Mockito.mock(BitbucketBranch.class);
        List<? extends BitbucketBranch> branchList = Collections.singletonList(branch);
        when(api.getBranches()).thenAnswer(new Returns(branchList));
        when(branch.getName()).thenReturn("main");
        when(branch.getRawNode()).thenReturn(sampleRepo.head());

        BitbucketCommit commit = Mockito.mock(BitbucketCommit.class);
        when(api.resolveCommit(sampleRepo.head())).thenReturn(commit);
        when(commit.getDateMillis()).thenReturn(System.currentTimeMillis());

        when(api.checkPathExists(Mockito.anyString(), eq("Jenkinsfile"))).thenReturn(true);

        BitbucketRepository repository = Mockito.mock(BitbucketRepository.class);
        when(api.getRepository()).thenReturn(repository);
        when(repository.getOwnerName()).thenReturn("bob");
        when(repository.getRepositoryName()).thenReturn("foo");
        when(repository.getScm()).thenReturn("git");
        when(repository.getLinks()).thenReturn(REPOSITORY_LINKS);

        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, api);
        WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "smokes");
        BitbucketSCMSource source = new BitbucketSCMSource("bob", "foo");
        source.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, true),
                new OriginPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD)),
                new ForkPullRequestDiscoveryTrait(
                        EnumSet.of(ChangeRequestCheckoutStrategy.HEAD),
                        new ForkPullRequestDiscoveryTrait.TrustTeamForks()
                )
        ));
        mp.getSourcesList().add(new BranchSource(source));

        mp.scheduleBuild2(0).getFuture().get();
        assertThat(mp.getIndexing().getResult(), is(Result.SUCCESS));
        assertThat(FileUtils.readFileToString(mp.getIndexing().getLogFile(), StandardCharsets.UTF_8), not(containsString(message)));
        j.waitUntilNoActivity();
        WorkflowJob master = mp.getItem("main");
        assertThat(master, notNullValue());

        // an error in checkPathExists(...)
        doThrow(new IOException(message)).when(api).getFile(any(BitbucketSCMFile.class));

        mp.scheduleBuild2(0).getFuture().get();
        assertThat(mp.getIndexing().getResult(), is(Result.SUCCESS));
        assertThat(FileUtils.readFileToString(mp.getIndexing().getLogFile(), StandardCharsets.UTF_8), containsString("‘Jenkinsfile’ not found"));
        master = mp.getItem("main");
        assertThat(master, nullValue());
    }

    @Test
    public void resolveCommitFails() throws Exception {
        // we are going to set up just enough fake bitbucket
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file')}");
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=InitialCommit");
        BitbucketApi api = Mockito.mock(BitbucketApi.class);
        when(api.getFile(any())).thenReturn(new BitbucketSCMFile(mock(BitbucketSCMFile.class), "Jenkinsfile", Type.REGULAR_FILE, "hash"));

        BitbucketBranch branch = Mockito.mock(BitbucketBranch.class);
        List<? extends BitbucketBranch> branchList = Collections.singletonList(branch);
        when(api.getBranches()).thenAnswer(new Returns(branchList));
        when(branch.getName()).thenReturn("main");
        when(branch.getRawNode()).thenReturn(sampleRepo.head());

        BitbucketCommit commit = Mockito.mock(BitbucketCommit.class);
        when(api.resolveCommit(sampleRepo.head())).thenReturn(commit);
        when(commit.getDateMillis()).thenReturn(System.currentTimeMillis());

        when(api.checkPathExists(Mockito.anyString(), eq("Jenkinsfile"))).thenReturn(true);

        BitbucketRepository repository = Mockito.mock(BitbucketRepository.class);
        when(api.getRepository()).thenReturn(repository);
        when(repository.getOwnerName()).thenReturn("bob");
        when(repository.getRepositoryName()).thenReturn("foo");
        when(repository.getScm()).thenReturn("git");
        when(repository.getLinks()).thenReturn(REPOSITORY_LINKS);

        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, api);
        WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "smokes");
        BitbucketSCMSource source = new BitbucketSCMSource("bob", "foo");
        source.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, true),
                new OriginPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD)),
                new ForkPullRequestDiscoveryTrait(
                        EnumSet.of(ChangeRequestCheckoutStrategy.HEAD),
                        new ForkPullRequestDiscoveryTrait.TrustTeamForks()
                )
        ));
        mp.getSourcesList().add(new BranchSource(source));

        mp.scheduleBuild2(0).getFuture().get();
        assertThat(mp.getIndexing().getResult(), is(Result.SUCCESS));
        assertThat(FileUtils.readFileToString(mp.getIndexing().getLogFile()), not(containsString(message)));
        j.waitUntilNoActivity();
        WorkflowJob master = mp.getItem("main");
        assertThat(master, notNullValue());
        assertThat(master.getLastBuild(), notNullValue());
        assertThat(master.getNextBuildNumber(), is(2));

        // an error in resolveCommit(...)
        when(api.resolveCommit(sampleRepo.head())).thenThrow(new IOException(message));

        mp.scheduleBuild2(0).getFuture().get();
        assertThat(mp.getIndexing().getResult(), is(Result.SUCCESS));
        master = mp.getItem("main");
        assertThat(master, notNullValue());
        assertThat(mp.getProjectFactory().getBranch(master), not(instanceOf(Branch.Dead.class)));
        assertThat(master.getLastBuild(), notNullValue());
        assertThat(master.getNextBuildNumber(), is(2));
    }

    @Test
    public void branchRemoved() throws Exception {
        // we are going to set up just enough fake bitbucket
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file')}");
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=InitialCommit");
        BitbucketApi api = Mockito.mock(BitbucketApi.class);
        when(api.getFile(any())).thenReturn(new BitbucketSCMFile(mock(BitbucketSCMFile.class), "Jenkinsfile", Type.REGULAR_FILE, "hash"));

        BitbucketBranch branch = Mockito.mock(BitbucketBranch.class);
        List<? extends BitbucketBranch> branchList = Collections.singletonList(branch);
        when(api.getBranches()).thenAnswer(new Returns(branchList));
        when(branch.getName()).thenReturn("main");
        when(branch.getRawNode()).thenReturn(sampleRepo.head());

        BitbucketCommit commit = Mockito.mock(BitbucketCommit.class);
        when(api.resolveCommit(sampleRepo.head())).thenReturn(commit);
        when(commit.getDateMillis()).thenReturn(System.currentTimeMillis());

        when(api.checkPathExists(Mockito.anyString(), eq("Jenkinsfile"))).thenReturn(true);

        BitbucketRepository repository = Mockito.mock(BitbucketRepository.class);
        when(api.getRepository()).thenReturn(repository);
        when(repository.getOwnerName()).thenReturn("bob");
        when(repository.getRepositoryName()).thenReturn("foo");
        when(repository.getScm()).thenReturn("git");
        when(repository.getLinks()).thenReturn(REPOSITORY_LINKS);

        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, api);
        WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "smokes");
        BitbucketSCMSource source = new BitbucketSCMSource("bob", "foo");
        source.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, true),
                new OriginPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD)),
                new ForkPullRequestDiscoveryTrait(
                        EnumSet.of(ChangeRequestCheckoutStrategy.HEAD),
                        new ForkPullRequestDiscoveryTrait.TrustTeamForks()
                )
        ));
        mp.getSourcesList().add(new BranchSource(source));

        mp.scheduleBuild2(0).getFuture().get();
        assertThat(mp.getIndexing().getResult(), is(Result.SUCCESS));
        assertThat(FileUtils.readFileToString(mp.getIndexing().getLogFile()), not(containsString(message)));
        j.waitUntilNoActivity();
        WorkflowJob master = mp.getItem("main");
        assertThat(master, notNullValue());
        assertThat(master.getLastBuild(), notNullValue());
        assertThat(master.getNextBuildNumber(), is(2));

        // the branch is actually removed
        when(api.getBranches()).thenAnswer(new Returns(Collections.emptyList()));

        mp.scheduleBuild2(0).getFuture().get();
        assertThat(mp.getIndexing().getResult(), is(Result.SUCCESS));
        master = mp.getItem("main");
        assertThat(master, nullValue());
    }
}
