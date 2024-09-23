package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerAPIClient;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.impl.BuildChooserSetting;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMSourceDefaults;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.junit.MockitoJUnitRunner;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests different scenarios of the
 * {@link BitbucketSCMSource#build(jenkins.scm.api.SCMHead, jenkins.scm.api.SCMRevision)} method.
 */
@RunWith(MockitoJUnitRunner.class)
public class BitbucketSCMSourceBuildTest {

    private static final String CLOUD_REPO_OWNER = "cloudbeers";
    private static final String REPO_NAME = "stunning-adventure";
    private static final String BRANCH_NAME = "branch1";
    private static final String COMMIT_HASH = "e851558f77c098d21af6bb8cc54a423f7cf12147";

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-73471")
    public void buildWhenSetSSHCheckoutTraitThenNoAuthenticatorExtension() throws IOException, InterruptedException {
        StandardUsernameCredentials userPassCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
            "user-pass", null, "user", "pass");
        CredentialsProvider.lookupStores(j.jenkins).iterator().next()
            .addCredentials(Domain.global(), userPassCredentials);
        StandardUsernameCredentials sshCredentials = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, "user-key", "user",
            new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(""), null, null);
        CredentialsProvider.lookupStores(j.jenkins).iterator().next()
            .addCredentials(Domain.global(), sshCredentials);

        WorkflowMultiBranchProject owner = j.createProject(WorkflowMultiBranchProject.class);
        BitbucketSCMSource instance = new BitbucketSCMSource(CLOUD_REPO_OWNER, REPO_NAME);
        instance.setOwner(owner);
        instance.setCredentialsId(userPassCredentials.getId());
        instance.setTraits(Arrays.asList(
            new BranchDiscoveryTrait(1),
            new SSHCheckoutTrait(sshCredentials.getId())));

        BitbucketRepository repository = mock(BitbucketRepository.class);
        when(repository.getLinks()).thenReturn(Map.of("clone", List.of(
            new BitbucketHref("http", String.format("https://bitbucket.org/%s/%s.git", CLOUD_REPO_OWNER, REPO_NAME)),
            new BitbucketHref("ssh", String.format("ssh://git@bitbucket.org/%s/%s.git", CLOUD_REPO_OWNER, REPO_NAME))
        )));
        BitbucketCloudApiClient client = mock(BitbucketCloudApiClient.class);
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, client);
        when(client.getRepository()).thenReturn(repository);

        BranchSCMHead head = new BranchSCMHead(BRANCH_NAME);
        AbstractGitSCMSource.SCMRevisionImpl revision =
            new AbstractGitSCMSource.SCMRevisionImpl(head, COMMIT_HASH);
        GitSCM build = (GitSCM)instance.build(head, revision);
        assertThat(build.getUserRemoteConfigs().size(), is(1));
        UserRemoteConfig remoteConfig = build.getUserRemoteConfigs().get(0);
        assertThat(remoteConfig.getUrl(), is(String.format("ssh://git@bitbucket.org/%s/%s.git", CLOUD_REPO_OWNER, REPO_NAME)));
        assertThat(remoteConfig.getRefspec(), is(String.format("+refs/heads/%s:refs/remotes/origin/%s", BRANCH_NAME, BRANCH_NAME)));
        assertThat(remoteConfig.getCredentialsId(), is(sshCredentials.getId()));
        assertThat(build.getExtensions(), containsInAnyOrder(
            instanceOf(BuildChooserSetting.class),
            instanceOf(GitSCMSourceDefaults.class))
        );
    }

    @Test
    public void buildBasicAuthThenAuthenticatorExtension() throws IOException, InterruptedException {
        StandardUsernameCredentials userPassCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
            "user-pass", null, "user", "pass");
        CredentialsProvider.lookupStores(j.jenkins).iterator().next()
            .addCredentials(Domain.global(), userPassCredentials);

        WorkflowMultiBranchProject owner = j.createProject(WorkflowMultiBranchProject.class);
        BitbucketSCMSource instance = new BitbucketSCMSource(CLOUD_REPO_OWNER, REPO_NAME);
        instance.setOwner(owner);
        instance.setCredentialsId(userPassCredentials.getId());
        instance.setTraits(List.of(new BranchDiscoveryTrait(1)));

        BitbucketRepository repository = mock(BitbucketRepository.class);
        when(repository.getLinks()).thenReturn(Map.of("clone", List.of(
            new BitbucketHref("http", String.format("https://bitbucket.org/%s/%s.git", CLOUD_REPO_OWNER, REPO_NAME)),
            new BitbucketHref("ssh", String.format("ssh://git@bitbucket.org/%s/%s.git", CLOUD_REPO_OWNER, REPO_NAME))
        )));
        BitbucketServerAPIClient client = mock(BitbucketServerAPIClient.class);
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, client);
        when(client.getRepository()).thenReturn(repository);

        BranchSCMHead head = new BranchSCMHead(BRANCH_NAME);
        AbstractGitSCMSource.SCMRevisionImpl revision =
            new AbstractGitSCMSource.SCMRevisionImpl(head, COMMIT_HASH);
        GitSCM build = (GitSCM)instance.build(head, revision);
        assertThat(build.getUserRemoteConfigs().size(), is(1));
        UserRemoteConfig remoteConfig = build.getUserRemoteConfigs().get(0);
        assertThat(remoteConfig.getUrl(), is(String.format("https://bitbucket.org/%s/%s.git", CLOUD_REPO_OWNER, REPO_NAME)));
        assertThat(remoteConfig.getRefspec(), is(String.format("+refs/heads/%s:refs/remotes/origin/%s", BRANCH_NAME, BRANCH_NAME)));
        assertThat(remoteConfig.getCredentialsId(), is(userPassCredentials.getId()));
        assertThat(build.getExtensions(), containsInAnyOrder(
            instanceOf(BuildChooserSetting.class),
            instanceOf(GitSCMSourceDefaults.class),
            instanceOf(GitClientAuthenticatorExtension.class)
        ));
    }
}
