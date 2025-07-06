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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketMockApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.impl.extension.BitbucketEnvVarExtension;
import com.cloudbees.jenkins.plugins.bitbucket.impl.extension.GitClientAuthenticatorExtension;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerAPIClient;
import com.cloudbees.jenkins.plugins.bitbucket.trait.BranchDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.SSHCheckoutTrait;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Descriptor.FormException;
import hudson.model.Item;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.impl.BuildChooserSetting;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMSourceDefaults;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.junit.jupiter.WithGitSampleRepo;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.WithTimeout;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests different scenarios of the
 * {@link BitbucketSCMSource#build(jenkins.scm.api.SCMHead, jenkins.scm.api.SCMRevision)} method.
 */
@WithJenkins
@WithGitSampleRepo
public class BitbucketSCMSourceBuildTest {

    private static final String CLOUD_REPO_OWNER = "cloudbeers";
    private static final String REPO_NAME = "stunning-adventure";
    private static final String BRANCH_NAME = "master";
    // Test private key from ssh-credentials test case
    // https://github.com/jenkinsci/ssh-credentials-plugin/blob/343.v884f71d78167/src/test/resources/com/cloudbees/jenkins/plugins/sshcredentials/impl/BasicSSHUserPrivateKeyTest/readOldCredentials/credentials.xml
    private static final String PRIVATE_KEY = "-----BEGIN RSA PRIVATE KEY-----\n" +
        "MIIEpAIBAAKCAQEAu1r+HHzmpybc4iwoP5+44FjvcaMkNEWeGQZlmPwLx70XW8+8\n" +
        "3d2BMLjhcw1zLsYG3FWpCyn8cB1OmjKiGjvnP5EBoAolvh3qOcWKyWlVGWGgs10B\n" +
        "0Cgnd3OBXRPQd1cBdiQZmmeCrrH0OjQefYIF2WYN+F8iuNGraAaRsXLgITanjTb1\n" +
        "6w1dnk+KLpU2J5G6kG1f/Qxl4pgny80S/3TktqoknbOrYDMOSA1Zdww39cpXJHp6\n" +
        "feEs8tavC93rOsR2O4ZfVUCjTFAF5FtIdRv3LXY3Q5W/AyY1h45Wk6mMVnEluFjB\n" +
        "aA+gzVAVaHFQfuhwoj4B7jWCmfHsPG1WmyK0YQIDAQABAoIBADt1qlXiMdV0mP9S\n" +
        "okdm6maQ8xTugKvyODWa+R1vSFHQqhwiNr927+xFkI9SAm8iu8SrjuWTIqF2O57m\n" +
        "WNnYjxB2dbyT29yVY+OH1P8M5cwTVsv1xYCJbdUUHEcs5akqPLWAyXteRHQq1+as\n" +
        "6cxNOov/PonHr55WNH7kLtLRMV54jZ68nrEh5pWdabFa0f7d/ByIvYRJm7lpjtSp\n" +
        "EBp5AseXzSg2EZP3HDPYYPDHK0tMPginz9+YuQCQFoMYAkVZoKFJGsWICktd5Uk7\n" +
        "wveOJLOfMng1Iww6CEc871GcLn5LbafLWRxjZssK2Z1fC+pZYLLZPeKDMSxoRXm6\n" +
        "PShUC1ECgYEA623dmwJNYfVRgAgOYhhcxzH4TAXUmUIpadEUjOkAhe3w/abDawFT\n" +
        "9ianhqfhTjSZGBtUttcN40Vy+P4bsqfQKZ7p6KzrdR2zWjlYcICWhZDZPGmMxpMZ\n" +
        "mUFhZXsLRVhn8qed8w1t6eju7S+t9satKIMC/KrhNsFzjrgbU9eC+m0CgYEAy7nN\n" +
        "gMwQeGxAQSJr9H7eKkthnjMe77rLIAZEbDJhcwYVz+Iie/E4hjESQ+IuvXa1VawD\n" +
        "O6cD0wWdOhH2McNdMNIbM4IOaO/TOaR5jfQwBWmb4iG2BZQWQQE/HUBnoJQWUhqm\n" +
        "b+D+s4bHh4J+bs+ptgg9Sd9V+VxJBcu2QDmI6UUCgYBTb1pMJyK5hrFdiH1gcnXe\n" +
        "+myetKpFrlby83AvCBxxWoQ/wKwc7hmNcOGKLVEB4E4pZvY83jZDx0cZyySRyjtR\n" +
        "pMoM9ct0dBQt84jORiQSLeVvLZEAhv1ZfPxBdLvn1Y7xRkoJ60Z60Vxrnqwueva/\n" +
        "Fr8mQIEUYLbNa53ztrrqeQKBgQCqOY4k2F3KwWjPA9wAZyFrZaEjdsOavBGNqK7z\n" +
        "WQVj/umq0eDOfzgjqE0Cu7MiTFYoR5pL9bmUUVSWePuliQANEwH3f+xackmkGHIY\n" +
        "0rhtTVkbEd/tuVb+6fO6lV4BJrufzvTS9sTbbPq7l6XdIVdE6o2LdDl6Kko5tYWL\n" +
        "FIf5oQKBgQDLHK/9NTb3VHp+Qriu2Vp8Pnaw6YF6pETfgjyrH2ULSW/R07v+AECC\n" +
        "sPr+d/hx2MQWp54HglY8lv98rOrRjMiRw1GVoXs+Ut9vkupmrpvzNE7ITl0tzBqD\n" +
        "sroT/IHW2jKMD0v8kKLUnKCZYzlw0By7+RvJ8lgzHB0D71f6EC1UWg==\n" +
        "-----END RSA PRIVATE KEY-----\n";

    public static JenkinsRule rule = new JenkinsRule();

    @BeforeAll
    static void init(JenkinsRule r) {
        rule = r;
    }

    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @BeforeEach
    void setup(GitSampleRepoRule gitRule) {
        sampleRepo = gitRule;
    }

    @AfterEach
    void setup() throws Exception {
        for (Item item : rule.jenkins.getAllItems()) {
            item.delete();
        }
    }

    @Test
    @Issue("JENKINS-73471")
    @WithTimeout(120)
    void buildWhenSetSSHCheckoutTraitThenEmptyAuthenticatorExtension() throws Exception {
        String jenkinsFile = "Jenkinsfile";
        sampleRepo.init();
        sampleRepo.write(jenkinsFile, "node { checkout scm }");
        sampleRepo.git("add", jenkinsFile);
        sampleRepo.git("commit", "--all", "--message=defined");

        StandardUsernameCredentials userPassCredentials = registerUserCredentials();
        StandardUsernameCredentials sshCredentials = registerSSHCredentials();

        WorkflowMultiBranchProject owner = rule.createProject(WorkflowMultiBranchProject.class, "testMultibranch");
        BitbucketSCMSource scmSource = new BitbucketSCMSource(CLOUD_REPO_OWNER, REPO_NAME);
        scmSource.setServerUrl("http://localhost:7990/bitbucket");
        scmSource.setOwner(owner);
        scmSource.setCredentialsId(userPassCredentials.getId());
        scmSource.setTraits(Arrays.asList(
            new BranchDiscoveryTrait(1),
            new SSHCheckoutTrait(sshCredentials.getId())));

        BitbucketRepository repository = mock(BitbucketRepository.class);
        when(repository.getCloneLinks()).thenReturn(List.of(
            new BitbucketHref("http", sampleRepo.toString()),
            new BitbucketHref("ssh", String.format("ssh://user@localhost/%s", sampleRepo))
        ));
        BitbucketApi client = mock(BitbucketApi.class);
        BitbucketMockApiFactory.add(scmSource.getServerUrl(), client);
        when(client.getRepository()).thenReturn(repository);

        BranchSCMHead head = new BranchSCMHead(BRANCH_NAME);
        AbstractGitSCMSource.SCMRevisionImpl revision =
            new AbstractGitSCMSource.SCMRevisionImpl(head, sampleRepo.head());
        GitSCM build = (GitSCM) scmSource.build(head, revision);

        assertThat(build.getUserRemoteConfigs().size(), is(1));
        UserRemoteConfig remoteConfig = build.getUserRemoteConfigs().get(0);
        assertThat(remoteConfig.getUrl(), is(String.format("ssh://user@localhost/%s", sampleRepo)));
        assertThat(remoteConfig.getRefspec(), is(String.format("+refs/heads/%s:refs/remotes/origin/%s", BRANCH_NAME, BRANCH_NAME)));
        assertThat(remoteConfig.getCredentialsId(), is(sshCredentials.getId()));
        assertThat(build.getExtensions(), containsInAnyOrder(
            instanceOf(BuildChooserSetting.class),
            instanceOf(GitSCMSourceDefaults.class),
            instanceOf(GitClientAuthenticatorExtension.class),
            instanceOf(BitbucketEnvVarExtension.class))
        );

        // Create a Pipeline with CpsScmFlowDefinition based of the GitSCM produced
        // Then check that the checkout uses GIT_SSH from the git-client logs
        WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "testGitScm");
        job.setDefinition(new CpsScmFlowDefinition(build, jenkinsFile));
        WorkflowRun run = job.scheduleBuild2(0).get();

        ByteArrayOutputStream byteArrayOutStr = new ByteArrayOutputStream();
        run.writeWholeLogTo(byteArrayOutStr);
        assertThat(byteArrayOutStr.toString(StandardCharsets.UTF_8), containsString("using GIT_SSH to set credentials"));
    }

    @Test
    @WithTimeout(120)
    void buildBasicAuthThenAuthenticatorExtension() throws Exception {
        String jenkinsFile = "Jenkinsfile";
        sampleRepo.init();
        sampleRepo.write(jenkinsFile, "node { checkout scm }");
        sampleRepo.git("add", jenkinsFile);
        sampleRepo.git("commit", "--all", "--message=defined");

        StandardUsernameCredentials userPassCredentials = registerUserCredentials();

        WorkflowMultiBranchProject owner = rule.createProject(WorkflowMultiBranchProject.class, "testMultibranch");
        BitbucketSCMSource scmSource = new BitbucketSCMSource(CLOUD_REPO_OWNER, REPO_NAME);
        scmSource.setServerUrl("http://localhost:7990/bitbucket");
        scmSource.setOwner(owner);
        scmSource.setCredentialsId(userPassCredentials.getId());
        scmSource.setTraits(List.of(new BranchDiscoveryTrait(1)));

        BitbucketRepository repository = mock(BitbucketRepository.class);
        when(repository.getCloneLinks()).thenReturn(List.of(
            new BitbucketHref("http", sampleRepo.toString()),
            new BitbucketHref("ssh", String.format("ssh://localhost:%s", sampleRepo))
        ));
        BitbucketServerAPIClient client = mock(BitbucketServerAPIClient.class);
        BitbucketMockApiFactory.add(scmSource.getServerUrl(), client);
        when(client.getRepository()).thenReturn(repository);

        BranchSCMHead head = new BranchSCMHead(BRANCH_NAME);
        AbstractGitSCMSource.SCMRevisionImpl revision =
            new AbstractGitSCMSource.SCMRevisionImpl(head, sampleRepo.head());
        GitSCM build = (GitSCM) scmSource.build(head, revision);

        assertThat(build.getUserRemoteConfigs().size(), is(1));
        UserRemoteConfig remoteConfig = build.getUserRemoteConfigs().get(0);
        assertThat(remoteConfig.getUrl(), is(sampleRepo.toString()));
        assertThat(remoteConfig.getRefspec(), is(String.format("+refs/heads/%s:refs/remotes/origin/%s", BRANCH_NAME, BRANCH_NAME)));
        assertThat(remoteConfig.getCredentialsId(), is(userPassCredentials.getId()));
        assertThat(build.getExtensions(), containsInAnyOrder(
            instanceOf(BuildChooserSetting.class),
            instanceOf(GitSCMSourceDefaults.class),
            instanceOf(GitClientAuthenticatorExtension.class),
            instanceOf(BitbucketEnvVarExtension.class)
        ));

        // Create a Pipeline with CpsScmFlowDefinition based of the GitSCM produced
        // Then check that the checkout scm uses GIT_ASKPASS from the git-client logs
        WorkflowJob job = rule.jenkins.createProject(WorkflowJob.class, "testGitScm");
        job.setDefinition(new CpsScmFlowDefinition(build, jenkinsFile));
        WorkflowRun run = job.scheduleBuild2(0).get();

        ByteArrayOutputStream byteArrayOutStr = new ByteArrayOutputStream();
        run.writeWholeLogTo(byteArrayOutStr);
        assertThat(byteArrayOutStr.toString(StandardCharsets.UTF_8), containsString("using GIT_ASKPASS to set credentials"));
    }

    @Test
    @Issue("JENKINS-75611")
    void verify_ssh_trait_on_data_center_with_disabled_https_protocol() throws Exception {
        StandardUsernameCredentials userPassCredentials = registerUserCredentials();
        StandardUsernameCredentials sshCredentials = registerSSHCredentials();

        WorkflowMultiBranchProject owner = rule.createProject(WorkflowMultiBranchProject.class, "testMultibranch");
        BitbucketSCMSource scmSource = new BitbucketSCMSource(CLOUD_REPO_OWNER, REPO_NAME);
        scmSource.setServerUrl("http://localhost:7990/bitbucket");
        scmSource.setOwner(owner);
        scmSource.setCredentialsId(userPassCredentials.getId());
        scmSource.setTraits(Arrays.asList(
            new BranchDiscoveryTrait(1),
            new SSHCheckoutTrait(sshCredentials.getId())));

        String sshCloneURL = String.format("ssh://user@localhost::7999/%s/%s.git", CLOUD_REPO_OWNER, REPO_NAME);

        BitbucketRepository repository = mock(BitbucketRepository.class);
        when(repository.getCloneLinks()).thenReturn(List.of(
            new BitbucketHref("http", sampleRepo.toString()),
            new BitbucketHref("ssh", sshCloneURL)
        ));
        BitbucketApi client = mock(BitbucketApi.class);
        BitbucketMockApiFactory.add(scmSource.getServerUrl(), client);
        when(client.getRepository()).thenReturn(repository);

        BranchSCMHead head = new BranchSCMHead(BRANCH_NAME);
        AbstractGitSCMSource.SCMRevisionImpl revision = new AbstractGitSCMSource.SCMRevisionImpl(head, "1dbb02d4c1b99f1e84459c6947e3caa53cadfad1");
        GitSCM build = (GitSCM) scmSource.build(head, revision);

        assertThat(build.getUserRemoteConfigs().size(), is(1));
        UserRemoteConfig remoteConfig = build.getUserRemoteConfigs().get(0);
        assertThat(remoteConfig.getUrl(), is(sshCloneURL));
        assertThat(remoteConfig.getRefspec(), is(String.format("+refs/heads/%s:refs/remotes/origin/%s", BRANCH_NAME, BRANCH_NAME)));
        assertThat(remoteConfig.getCredentialsId(), is(sshCredentials.getId()));
        assertThat(build.getExtensions(), containsInAnyOrder(
            instanceOf(BuildChooserSetting.class),
            instanceOf(GitSCMSourceDefaults.class),
            instanceOf(GitClientAuthenticatorExtension.class),
            instanceOf(BitbucketEnvVarExtension.class))
        );
    }

    private StandardUsernameCredentials registerSSHCredentials() throws IOException {
        StandardUsernameCredentials sshCredentials = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, "user-key", "user",
            new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(PRIVATE_KEY), null, null);
        CredentialsProvider.lookupStores(rule.jenkins).iterator().next()
            .addCredentials(Domain.global(), sshCredentials);
        return sshCredentials;
    }

    private StandardUsernameCredentials registerUserCredentials() throws FormException, IOException {
        StandardUsernameCredentials userPassCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
                "user-pass", null, "user", "pass");
        CredentialsProvider.lookupStores(rule.jenkins).iterator().next()
            .addCredentials(Domain.global(), userPassCredentials);
        return userPassCredentials;
    }
}
