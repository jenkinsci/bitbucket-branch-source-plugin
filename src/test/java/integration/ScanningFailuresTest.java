package integration;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketMockApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBranch;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryProtocol;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryType;
import hudson.model.Result;
import hudson.model.TopLevelItem;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import jenkins.branch.Branch;
import jenkins.branch.BranchSource;
import jenkins.plugins.git.GitSampleRepoRule;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

@Issue("JENKINS-36029")
public class ScanningFailuresTest {

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
        message = "We gonna Boom Boom Boom ‘til the break of Boom. "+ Long.toHexString(entropy.nextLong())+ " Who's the Boom King? Who? I'm the Boom King!" ;
    }

    @Test
    public void getBranchesFailsWithIOException() throws Exception {
        getBranchesFails(new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                return new IOException(message);
            }
        }, Result.FAILURE);
    }

    @Test
    public void getBranchesFailsWithInterruptedException() throws Exception {
        getBranchesFails(new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                return new InterruptedException(message);
            }
        }, Result.ABORTED);
    }

    @Test
    public void getBranchesFailsWithRuntimeException() throws Exception {
        getBranchesFails(new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                return new RuntimeException(message);
            }
        }, Result.FAILURE);
    }

    @Test
    public void getBranchesFailsWithError() throws Exception {
        getBranchesFails(new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                return new Error(message);
            }
        }, Result.NOT_BUILT);
    }

    // We just need to verify the different types of exception being propagated for one source of exceptions
    // the others should all propagate likewise if one type succeeds.
    private void getBranchesFails(Callable<Throwable> exception, Result expectedResult) throws Exception {
        // we are going to set up just enough fake bitbucket
        sampleRepo.init();
        sampleRepo
                .write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file')}");
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=InitialCommit");
        BitbucketApi api = Mockito.mock(BitbucketApi.class);

        BitbucketBranch branch = Mockito.mock(BitbucketBranch.class);
        List<? extends BitbucketBranch> branchList = Collections.singletonList(branch);
        when(api.getBranches()).thenAnswer(new Returns(branchList));
        when(branch.getName()).thenReturn("master");
        when(branch.getRawNode()).thenReturn(sampleRepo.head());

        BitbucketCommit commit = Mockito.mock(BitbucketCommit.class);
        when(api.resolveCommit(sampleRepo.head())).thenReturn(commit);
        when(commit.getDateMillis()).thenReturn(System.currentTimeMillis());

        when(api.checkPathExists("master", "Jenkinsfile")).thenReturn(true);

        when(api.getRepositoryUri(eq(BitbucketRepositoryType.GIT),
                any(BitbucketRepositoryProtocol.class),
                any(Integer.class),
                eq("bob"),
                eq("foo")))
                .thenReturn(sampleRepo.fileUrl());

        BitbucketRepository repository = Mockito.mock(BitbucketRepository.class);
        when(api.getRepository()).thenReturn(repository);
        when(repository.getOwnerName()).thenReturn("bob");
        when(repository.getRepositoryName()).thenReturn("foo");
        when(repository.getScm()).thenReturn("git");

        BitbucketMockApiFactory.add(null, api);
        WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "smokes");
        mp.getSourcesList().add(new BranchSource(new BitbucketSCMSource(null, "bob", "foo")));

        mp.scheduleBuild2(0).getFuture().get();
        assertThat(mp.getIndexing().getResult(), is(Result.SUCCESS));
        assertThat(FileUtils.readFileToString(mp.getIndexing().getLogFile()), not(containsString(message)));
        j.waitUntilNoActivity();
        WorkflowJob master = mp.getItem("master");
        assertThat(master, notNullValue());

        // an error in getBranches()

        when(api.getBranches()).thenThrow(exception.call());

        if (Result.NOT_BUILT.equals(expectedResult)) {
            // when not built the future will never complete and the log may not contain the exception stack trace
            mp.scheduleBuild2(0);
            j.waitUntilNoActivity();
            assertThat(mp.getIndexing().getResult(), is(expectedResult));
        } else {
            mp.scheduleBuild2(0).getFuture().get(10, TimeUnit.SECONDS);
            assertThat(mp.getIndexing().getResult(), is(expectedResult));
            assertThat(FileUtils.readFileToString(mp.getIndexing().getLogFile()), containsString(message));
        }
        master = mp.getItem("master");
        assertThat(master, notNullValue());
        assertThat(mp.getProjectFactory().getBranch(master), not(instanceOf(Branch.Dead.class)));
    }

    @Test
    public void checkPathExistsFails() throws Exception {
        // we are going to set up just enough fake bitbucket
        sampleRepo.init();
        sampleRepo
                .write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file')}");
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=InitialCommit");
        BitbucketApi api = Mockito.mock(BitbucketApi.class);

        BitbucketBranch branch = Mockito.mock(BitbucketBranch.class);
        List<? extends BitbucketBranch> branchList = Collections.singletonList(branch);
        when(api.getBranches()).thenAnswer(new Returns(branchList));
        when(branch.getName()).thenReturn("master");
        when(branch.getRawNode()).thenReturn(sampleRepo.head());

        BitbucketCommit commit = Mockito.mock(BitbucketCommit.class);
        when(api.resolveCommit(sampleRepo.head())).thenReturn(commit);
        when(commit.getDateMillis()).thenReturn(System.currentTimeMillis());

        when(api.checkPathExists("master", "Jenkinsfile")).thenReturn(true);

        when(api.getRepositoryUri(eq(BitbucketRepositoryType.GIT),
                any(BitbucketRepositoryProtocol.class),
                any(Integer.class),
                eq("bob"),
                eq("foo")))
                .thenReturn(sampleRepo.fileUrl());

        BitbucketRepository repository = Mockito.mock(BitbucketRepository.class);
        when(api.getRepository()).thenReturn(repository);
        when(repository.getOwnerName()).thenReturn("bob");
        when(repository.getRepositoryName()).thenReturn("foo");
        when(repository.getScm()).thenReturn("git");

        BitbucketMockApiFactory.add(null, api);
        WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "smokes");
        mp.getSourcesList().add(new BranchSource(new BitbucketSCMSource(null, "bob", "foo")));

        mp.scheduleBuild2(0).getFuture().get();
        assertThat(mp.getIndexing().getResult(), is(Result.SUCCESS));
        assertThat(FileUtils.readFileToString(mp.getIndexing().getLogFile()), not(containsString(message)));
        j.waitUntilNoActivity();
        WorkflowJob master = mp.getItem("master");
        assertThat(master, notNullValue());

        // an error in checkPathExists(...)
        when(api.checkPathExists("master", "Jenkinsfile")).thenThrow(new IOException(message));

        mp.scheduleBuild2(0).getFuture().get();
        assertThat(mp.getIndexing().getResult(), is(Result.FAILURE));
        assertThat(FileUtils.readFileToString(mp.getIndexing().getLogFile()), containsString(message));
        master = mp.getItem("master");
        assertThat(master, notNullValue());
        assertThat(mp.getProjectFactory().getBranch(master), not(instanceOf(Branch.Dead.class)));
    }

    @Test
    public void resolveCommitFails() throws Exception {
        // we are going to set up just enough fake bitbucket
        sampleRepo.init();
        sampleRepo
                .write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file')}");
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=InitialCommit");
        BitbucketApi api = Mockito.mock(BitbucketApi.class);

        BitbucketBranch branch = Mockito.mock(BitbucketBranch.class);
        List<? extends BitbucketBranch> branchList = Collections.singletonList(branch);
        when(api.getBranches()).thenAnswer(new Returns(branchList));
        when(branch.getName()).thenReturn("master");
        when(branch.getRawNode()).thenReturn(sampleRepo.head());

        BitbucketCommit commit = Mockito.mock(BitbucketCommit.class);
        when(api.resolveCommit(sampleRepo.head())).thenReturn(commit);
        when(commit.getDateMillis()).thenReturn(System.currentTimeMillis());

        when(api.checkPathExists("master", "Jenkinsfile")).thenReturn(true);

        when(api.getRepositoryUri(eq(BitbucketRepositoryType.GIT),
                any(BitbucketRepositoryProtocol.class),
                any(Integer.class),
                eq("bob"),
                eq("foo")))
                .thenReturn(sampleRepo.fileUrl());

        BitbucketRepository repository = Mockito.mock(BitbucketRepository.class);
        when(api.getRepository()).thenReturn(repository);
        when(repository.getOwnerName()).thenReturn("bob");
        when(repository.getRepositoryName()).thenReturn("foo");
        when(repository.getScm()).thenReturn("git");

        BitbucketMockApiFactory.add(null, api);
        WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "smokes");
        mp.getSourcesList().add(new BranchSource(new BitbucketSCMSource(null, "bob", "foo")));

        mp.scheduleBuild2(0).getFuture().get();
        assertThat(mp.getIndexing().getResult(), is(Result.SUCCESS));
        assertThat(FileUtils.readFileToString(mp.getIndexing().getLogFile()), not(containsString(message)));
        j.waitUntilNoActivity();
        WorkflowJob master = mp.getItem("master");
        assertThat(master, notNullValue());
        assertThat(master.getLastBuild(), notNullValue());
        assertThat(master.getNextBuildNumber(), is(2));

        // an error in resolveCommit(...)

        when(api.resolveCommit(sampleRepo.head())).thenThrow(new IOException(message));

        mp.scheduleBuild2(0).getFuture().get();
        assertThat(mp.getIndexing().getResult(), is(Result.SUCCESS));
        master = mp.getItem("master");
        assertThat(master, notNullValue());
        assertThat(mp.getProjectFactory().getBranch(master), not(instanceOf(Branch.Dead.class)));
        assertThat(master.getLastBuild(), notNullValue());
        assertThat(master.getNextBuildNumber(), is(2));
    }

    @Test
    public void branchRemoved() throws Exception {
        // we are going to set up just enough fake bitbucket
        sampleRepo.init();
        sampleRepo
                .write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file')}");
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=InitialCommit");
        BitbucketApi api = Mockito.mock(BitbucketApi.class);

        BitbucketBranch branch = Mockito.mock(BitbucketBranch.class);
        List<? extends BitbucketBranch> branchList = Collections.singletonList(branch);
        when(api.getBranches()).thenAnswer(new Returns(branchList));
        when(branch.getName()).thenReturn("master");
        when(branch.getRawNode()).thenReturn(sampleRepo.head());

        BitbucketCommit commit = Mockito.mock(BitbucketCommit.class);
        when(api.resolveCommit(sampleRepo.head())).thenReturn(commit);
        when(commit.getDateMillis()).thenReturn(System.currentTimeMillis());

        when(api.checkPathExists("master", "Jenkinsfile")).thenReturn(true);

        when(api.getRepositoryUri(eq(BitbucketRepositoryType.GIT),
                any(BitbucketRepositoryProtocol.class),
                any(Integer.class),
                eq("bob"),
                eq("foo")))
                .thenReturn(sampleRepo.fileUrl());

        BitbucketRepository repository = Mockito.mock(BitbucketRepository.class);
        when(api.getRepository()).thenReturn(repository);
        when(repository.getOwnerName()).thenReturn("bob");
        when(repository.getRepositoryName()).thenReturn("foo");
        when(repository.getScm()).thenReturn("git");

        BitbucketMockApiFactory.add(null, api);
        WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "smokes");
        mp.getSourcesList().add(new BranchSource(new BitbucketSCMSource(null, "bob", "foo")));

        mp.scheduleBuild2(0).getFuture().get();
        assertThat(mp.getIndexing().getResult(), is(Result.SUCCESS));
        assertThat(FileUtils.readFileToString(mp.getIndexing().getLogFile()), not(containsString(message)));
        j.waitUntilNoActivity();
        WorkflowJob master = mp.getItem("master");
        assertThat(master, notNullValue());
        assertThat(master.getLastBuild(), notNullValue());
        assertThat(master.getNextBuildNumber(), is(2));

        // the branch is actually removed

        when(api.getBranches()).thenAnswer(new Returns(Collections.emptyList()));

        mp.scheduleBuild2(0).getFuture().get();
        assertThat(mp.getIndexing().getResult(), is(Result.SUCCESS));
        master = mp.getItem("master");
        assertThat(master, nullValue());
    }
}
