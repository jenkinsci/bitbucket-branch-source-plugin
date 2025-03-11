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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudCommit;
import com.cloudbees.jenkins.plugins.bitbucket.impl.extension.FallbackToOtherRepositoryGitSCMExtension;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.branch.BitbucketServerCommit;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.browser.BitbucketServer;
import hudson.plugins.git.browser.BitbucketWeb;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.BuildChooserSetting;
import hudson.util.LogTaskListener;
import java.io.IOException;
import java.net.URL;
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
import jenkins.plugins.git.traits.RefSpecsSCMSourceTrait;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.assertj.core.api.Assertions;
import org.eclipse.jgit.transport.RemoteConfig;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class BitbucketGitSCMBuilderTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
    private BitbucketSCMSource source;
    private WorkflowMultiBranchProject owner;

    @Before
    public void setUp() throws Exception {
        owner = j.createProject(WorkflowMultiBranchProject.class);
        source = new BitbucketSCMSource( "tester", "test-repo");
        owner.setSourcesList(Collections.singletonList(new BranchSource(source)));
        source.setOwner(owner);
        Credentials userPasswordCredential = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "user-pass", null, "git-user", "git-secret");
        Credentials sshPrivateKeyCredential = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, "user-key", "git",
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource("privateKey"), null, null);
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(Collections.singletonMap(Domain.global(),
                Arrays.<Credentials>asList(userPasswordCredential, sshPrivateKeyCredential)));
    }

    @After
    public void tearDown() throws IOException, InterruptedException {
        SystemCredentialsProvider.getInstance()
                .setDomainCredentialsMap(Collections.<Domain, List<Credentials>>emptyMap());
        owner.delete();
        BitbucketMockApiFactory.clear();
    }

    @Test
    public void given_server_endpoint_than_use_BitbucketServer_browser() throws Exception {
        source.setServerUrl("https://www.bitbucket.test/web");
        BranchSCMHead head = new BranchSCMHead("test-branch");
        BitbucketGitSCMRevision revision = new BitbucketGitSCMRevision(head, new BitbucketServerCommit("046d9a3c1532acf4cf08fe93235c00e4d673c1d2"));

        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source, head, revision, null);
        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());

        Assertions.assertThat(instance.browser())
            .isInstanceOf(BitbucketServer.class)
            .satisfies(browser -> Assertions.assertThat(browser.getRepoUrl()).isEqualTo("https://www.bitbucket.test/web/projects/tester/repos/test-repo"));

        GitSCM actual = instance.build();
        Assertions.assertThat(actual.getBrowser())
            .isInstanceOf(BitbucketServer.class)
            .satisfies(browser -> Assertions.assertThat(browser.getRepoUrl()).isEqualTo("https://www.bitbucket.test/web/projects/tester/repos/test-repo"));
    }

    @Test
    public void given_cloud_endpoint_than_use_BitbucketWeb_browser() throws Exception {
        BranchSCMHead head = new BranchSCMHead("test-branch");
        BitbucketGitSCMRevision revision = new BitbucketGitSCMRevision(head, new BitbucketCloudCommit(null, null, "046d9a3c1532acf4cf08fe93235c00e4d673c1d2", null));

        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source, head, revision, null);
        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());

        Assertions.assertThat(instance.browser())
            .isInstanceOf(BitbucketWeb.class)
            .satisfies(browser -> Assertions.assertThat(browser.getRepoUrl()).isEqualTo("https://bitbucket.org/tester/test-repo"));

        GitSCM actual = instance.build();
        Assertions.assertThat(actual.getBrowser())
            .isInstanceOf(BitbucketWeb.class)
            .satisfies(browser -> Assertions.assertThat(browser.getRepoUrl()).isEqualTo("https://bitbucket.org/tester/test-repo"));
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
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("https://bitbucket.org/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));

        GitSCM actual = instance.build();
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
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("https://bitbucket.org/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));

        GitSCM actual = instance.build();
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
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source, head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("ssh://git@bitbucket.org/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));

        GitSCM actual = instance.build();
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.org/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.org/tester/test-repo.git"));
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
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("https://bitbucket.org/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));

        GitSCM actual = instance.build();
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
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("https://bitbucket.org/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));

        GitSCM actual = instance.build();
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
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source, head, null, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("ssh://git@bitbucket.org/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));

        GitSCM actual = instance.build();
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.org/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.org/tester/test-repo.git"));
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
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));

        GitSCM actual = instance.build();
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
    public void given__server_branch_rev_anon_with_extra_refSpec_when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        BranchSCMHead head = new BranchSCMHead("test-branch");
        AbstractGitSCMSource.SCMRevisionImpl revision =
                new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, null);
        instance.withTrait(new RefSpecsSCMSourceTrait("+refs/heads/*:refs/remotes/@{remote}/*"));
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(instance.refSpecs(), hasSize(2));
        assertThat(instance.refSpecs(), contains(
            "+refs/heads/*:refs/remotes/@{remote}/*",
            "+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"
        ));

        GitSCM actual = instance.build();
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/*:refs/remotes/origin/* +refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(2));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/*"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/*"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).isWildcard(), is(false));
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
    public void given__server_withMirror_branch_rev_anon__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        BranchSCMHead head = new BranchSCMHead("test-branch");
        AbstractGitSCMSource.SCMRevisionImpl revision =
                new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of(
                new BitbucketHref("http", "https://bitbucket-mirror.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket-mirror.test:7999/tester/test-repo.git")
            )
        );
        assertThat(instance.remote(), is("https://bitbucket-mirror.test/scm/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));

        GitSCM actual = instance.build();
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket-mirror.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket-mirror.test/scm/tester/test-repo.git"));
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
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));

        GitSCM actual = instance.build();
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
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source, head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));

        GitSCM actual = instance.build();
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
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source, head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://www.bitbucket.test/web"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://web.bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));

        GitSCM actual = instance.build();
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
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));

        GitSCM actual = instance.build();
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
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));

        GitSCM actual = instance.build();
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
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source, head, null, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));

        GitSCM actual = instance.build();
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
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source, head, null, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://www.bitbucket.test/web"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://www.bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"));

        GitSCM actual = instance.build();
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
        PullRequestSCMRevision revision =
                new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));

        GitSCM actual = instance.build();
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
        PullRequestSCMRevision revision =
                new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));

        GitSCM actual = instance.build();
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
        PullRequestSCMRevision revision =
                new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source, head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));

        GitSCM actual = instance.build();
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
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
        PullRequestSCMRevision revision =
                new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait(null);
        sshTrait.decorateBuilder(instance);

        GitSCM actual = instance.build();
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.remote(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));

        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
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
        PullRequestSCMRevision revision =
                new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait(null);
        sshTrait.decorateBuilder(instance);

        GitSCM actual = instance.build();
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.remote(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));

        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
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
        PullRequestSCMRevision revision =
                new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source, head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait(null);
        sshTrait.decorateBuilder(instance);

        GitSCM actual = instance.build();
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.remote(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));

        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
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
        PullRequestSCMRevision revision =
                new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait("user-key");
        sshTrait.decorateBuilder(instance);

        GitSCM actual = instance.build();
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.remote(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));

        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
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
        PullRequestSCMRevision revision =
                new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait("user-key");
        sshTrait.decorateBuilder(instance);

        GitSCM actual = instance.build();
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.remote(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));

        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
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
        PullRequestSCMRevision revision =
                new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait("user-key");
        sshTrait.decorateBuilder(instance);

        GitSCM actual = instance.build();
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.remote(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));

        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
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
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));

        GitSCM actual = instance.build();
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
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));

        GitSCM actual = instance.build();
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
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source, head, null, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));

        GitSCM actual = instance.build();
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
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
        PullRequestSCMRevision revision =
                new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));

        GitSCM actual = instance.build();
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
    public void given__server_withMirror_pullHead_rev_anon__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision revision =
                new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of(
                new BitbucketHref("http", "https://bitbucket-mirror.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket-mirror.test:7999/tester/test-repo.git")
            )
        );
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));

        GitSCM actual = instance.build();
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
    public void given__server_pullHead_defaultOrigin_rev_anon__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "tester", "test-repo", "pr-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), SCMHeadOrigin.DEFAULT,
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision revision =
                new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/pr-branch:refs/remotes/@{remote}/pr-branch"));

        GitSCM actual = instance.build();
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/pr-branch:refs/remotes/origin/pr-branch"));
        assertThat(config.getUrl(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/pr-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/pr-branch"));
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
                .getCandidateRevisions(false, "pr-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__server_withMirror_pullHead_defaultOrigin_rev_anon__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "tester", "test-repo", "pr-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), SCMHeadOrigin.DEFAULT,
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision revision =
                new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is(head));
        assertThat(instance.revision(), is(revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of(
                new BitbucketHref("http", "https://bitbucket-mirror.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket-mirror.test:7999/tester/test-repo.git")
            )
        );
        assertThat(instance.remote(), is("https://bitbucket-mirror.test/scm/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/pr-branch:refs/remotes/@{remote}/pr-branch"));

        GitSCM actual = instance.build();
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/pr-branch:refs/remotes/origin/pr-branch"));
        assertThat(config.getUrl(), is("https://bitbucket-mirror.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket-mirror.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/pr-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/pr-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(BuildChooserSetting.class),
                instanceOf(GitSCMSourceDefaults.class),
                instanceOf(FallbackToOtherRepositoryGitSCMExtension.class)
        ));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
                (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
                .getCandidateRevisions(false, "pr-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                        Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__server_pullMerge_defaultOrigin_rev_anon__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "tester", "test-repo", "pr-branch", "1", "a fake title",
            new BranchSCMHead("test-branch"), SCMHeadOrigin.DEFAULT,
            ChangeRequestCheckoutStrategy.MERGE);
        PullRequestSCMRevision revision =
            new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
            head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
            instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(
            instance.refSpecs(),
            contains(
                "+refs/heads/pr-branch:refs/remotes/@{remote}/pr-branch",
                "+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"
            )
        );

        GitSCM actual = instance.build();
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/pr-branch:refs/remotes/origin/pr-branch +refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(2));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/pr-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/pr-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(origin.getFetchRefSpecs().get(1).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
            instanceOf(BuildChooserSetting.class),
            instanceOf(GitSCMSourceDefaults.class),
            instanceOf(MergeWithGitSCMExtension.class)
        ));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
            (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
            .getCandidateRevisions(false, "pr-branch", Mockito.mock(GitClient.class), new LogTaskListener(
                Logger.getAnonymousLogger(), Level.FINEST), null, null);
        assertThat(revisions, hasSize(1));
        assertThat(revisions.iterator().next().getSha1String(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__server_withMirror_pullMerge_defaultOrigin_rev_anon__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "tester", "test-repo", "pr-branch", "1", "a fake title",
            new BranchSCMHead("test-branch"), SCMHeadOrigin.DEFAULT,
            ChangeRequestCheckoutStrategy.MERGE);
        PullRequestSCMRevision revision =
            new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
            head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
            instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of(
                new BitbucketHref("http", "https://bitbucket-mirror.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket-mirror.test:7999/tester/test-repo.git")
            )
        );
        assertThat(instance.remote(), is("https://bitbucket-mirror.test/scm/tester/test-repo.git"));
        assertThat(
            instance.refSpecs(),
            contains(
                "+refs/heads/pr-branch:refs/remotes/@{remote}/pr-branch",
                "+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"
            )
        );

        GitSCM actual = instance.build();
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/pr-branch:refs/remotes/origin/pr-branch +refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket-mirror.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is(nullValue()));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket-mirror.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(2));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/pr-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/pr-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(origin.getFetchRefSpecs().get(1).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
            instanceOf(BuildChooserSetting.class),
            instanceOf(GitSCMSourceDefaults.class),
            instanceOf(MergeWithGitSCMExtension.class),
            instanceOf(FallbackToOtherRepositoryGitSCMExtension.class)
        ));
        BuildChooserSetting chooser = getExtension(actual, BuildChooserSetting.class);
        assertThat(chooser.getBuildChooser(), instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        AbstractGitSCMSource.SpecificRevisionBuildChooser revChooser =
            (AbstractGitSCMSource.SpecificRevisionBuildChooser) chooser.getBuildChooser();
        Collection<Revision> revisions = revChooser
            .getCandidateRevisions(false, "pr-branch", Mockito.mock(GitClient.class), new LogTaskListener(
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
        PullRequestSCMRevision revision =
                new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));

        GitSCM actual = instance.build();
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
        PullRequestSCMRevision revision =
                new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source, head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));

        GitSCM actual = instance.build();
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
        PullRequestSCMRevision revision =
                new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source, head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://www.bitbucket.test/web"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://www.bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));

        GitSCM actual = instance.build();
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
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));

        GitSCM actual = instance.build();
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
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));

        GitSCM actual = instance.build();
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
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source, head, null, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));

        GitSCM actual = instance.build();
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
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source, head, null, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://www.bitbucket.test/web"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://www.bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1"));

        GitSCM actual = instance.build();
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
        PullRequestSCMRevision revision =
                new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));

        GitSCM actual = instance.build();
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
        PullRequestSCMRevision revision =
                new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));

        GitSCM actual = instance.build();
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
        PullRequestSCMRevision revision =
                new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source, head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));

        GitSCM actual = instance.build();
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(2));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        config = actual.getUserRemoteConfigs().get(1);
        assertThat(config.getName(), is("upstream"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/upstream/test-branch"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.org/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/qa-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        origin = actual.getRepositoryByName("upstream");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.org/tester/test-repo.git"));
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
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));

        GitSCM actual = instance.build();
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
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("https://bitbucket.org/qa/qa-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));

        GitSCM actual = instance.build();
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
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source, head, null, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.org"));

        instance.withCloneLinks(buildCloneLinks(), Collections.emptyList());
        assertThat(instance.remote(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
        assertThat(instance.refSpecs(), contains("+refs/heads/qa-branch:refs/remotes/@{remote}/PR-1"));

        GitSCM actual = instance.build();
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(2));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/heads/qa-branch:refs/remotes/origin/PR-1"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        config = actual.getUserRemoteConfigs().get(1);
        assertThat(config.getName(), is("upstream"));
        assertThat(config.getRefspec(), is("+refs/heads/test-branch:refs/remotes/upstream/test-branch"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.org/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.org/qa/qa-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(1));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/heads/qa-branch"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        origin = actual.getRepositoryByName("upstream");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.org/tester/test-repo.git"));
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
        PullRequestSCMRevision revision =
                new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(
            instance.refSpecs(),
            contains(
                "+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1",
                "+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"
            )
        );

        GitSCM actual = instance.build();
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1 +refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), nullValue());
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(2));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(origin.getFetchRefSpecs().get(1).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).isWildcard(), is(false));
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
        assertThat(merge.getBaseName(), is("remotes/origin/test-branch"));
        assertThat(merge.getBaseHash(), is("deadbeefcafebabedeadbeefcafebabedeadbeef"));
    }


    @Test
    public void given__server_pullMerge_rev_userpass__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        PullRequestSCMRevision revision =
                new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source,
                head, revision, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(
            instance.refSpecs(),
            contains(
                "+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1",
                "+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"
            )
        );

        GitSCM actual = instance.build();
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1 +refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(2));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(origin.getFetchRefSpecs().get(1).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).isWildcard(), is(false));
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
        assertThat(merge.getBaseName(), is("remotes/origin/test-branch"));
        assertThat(merge.getBaseHash(), is("deadbeefcafebabedeadbeefcafebabedeadbeef"));
    }

    @Test
    public void given__server_pullMerge_rev_userkey__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        PullRequestSCMRevision revision =
                new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source, head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(
            instance.refSpecs(),
            contains(
                "+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1",
                "+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"
            )
        );

        GitSCM actual = instance.build();
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1 +refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(2));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(origin.getFetchRefSpecs().get(1).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).isWildcard(), is(false));
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
        assertThat(merge.getBaseName(), is("remotes/origin/test-branch"));
        assertThat(merge.getBaseHash(), is("deadbeefcafebabedeadbeefcafebabedeadbeef"));
    }

    @Test
    public void given__server_pullMerge_rev_userkey__when_different_clone_url__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://www.bitbucket.test/web");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        PullRequestSCMRevision revision =
                new PullRequestSCMRevision(head, new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new AbstractGitSCMSource.SCMRevisionImpl(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source, head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://www.bitbucket.test/web"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://www.bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(
            instance.refSpecs(),
            contains(
                "+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1",
                "+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"
            )
        );

        GitSCM actual = instance.build();
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1 +refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(2));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(origin.getFetchRefSpecs().get(1).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).isWildcard(), is(false));
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
        assertThat(merge.getBaseName(), is("remotes/origin/test-branch"));
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
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(
            instance.refSpecs(),
            contains(
                "+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1",
                "+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"
            )
        );

        GitSCM actual = instance.build();
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1 +refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), nullValue());
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(2));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(origin.getFetchRefSpecs().get(1).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(MergeWithGitSCMExtension.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/origin/test-branch"));
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
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(
            instance.refSpecs(),
            contains(
                "+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1",
                "+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"
            )
        );

        GitSCM actual = instance.build();
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1 +refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-pass"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("https://bitbucket.test/scm/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(2));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(origin.getFetchRefSpecs().get(1).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(MergeWithGitSCMExtension.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/origin/test-branch"));
        assertThat(merge.getBaseHash(), is(nullValue()));
    }

    @Test
    public void given__server_pullMerge_norev_userkey__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://bitbucket.test");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source, head, null, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://bitbucket.test"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(
            instance.refSpecs(),
            contains(
                "+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1",
                "+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"
            )
        );

        GitSCM actual = instance.build();
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1 +refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@bitbucket.test:7999/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(2));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(origin.getFetchRefSpecs().get(1).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(MergeWithGitSCMExtension.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/origin/test-branch"));
        assertThat(merge.getBaseHash(), is(nullValue()));
    }

    @Test
    public void given__server_pullMerge_norev_userkey_different_clone_url__when__build__then__scmBuilt() throws Exception {
        source.setServerUrl("https://www.bitbucket.test/web");
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch"), new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.MERGE);
        BitbucketGitSCMBuilder instance = new BitbucketGitSCMBuilder(source, head, null, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat("expecting dummy value until clone links provided or withBitbucketRemote called",
                instance.remote(), is("https://www.bitbucket.test/web"));

        instance.withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://www.bitbucket.test/scm/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git")
            ),
            List.of()
        );
        assertThat(instance.remote(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(
            instance.refSpecs(),
            contains(
                "+refs/pull-requests/1/from:refs/remotes/@{remote}/PR-1",
                "+refs/heads/test-branch:refs/remotes/@{remote}/test-branch"
            )
        );

        GitSCM actual = instance.build();
        assertThat(actual.getGitTool(), nullValue());
        assertThat(actual.getUserRemoteConfigs(), hasSize(1));
        UserRemoteConfig config = actual.getUserRemoteConfigs().get(0);
        assertThat(config.getName(), is("origin"));
        assertThat(config.getRefspec(), is("+refs/pull-requests/1/from:refs/remotes/origin/PR-1 +refs/heads/test-branch:refs/remotes/origin/test-branch"));
        assertThat(config.getUrl(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(config.getCredentialsId(), is("user-key"));
        RemoteConfig origin = actual.getRepositoryByName("origin");
        assertThat(origin, notNullValue());
        assertThat(origin.getURIs(), hasSize(1));
        assertThat(origin.getURIs().get(0).toString(), is("ssh://git@ssh.bitbucket.test:7999/tester/test-repo.git"));
        assertThat(origin.getFetchRefSpecs(), hasSize(2));
        assertThat(origin.getFetchRefSpecs().get(0).getSource(), is("refs/pull-requests/1/from"));
        assertThat(origin.getFetchRefSpecs().get(0).getDestination(), is("refs/remotes/origin/PR-1"));
        assertThat(origin.getFetchRefSpecs().get(0).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(0).isWildcard(), is(false));
        assertThat(origin.getFetchRefSpecs().get(1).getSource(), is("refs/heads/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).getDestination(), is("refs/remotes/origin/test-branch"));
        assertThat(origin.getFetchRefSpecs().get(1).isForceUpdate(), is(true));
        assertThat(origin.getFetchRefSpecs().get(1).isWildcard(), is(false));
        assertThat(actual.getExtensions(), containsInAnyOrder(
                instanceOf(MergeWithGitSCMExtension.class),
                instanceOf(GitSCMSourceDefaults.class))
        );
        MergeWithGitSCMExtension merge = getExtension(actual, MergeWithGitSCMExtension.class);
        assertThat(merge, notNullValue());
        assertThat(merge.getBaseName(), is("remotes/origin/test-branch"));
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

    private List<BitbucketHref> buildCloneLinks() throws Exception {
        URL serverURL = new URL(source.getServerUrl());
        return List.of(
            new BitbucketHref(serverURL.getProtocol(), String.format("%s/%s/%s.git", source.getServerUrl(), source.getRepoOwner(), source.getRepository())),
            new BitbucketHref("ssh", String.format("ssh://git@%s/%s/%s.git", serverURL.getHost(), source.getRepoOwner(), source.getRepository()))
        );
    }
}
