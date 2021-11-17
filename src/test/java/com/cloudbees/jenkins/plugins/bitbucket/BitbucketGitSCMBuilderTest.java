package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.browser.BitbucketWeb;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.BuildChooserSetting;
import hudson.util.LogTaskListener;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.branch.BranchSource;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMSourceDefaults;
import jenkins.plugins.git.MergeWithGitSCMExtension;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.eclipse.jgit.transport.RemoteConfig;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class BitbucketGitSCMBuilderTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
    private BitbucketSCMSource source;
    private WorkflowMultiBranchProject owner;

    @Before
    public void setUp() throws IOException {
        owner = j.createProject(WorkflowMultiBranchProject.class);
        source = new BitbucketSCMSource( "tester", "test-repo");
        owner.setSourcesList(Collections.singletonList(new BranchSource(source)));
        source.setOwner(owner);
        Credentials userPasswordCredential = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "user-pass", null, "git-user", "git-secret");
        Credentials sshPrivateKeyCredential = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, "user-key", "git",
                new BasicSSHUserPrivateKey.UsersPrivateKeySource(), null, null);
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(Collections.singletonMap(Domain.global(),
                Arrays.<Credentials>asList(userPasswordCredential, sshPrivateKeyCredential)));
    }

    @After
    public void tearDown() throws IOException, InterruptedException {
        SystemCredentialsProvider.getInstance()
                .setDomainCredentialsMap(Collections.<Domain, List<Credentials>>emptyMap());
        owner.delete();
    }

    @Test
    public void given__cloud_branch_rev_anon__when__build__then__scmBuilt() throws Exception {
        BranchSCMHead head = new BranchSCMHead("test-branch");
        AbstractGitSCMSource.SCMRevisionImpl revision =
                new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.org/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket.org/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.org/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(BuildChooserSetting.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "test-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_branch_rev_userpass__when__build__then__scmBuilt() throws Exception {
        BranchSCMHead head = new BranchSCMHead("test-branch");
        AbstractGitSCMSource.SCMRevisionImpl revision =
                new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.org/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket.org/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.org/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(BuildChooserSetting.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "test-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_branch_rev_userkey__when__build__then__scmBuilt() throws Exception {
        BranchSCMHead head = new BranchSCMHead("test-branch");
        AbstractGitSCMSource.SCMRevisionImpl revision =
                new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("git@bitbucket.org:tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("git@bitbucket.org:tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@bitbucket.org:tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(BuildChooserSetting.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "test-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_branch_norev_anon__when__build__then__scmBuilt() throws Exception {
        BranchSCMHead head = new BranchSCMHead("test-branch");
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, null, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.org/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket.org/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.org/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__cloud_branch_norev_userpass__when__build__then__scmBuilt() throws Exception {
        BranchSCMHead head = new BranchSCMHead("test-branch");
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, null, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.org/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket.org/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.org/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__cloud_branch_norev_userkey__when__build__then__scmBuilt() throws Exception {
        BranchSCMHead head = new BranchSCMHead("test-branch");
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, null, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("git@bitbucket.org:tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("git@bitbucket.org:tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@bitbucket.org:tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__server_branch_rev_anon__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        BranchSCMHead head = new BranchSCMHead("test-branch");
        AbstractGitSCMSource.SCMRevisionImpl revision =
                new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(GitSCMSourceDefaults.class),
                instanceOf(BuildChooserSetting.class)
        ));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "test-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }


    @Test
    public void given__server_branch_rev_userpass__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        BranchSCMHead head = new BranchSCMHead("test-branch");
        AbstractGitSCMSource.SCMRevisionImpl revision =
                new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(GitSCMSourceDefaults.class),
                instanceOf(BuildChooserSetting.class)
        ));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "test-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__server_branch_rev_userkey__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        BranchSCMHead head = new BranchSCMHead("test-branch");
        AbstractGitSCMSource.SCMRevisionImpl revision =
                new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(GitSCMSourceDefaults.class),
                instanceOf(BuildChooserSetting.class)
        ));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "test-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__server_branch_rev_userkey_different_clone_url__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://www.bitbucket.test/web");
        BranchSCMHead head = new BranchSCMHead("test-branch");
        AbstractGitSCMSource.SCMRevisionImpl revision =
                new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://www.bitbucket.test/web"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(),
                is("https://www.bitbucket.test/web/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@web.bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(),
                is("https://www.bitbucket.test/web/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(GitSCMSourceDefaults.class),
                instanceOf(BuildChooserSetting.class)
        ));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "test-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__server_branch_norev_anon__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        BranchSCMHead head = new BranchSCMHead("test-branch");
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, null, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__server_branch_norev_userpass__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        BranchSCMHead head = new BranchSCMHead("test-branch");
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, null, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__server_branch_norev_userkey__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        BranchSCMHead head = new BranchSCMHead("test-branch");
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, null, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__server_branch_norev_userkey_different_clone_url__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://www.bitbucket.test/web");
        BranchSCMHead head = new BranchSCMHead("test-branch");
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, null, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://www.bitbucket.test/web"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(),
                is("https://www.bitbucket.test/web/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@www.bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(),
                is("https://www.bitbucket.test/web/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__cloud_pullHead_rev_anon__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision<AbstractGitSCMSource.SCMRevisionImpl> revision =
                new PullRequestSCMRevision<>(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/qa-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(BuildChooserSetting.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "qa-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_pullHead_rev_userpass__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision<AbstractGitSCMSource.SCMRevisionImpl> revision =
                new PullRequestSCMRevision<>(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/qa-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(BuildChooserSetting.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "qa-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_pullHead_rev_userkey__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision<AbstractGitSCMSource.SCMRevisionImpl> revision =
                new PullRequestSCMRevision<>(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("git@bitbucket.org:qa/qa-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("git@bitbucket.org:qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@bitbucket.org:qa/qa-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/qa-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(BuildChooserSetting.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "qa-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_pullHead_rev_anon_sshtrait_anon__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision<AbstractGitSCMSource.SCMRevisionImpl> revision =
                new PullRequestSCMRevision<>(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait(null);
        sshTrait.decorateBuilder(instance);

        GitSCM actual = instance.build();
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.remote(), is("git@bitbucket.org:qa/qa-repo.git"));

        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("git@bitbucket.org:qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@bitbucket.org:qa/qa-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/qa-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(BuildChooserSetting.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "qa-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_pullHead_rev_userpass_sshtrait_anon__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision<AbstractGitSCMSource.SCMRevisionImpl> revision =
                new PullRequestSCMRevision<>(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait(null);
        sshTrait.decorateBuilder(instance);

        GitSCM actual = instance.build();
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.remote(), is("git@bitbucket.org:qa/qa-repo.git"));

        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("git@bitbucket.org:qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@bitbucket.org:qa/qa-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/qa-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(BuildChooserSetting.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "qa-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_pullHead_rev_userkey_sshtrait_anon__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision<AbstractGitSCMSource.SCMRevisionImpl> revision =
                new PullRequestSCMRevision<>(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("git@bitbucket.org:qa/qa-repo.git"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait(null);
        sshTrait.decorateBuilder(instance);

        GitSCM actual = instance.build();
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.remote(), is("git@bitbucket.org:qa/qa-repo.git"));

        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("git@bitbucket.org:qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@bitbucket.org:qa/qa-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/qa-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(BuildChooserSetting.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "qa-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_pullHead_rev_anon_sshtrait_userkey__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision<AbstractGitSCMSource.SCMRevisionImpl> revision =
                new PullRequestSCMRevision<>(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait("user-key");
        sshTrait.decorateBuilder(instance);

        GitSCM actual = instance.build();
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.remote(), is("git@bitbucket.org:qa/qa-repo.git"));

        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("git@bitbucket.org:qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@bitbucket.org:qa/qa-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/qa-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(BuildChooserSetting.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "qa-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_pullHead_rev_userpass_sshtrait_userkey__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision<AbstractGitSCMSource.SCMRevisionImpl> revision =
                new PullRequestSCMRevision<>(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait("user-key");
        sshTrait.decorateBuilder(instance);

        GitSCM actual = instance.build();
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.remote(), is("git@bitbucket.org:qa/qa-repo.git"));

        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("git@bitbucket.org:qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@bitbucket.org:qa/qa-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/qa-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(BuildChooserSetting.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "qa-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_pullHead_rev_userkey_sshtrait_userkey__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision<AbstractGitSCMSource.SCMRevisionImpl> revision =
                new PullRequestSCMRevision<>(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("git@bitbucket.org:qa/qa-repo.git"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait("user-key");
        sshTrait.decorateBuilder(instance);

        GitSCM actual = instance.build();
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.remote(), is("git@bitbucket.org:qa/qa-repo.git"));

        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("git@bitbucket.org:qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@bitbucket.org:qa/qa-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/qa-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(BuildChooserSetting.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "qa-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__cloud_pullHead_norev_anon__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, null, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/qa-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__cloud_pullHead_norev_userpass__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, null, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/qa-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__cloud_pullHead_norev_userkey__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, null, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("git@bitbucket.org:qa/qa-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("git@bitbucket.org:qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@bitbucket.org:qa/qa-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/qa-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__server_pullHead_rev_anon__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision<AbstractGitSCMSource.SCMRevisionImpl> revision =
                new PullRequestSCMRevision<>(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(BuildChooserSetting.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "qa-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }


    @Test
    public void given__server_pullHead_rev_userpass__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision<AbstractGitSCMSource.SCMRevisionImpl> revision =
                new PullRequestSCMRevision<>(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(BuildChooserSetting.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "qa-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__server_pullHead_rev_userkey__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision<AbstractGitSCMSource.SCMRevisionImpl> revision =
                new PullRequestSCMRevision<>(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(BuildChooserSetting.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "qa-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__server_pullHead_rev_userkey_different_clone_url__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://www.bitbucket.test/web");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision<AbstractGitSCMSource.SCMRevisionImpl> revision =
                new PullRequestSCMRevision<>(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://www.bitbucket.test/web"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(),
                is("https://www.bitbucket.test/web/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@www.bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(),
                is("https://www.bitbucket.test/web/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(BuildChooserSetting.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "qa-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }
    @Test
    public void given__server_pullHead_norev_anon__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, null, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__server_pullHead_norev_userpass__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, null, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__server_pullHead_norev_userkey__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, null, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__server_pullHead_norev_userkey__when_different_clone_url__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://www.bitbucket.test/web");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, null, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://www.bitbucket.test/web"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(),
                is("https://www.bitbucket.test/web/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@www.bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(),
                is("https://www.bitbucket.test/web/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), contains(instanceOf(GitSCMSourceDefaults.class)));
    }

    @Test
    public void given__cloud_pullMerge_rev_anon__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        PullRequestSCMRevision<AbstractGitSCMSource.SCMRevisionImpl> revision =
                new PullRequestSCMRevision<>(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(2));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        config = actual.getUserRemoteConfigs().get(1);
        assertThat(config.getName(), is("upstream"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/upstream/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket.org/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/qa-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        origin = actual.getRepositoryByName("upstream");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.org/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/upstream/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(GitSCMSourceDefaults.class),
                instanceOf(BuildChooserSetting.class),
                instanceOf(MergeWithGitSCMExtension.class)
        ));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser, notNullValue());
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "test-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/upstream/test-branch"));
        assertThat(merge.getBaseHash(), is("deadbeefcafebabedeadbeefcafebabedeadbeef"));
    }

    @Test
    public void given__cloud_pullMerge_rev_userpass__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        PullRequestSCMRevision<AbstractGitSCMSource.SCMRevisionImpl> revision =
                new PullRequestSCMRevision<>(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(2));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        config = actual.getUserRemoteConfigs().get(1);
        assertThat(config.getName(), is("upstream"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/upstream/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket.org/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/qa-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        origin = actual.getRepositoryByName("upstream");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.org/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/upstream/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(GitSCMSourceDefaults.class),
                instanceOf(BuildChooserSetting.class),
                instanceOf(MergeWithGitSCMExtension.class)
        ));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser, notNullValue());
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "test-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/upstream/test-branch"));
        assertThat(merge.getBaseHash(), is("deadbeefcafebabedeadbeefcafebabedeadbeef"));
    }

    @Test
    public void given__cloud_pullMerge_rev_userkey__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        PullRequestSCMRevision<AbstractGitSCMSource.SCMRevisionImpl> revision =
                new PullRequestSCMRevision<>(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("git@bitbucket.org:qa/qa-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(2));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("git@bitbucket.org:qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        config = actual.getUserRemoteConfigs().get(1);
        assertThat(config.getName(), is("upstream"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/upstream/test-branch"));
        assertThat(config.getUrl(), is("git@bitbucket.org:tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@bitbucket.org:qa/qa-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/qa-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        origin = actual.getRepositoryByName("upstream");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@bitbucket.org:tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/upstream/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(GitSCMSourceDefaults.class),
                instanceOf(BuildChooserSetting.class),
                instanceOf(MergeWithGitSCMExtension.class)
        ));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser, notNullValue());
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "test-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/upstream/test-branch"));
        assertThat(merge.getBaseHash(), is("deadbeefcafebabedeadbeefcafebabedeadbeef"));
    }

    @Test
    public void given__cloud_pullMerge_norev_anon__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, null, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(2));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        config = actual.getUserRemoteConfigs().get(1);
        assertThat(config.getName(), is("upstream"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/upstream/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket.org/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/qa-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        origin = actual.getRepositoryByName("upstream");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.org/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/upstream/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(GitSCMSourceDefaults.class),
                instanceOf(MergeWithGitSCMExtension.class)
        ));
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge.getBaseName(), is("remotes/upstream/test-branch"));
        assertThat(merge.getBaseHash(), is(nullValue()));
    }

    @Test
    public void given__cloud_pullMerge_norev_userpass__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, null, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(2));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        config = actual.getUserRemoteConfigs().get(1);
        assertThat(config.getName(), is("upstream"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/upstream/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket.org/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/qa-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        origin = actual.getRepositoryByName("upstream");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.org/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/upstream/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(GitSCMSourceDefaults.class),
                instanceOf(MergeWithGitSCMExtension.class)
        ));
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge.getBaseName(), is("remotes/upstream/test-branch"));
        assertThat(merge.getBaseHash(), is(nullValue()));
    }

    @Test
    public void given__cloud_pullMerge_norev_userkey__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, null, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.org/tester/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("git@bitbucket.org:qa/qa-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.org/qa/qa-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(2));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("git@bitbucket.org:qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        config = actual.getUserRemoteConfigs().get(1);
        assertThat(config.getName(), is("upstream"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/upstream/test-branch"));
        assertThat(config.getUrl(), is("git@bitbucket.org:tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@bitbucket.org:qa/qa-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/qa-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        origin = actual.getRepositoryByName("upstream");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("git@bitbucket.org:tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/upstream/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(GitSCMSourceDefaults.class),
                instanceOf(MergeWithGitSCMExtension.class)
        ));
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge.getBaseName(), is("remotes/upstream/test-branch"));
        assertThat(merge.getBaseHash(), is(nullValue()));
    }

    @Test
    public void given__server_pullMerge_rev_anon__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        PullRequestSCMRevision<AbstractGitSCMSource.SCMRevisionImpl> revision =
                new PullRequestSCMRevision<>(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(2));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        config = actual.getUserRemoteConfigs().get(1);
        assertThat(config.getName(), is("upstream"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/upstream/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        origin = actual.getRepositoryByName("upstream");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/upstream/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(MergeWithGitSCMExtension.class),
                instanceOf(BuildChooserSetting.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser, notNullValue());
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "test-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/upstream/test-branch"));
        assertThat(merge.getBaseHash(), is("deadbeefcafebabedeadbeefcafebabedeadbeef"));
    }


    @Test
    public void given__server_pullMerge_rev_userpass__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        PullRequestSCMRevision<AbstractGitSCMSource.SCMRevisionImpl> revision =
                new PullRequestSCMRevision<>(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(2));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        config = actual.getUserRemoteConfigs().get(1);
        assertThat(config.getName(), is("upstream"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/upstream/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        origin = actual.getRepositoryByName("upstream");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/upstream/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(MergeWithGitSCMExtension.class),
                instanceOf(BuildChooserSetting.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser, notNullValue());
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "test-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/upstream/test-branch"));
        assertThat(merge.getBaseHash(), is("deadbeefcafebabedeadbeefcafebabedeadbeef"));
    }

    @Test
    public void given__server_pullMerge_rev_userkey__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        PullRequestSCMRevision<AbstractGitSCMSource.SCMRevisionImpl> revision =
                new PullRequestSCMRevision<>(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(2));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        config = actual.getUserRemoteConfigs().get(1);
        assertThat(config.getName(), is("upstream"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/upstream/test-branch"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        origin = actual.getRepositoryByName("upstream");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/upstream/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(MergeWithGitSCMExtension.class),
                instanceOf(BuildChooserSetting.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser, notNullValue());
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "test-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/upstream/test-branch"));
        assertThat(merge.getBaseHash(), is("deadbeefcafebabedeadbeefcafebabedeadbeef"));
    }

    @Test
    public void given__server_pullMerge_rev_userkey__when_different_clone_url__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://www.bitbucket.test/web");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        PullRequestSCMRevision<AbstractGitSCMSource.SCMRevisionImpl> revision =
                new PullRequestSCMRevision<>(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://www.bitbucket.test/web"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(),
                is("https://www.bitbucket.test/web/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@www.bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(),
                is("https://www.bitbucket.test/web/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(2));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        config = actual.getUserRemoteConfigs().get(1);
        assertThat(config.getName(), is("upstream"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/upstream/test-branch"));
        assertThat(config.getUrl(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        origin = actual.getRepositoryByName("upstream");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/upstream/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(MergeWithGitSCMExtension.class),
                instanceOf(BuildChooserSetting.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser, notNullValue());
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "test-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/upstream/test-branch"));
        assertThat(merge.getBaseHash(), is("deadbeefcafebabedeadbeefcafebabedeadbeef"));
    }

    @Test
    public void given__server_pullMerge_norev_anon__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, null, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(2));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        config = actual.getUserRemoteConfigs().get(1);
        assertThat(config.getName(), is("upstream"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/upstream/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        origin = actual.getRepositoryByName("upstream");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/upstream/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(MergeWithGitSCMExtension.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/upstream/test-branch"));
        assertThat(merge.getBaseHash(), is(nullValue()));
    }

    @Test
    public void given__server_pullMerge_norev_userpass__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, null, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(2));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        config = actual.getUserRemoteConfigs().get(1);
        assertThat(config.getName(), is("upstream"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/upstream/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        origin = actual.getRepositoryByName("upstream");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/upstream/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(MergeWithGitSCMExtension.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/upstream/test-branch"));
        assertThat(merge.getBaseHash(), is(nullValue()));
    }

    @Test
    public void given__server_pullMerge_norev_userkey__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, null, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(), is("https://bitbucket.test/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(2));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        config = actual.getUserRemoteConfigs().get(1);
        assertThat(config.getName(), is("upstream"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/upstream/test-branch"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        origin = actual.getRepositoryByName("upstream");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/upstream/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(MergeWithGitSCMExtension.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/upstream/test-branch"));
        assertThat(merge.getBaseHash(), is(nullValue()));
    }

    @Test
    public void given__server_pullMerge_norev_userkey_different_clone_url__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://www.bitbucket.test/web");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, null, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://www.bitbucket.test/web"));
        assertThat(instance.browser(), instanceOf(BitbucketWeb.class));
        assertThat(instance.browser().getRepoUrl(),
                is("https://www.bitbucket.test/web/projects/tester/repos/test-repo"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://tester@www.bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git")
        ));
        assertThat(instance.remote(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));

        GitSCM actual = instance.build();
        assertThat(actual.getBrowser(), instanceOf(BitbucketWeb.class));
        assertThat(actual.getBrowser().getRepoUrl(),
                is("https://www.bitbucket.test/web/projects/tester/repos/test-repo"));
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(2));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        config = actual.getUserRemoteConfigs().get(1);
        assertThat(config.getName(), is("upstream"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/upstream/test-branch"));
        assertThat(config.getUrl(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        origin = actual.getRepositoryByName("upstream");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/upstream/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(MergeWithGitSCMExtension.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/upstream/test-branch"));
        assertThat(merge.getBaseHash(), is(nullValue()));
    }

    private static <T extends GitSCMExtension> T getExtension(GitSCM scm, Class<T> type) {
        for (GitSCMExtension e : scm.getExtensions()) {
            if (type.isInstance(e)) {
                return type.cast(e);
            }
        }
        return null;
    }

}
