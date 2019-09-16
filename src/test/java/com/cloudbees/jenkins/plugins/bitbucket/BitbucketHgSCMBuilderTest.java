package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryType;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.plugins.mercurial.browser.BitBucket;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jenkins.branch.BranchSource;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public class BitbucketHgSCMBuilderTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
    private BitbucketSCMSource source;
    private WorkflowMultiBranchProject owner;

    @Before
    public void setUp() throws IOException {
        owner = j.createProject(WorkflowMultiBranchProject.class);
        source = new BitbucketSCMSource("tester", "test-repo");
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
    public void given__branch_rev_anon__when__build__then__scmBuilt() throws Exception {
        BranchSCMHead head = new BranchSCMHead("test-branch", BitbucketRepositoryType.MERCURIAL);
        BitbucketSCMSource.MercurialRevision revision =
                new BitbucketSCMSource.MercurialRevision(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        BitbucketHgSCMBuilder instance = new BitbucketHgSCMBuilder(source,
                head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketSource called",
                instance.source(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitBucket.class));
        assertThat(instance.browser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.source(), is("https://bitbucket.org/tester/test-repo"));

        MercurialSCM actual = instance.build();
        assertThat(actual.getCredentialsId(), is(nullValue()));
        assertThat(actual.getBrowser(), instanceOf(BitBucket.class));
        assertThat(actual.getBrowser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));
        assertThat(actual.getSource(), is("https://bitbucket.org/tester/test-repo"));
        assertThat(actual.getRevisionType(), is(MercurialSCM.RevisionType.CHANGESET));
        assertThat(actual.getRevision(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__pullHead_rev_anon__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch", BitbucketRepositoryType.MERCURIAL),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision<BitbucketSCMSource.MercurialRevision> revision =
                new PullRequestSCMRevision<>(head, new BitbucketSCMSource.MercurialRevision(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new BitbucketSCMSource.MercurialRevision(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketHgSCMBuilder instance = new BitbucketHgSCMBuilder(source,
                head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketSource called",
                instance.source(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitBucket.class));
        assertThat(instance.browser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.source(), is("https://bitbucket.org/qa/qa-repo"));

        MercurialSCM actual = instance.build();
        assertThat(actual.getCredentialsId(), is(nullValue()));
        assertThat(actual.getBrowser(), instanceOf(BitBucket.class));
        assertThat(actual.getBrowser().getUrl().toString(), is("https://bitbucket.org/qa/qa-repo/"));
        assertThat(actual.getSource(), is("https://bitbucket.org/qa/qa-repo"));
        assertThat(actual.getRevisionType(), is(MercurialSCM.RevisionType.CHANGESET));
        assertThat(actual.getRevision(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__branch_rev_userpass__when__build__then__scmBuilt() throws Exception {
        BranchSCMHead head = new BranchSCMHead("test-branch", BitbucketRepositoryType.MERCURIAL);
        BitbucketSCMSource.MercurialRevision revision =
                new BitbucketSCMSource.MercurialRevision(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        BitbucketHgSCMBuilder instance = new BitbucketHgSCMBuilder(source,
                head, revision, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketSource called",
                instance.source(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitBucket.class));
        assertThat(instance.browser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.source(), is("https://bitbucket.org/tester/test-repo"));

        MercurialSCM actual = instance.build();
        assertThat(actual.getCredentialsId(), is("user-pass"));
        assertThat(actual.getBrowser(), instanceOf(BitBucket.class));
        assertThat(actual.getBrowser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));
        assertThat(actual.getSource(), is("https://bitbucket.org/tester/test-repo"));
        assertThat(actual.getRevisionType(), is(MercurialSCM.RevisionType.CHANGESET));
        assertThat(actual.getRevision(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__pullHead_rev_userpass__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch", BitbucketRepositoryType.MERCURIAL),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision<BitbucketSCMSource.MercurialRevision> revision =
                new PullRequestSCMRevision<>(head, new BitbucketSCMSource.MercurialRevision(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new BitbucketSCMSource.MercurialRevision(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketHgSCMBuilder instance = new BitbucketHgSCMBuilder(source,
                head, revision, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketSource called",
                instance.source(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitBucket.class));
        assertThat(instance.browser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.source(), is("https://bitbucket.org/qa/qa-repo"));

        MercurialSCM actual = instance.build();
        assertThat(actual.getCredentialsId(), is("user-pass"));
        assertThat(actual.getBrowser(), instanceOf(BitBucket.class));
        assertThat(actual.getBrowser().getUrl().toString(), is("https://bitbucket.org/qa/qa-repo/"));
        assertThat(actual.getSource(), is("https://bitbucket.org/qa/qa-repo"));
        assertThat(actual.getRevisionType(), is(MercurialSCM.RevisionType.CHANGESET));
        assertThat(actual.getRevision(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__branch_rev_userkey__when__build__then__scmBuilt() throws Exception {
        BranchSCMHead head = new BranchSCMHead("test-branch", BitbucketRepositoryType.MERCURIAL);
        BitbucketSCMSource.MercurialRevision revision =
                new BitbucketSCMSource.MercurialRevision(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        BitbucketHgSCMBuilder instance = new BitbucketHgSCMBuilder(source,
                head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketSource called",
                instance.source(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitBucket.class));
        assertThat(instance.browser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.source(), is("ssh://hg@bitbucket.org/tester/test-repo"));

        MercurialSCM actual = instance.build();
        assertThat(actual.getCredentialsId(), is("user-key"));
        assertThat(actual.getBrowser(), instanceOf(BitBucket.class));
        assertThat(actual.getBrowser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));
        assertThat(actual.getSource(), is("ssh://hg@bitbucket.org/tester/test-repo"));
        assertThat(actual.getRevisionType(), is(MercurialSCM.RevisionType.CHANGESET));
        assertThat(actual.getRevision(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__pullHead_rev_userkey__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch", BitbucketRepositoryType.MERCURIAL),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        PullRequestSCMRevision<BitbucketSCMSource.MercurialRevision> revision =
                new PullRequestSCMRevision<>(head, new BitbucketSCMSource.MercurialRevision(head.getTarget(),
                        "deadbeefcafebabedeadbeefcafebabedeadbeef"),
                        new BitbucketSCMSource.MercurialRevision(head, "cafebabedeadbeefcafebabedeadbeefcafebabe"));
        BitbucketHgSCMBuilder instance = new BitbucketHgSCMBuilder(source,
                head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketSource called",
                instance.source(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitBucket.class));
        assertThat(instance.browser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.source(), is("ssh://hg@bitbucket.org/qa/qa-repo"));

        MercurialSCM actual = instance.build();
        assertThat(actual.getCredentialsId(), is("user-key"));
        assertThat(actual.getBrowser(), instanceOf(BitBucket.class));
        assertThat(actual.getBrowser().getUrl().toString(), is("https://bitbucket.org/qa/qa-repo/"));
        assertThat(actual.getSource(), is("ssh://hg@bitbucket.org/qa/qa-repo"));
        assertThat(actual.getRevisionType(), is(MercurialSCM.RevisionType.CHANGESET));
        assertThat(actual.getRevision(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__branch_rev_anon_sshtrait_anon__when__build__then__scmBuilt() throws Exception {
        BranchSCMHead head = new BranchSCMHead("test-branch", BitbucketRepositoryType.MERCURIAL);
        BitbucketSCMSource.MercurialRevision revision =
                new BitbucketSCMSource.MercurialRevision(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        BitbucketHgSCMBuilder instance = new BitbucketHgSCMBuilder(source,
                head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketSource called",
                instance.source(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitBucket.class));
        assertThat(instance.browser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.source(), is("https://bitbucket.org/tester/test-repo"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait(null);
        sshTrait.decorateBuilder(instance);

        MercurialSCM actual = instance.build();
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.source(), is("ssh://hg@bitbucket.org/tester/test-repo"));

        assertThat(actual.getCredentialsId(), is(nullValue()));
        assertThat(actual.getBrowser(), instanceOf(BitBucket.class));
        assertThat(actual.getBrowser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));
        assertThat(actual.getSource(), is("ssh://hg@bitbucket.org/tester/test-repo"));
        assertThat(actual.getRevisionType(), is(MercurialSCM.RevisionType.CHANGESET));
        assertThat(actual.getRevision(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__branch_rev_userpass_sshtrait_anon__when__build__then__scmBuilt() throws Exception {
        BranchSCMHead head = new BranchSCMHead("test-branch", BitbucketRepositoryType.MERCURIAL);
        BitbucketSCMSource.MercurialRevision revision =
                new BitbucketSCMSource.MercurialRevision(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        BitbucketHgSCMBuilder instance = new BitbucketHgSCMBuilder(source,
                head, revision, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketSource called",
                instance.source(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitBucket.class));
        assertThat(instance.browser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.source(), is("https://bitbucket.org/tester/test-repo"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait(null);
        sshTrait.decorateBuilder(instance);

        MercurialSCM actual = instance.build();
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.source(), is("ssh://hg@bitbucket.org/tester/test-repo"));

        assertThat(actual.getCredentialsId(), is(nullValue()));
        assertThat(actual.getBrowser(), instanceOf(BitBucket.class));
        assertThat(actual.getBrowser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));
        assertThat(actual.getSource(), is("ssh://hg@bitbucket.org/tester/test-repo"));
        assertThat(actual.getRevisionType(), is(MercurialSCM.RevisionType.CHANGESET));
        assertThat(actual.getRevision(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__branch_rev_userkey_sshtrait_anon__when__build__then__scmBuilt() throws Exception {
        BranchSCMHead head = new BranchSCMHead("test-branch", BitbucketRepositoryType.MERCURIAL);
        BitbucketSCMSource.MercurialRevision revision =
                new BitbucketSCMSource.MercurialRevision(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        BitbucketHgSCMBuilder instance = new BitbucketHgSCMBuilder(source,
                head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketSource called",
                instance.source(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitBucket.class));
        assertThat(instance.browser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.source(), is("ssh://hg@bitbucket.org/tester/test-repo"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait(null);
        sshTrait.decorateBuilder(instance);

        MercurialSCM actual = instance.build();
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.source(), is("ssh://hg@bitbucket.org/tester/test-repo"));

        assertThat(actual.getCredentialsId(), is(nullValue()));
        assertThat(actual.getBrowser(), instanceOf(BitBucket.class));
        assertThat(actual.getBrowser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));
        assertThat(actual.getSource(), is("ssh://hg@bitbucket.org/tester/test-repo"));
        assertThat(actual.getRevisionType(), is(MercurialSCM.RevisionType.CHANGESET));
        assertThat(actual.getRevision(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__branch_rev_anon_sshtrait_userkey__when__build__then__scmBuilt() throws Exception {
        BranchSCMHead head = new BranchSCMHead("test-branch", BitbucketRepositoryType.MERCURIAL);
        BitbucketSCMSource.MercurialRevision revision =
                new BitbucketSCMSource.MercurialRevision(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        BitbucketHgSCMBuilder instance = new BitbucketHgSCMBuilder(source,
                head, revision, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketSource called",
                instance.source(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitBucket.class));
        assertThat(instance.browser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.source(), is("https://bitbucket.org/tester/test-repo"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait("user-key");
        sshTrait.decorateBuilder(instance);

        MercurialSCM actual = instance.build();
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.source(), is("ssh://hg@bitbucket.org/tester/test-repo"));

        assertThat(actual.getCredentialsId(), is("user-key"));
        assertThat(actual.getBrowser(), instanceOf(BitBucket.class));
        assertThat(actual.getBrowser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));
        assertThat(actual.getSource(), is("ssh://hg@bitbucket.org/tester/test-repo"));
        assertThat(actual.getRevisionType(), is(MercurialSCM.RevisionType.CHANGESET));
        assertThat(actual.getRevision(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__branch_rev_userpass_sshtrait_userkey__when__build__then__scmBuilt() throws Exception {
        BranchSCMHead head = new BranchSCMHead("test-branch", BitbucketRepositoryType.MERCURIAL);
        BitbucketSCMSource.MercurialRevision revision =
                new BitbucketSCMSource.MercurialRevision(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        BitbucketHgSCMBuilder instance = new BitbucketHgSCMBuilder(source,
                head, revision, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketSource called",
                instance.source(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitBucket.class));
        assertThat(instance.browser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.source(), is("https://bitbucket.org/tester/test-repo"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait("user-key");
        sshTrait.decorateBuilder(instance);

        MercurialSCM actual = instance.build();
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.source(), is("ssh://hg@bitbucket.org/tester/test-repo"));

        assertThat(actual.getCredentialsId(), is("user-key"));
        assertThat(actual.getBrowser(), instanceOf(BitBucket.class));
        assertThat(actual.getBrowser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));
        assertThat(actual.getSource(), is("ssh://hg@bitbucket.org/tester/test-repo"));
        assertThat(actual.getRevisionType(), is(MercurialSCM.RevisionType.CHANGESET));
        assertThat(actual.getRevision(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__branch_rev_userkey_sshtrait_userkey__when__build__then__scmBuilt() throws Exception {
        BranchSCMHead head = new BranchSCMHead("test-branch", BitbucketRepositoryType.MERCURIAL);
        BitbucketSCMSource.MercurialRevision revision =
                new BitbucketSCMSource.MercurialRevision(head, "cafebabedeadbeefcafebabedeadbeefcafebabe");
        BitbucketHgSCMBuilder instance = new BitbucketHgSCMBuilder(source,
                head, revision, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((SCMRevision) revision));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketSource called",
                instance.source(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitBucket.class));
        assertThat(instance.browser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.source(), is("ssh://hg@bitbucket.org/tester/test-repo"));

        SSHCheckoutTrait sshTrait = new SSHCheckoutTrait("user-key");
        sshTrait.decorateBuilder(instance);

        MercurialSCM actual = instance.build();
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.source(), is("ssh://hg@bitbucket.org/tester/test-repo"));

        assertThat(actual.getCredentialsId(), is("user-key"));
        assertThat(actual.getBrowser(), instanceOf(BitBucket.class));
        assertThat(actual.getBrowser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));
        assertThat(actual.getSource(), is("ssh://hg@bitbucket.org/tester/test-repo"));
        assertThat(actual.getRevisionType(), is(MercurialSCM.RevisionType.CHANGESET));
        assertThat(actual.getRevision(), is("cafebabedeadbeefcafebabedeadbeefcafebabe"));
    }

    @Test
    public void given__branch_norev_anon__when__build__then__scmBuilt() throws Exception {
        BranchSCMHead head = new BranchSCMHead("test-branch", BitbucketRepositoryType.MERCURIAL);
        BitbucketHgSCMBuilder instance = new BitbucketHgSCMBuilder(source,
                head, null, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketSource called",
                instance.source(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitBucket.class));
        assertThat(instance.browser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.source(), is("https://bitbucket.org/tester/test-repo"));

        MercurialSCM actual = instance.build();
        assertThat(actual.getCredentialsId(), is(nullValue()));
        assertThat(actual.getBrowser(), instanceOf(BitBucket.class));
        assertThat(actual.getBrowser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));
        assertThat(actual.getSource(), is("https://bitbucket.org/tester/test-repo"));
        assertThat(actual.getRevisionType(), is(MercurialSCM.RevisionType.BRANCH));
        assertThat(actual.getRevision(), is("test-branch"));
    }

    @Test
    public void given__pullHead_norev_anon__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch", BitbucketRepositoryType.MERCURIAL),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        BitbucketHgSCMBuilder instance = new BitbucketHgSCMBuilder(source,
                head, null, null);
        assertThat(instance.credentialsId(), is(nullValue()));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketSource called",
                instance.source(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitBucket.class));
        assertThat(instance.browser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.source(), is("https://bitbucket.org/qa/qa-repo"));

        MercurialSCM actual = instance.build();
        assertThat(actual.getCredentialsId(), is(nullValue()));
        assertThat(actual.getBrowser(), instanceOf(BitBucket.class));
        assertThat(actual.getBrowser().getUrl().toString(), is("https://bitbucket.org/qa/qa-repo/"));
        assertThat(actual.getSource(), is("https://bitbucket.org/qa/qa-repo"));
        assertThat(actual.getRevisionType(), is(MercurialSCM.RevisionType.BRANCH));
        assertThat(actual.getRevision(), is("qa-branch"));
    }

    @Test
    public void given__branch_norev_userpass__when__build__then__scmBuilt() throws Exception {
        BranchSCMHead head = new BranchSCMHead("test-branch", BitbucketRepositoryType.MERCURIAL);
        BitbucketHgSCMBuilder instance = new BitbucketHgSCMBuilder(source,
                head, null, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is((nullValue())));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketSource called",
                instance.source(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitBucket.class));
        assertThat(instance.browser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.source(), is("https://bitbucket.org/tester/test-repo"));

        MercurialSCM actual = instance.build();
        assertThat(actual.getCredentialsId(), is("user-pass"));
        assertThat(actual.getBrowser(), instanceOf(BitBucket.class));
        assertThat(actual.getBrowser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));
        assertThat(actual.getSource(), is("https://bitbucket.org/tester/test-repo"));
        assertThat(actual.getRevisionType(), is(MercurialSCM.RevisionType.BRANCH));
        assertThat(actual.getRevision(), is("test-branch"));
    }

    @Test
    public void given__pullHead_norev_userpass__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch", BitbucketRepositoryType.MERCURIAL),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        BitbucketHgSCMBuilder instance = new BitbucketHgSCMBuilder(source,
                head, null, "user-pass");
        assertThat(instance.credentialsId(), is("user-pass"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketSource called",
                instance.source(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitBucket.class));
        assertThat(instance.browser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.source(), is("https://bitbucket.org/qa/qa-repo"));

        MercurialSCM actual = instance.build();
        assertThat(actual.getCredentialsId(), is("user-pass"));
        assertThat(actual.getBrowser(), instanceOf(BitBucket.class));
        assertThat(actual.getBrowser().getUrl().toString(), is("https://bitbucket.org/qa/qa-repo/"));
        assertThat(actual.getSource(), is("https://bitbucket.org/qa/qa-repo"));
        assertThat(actual.getRevisionType(), is(MercurialSCM.RevisionType.BRANCH));
        assertThat(actual.getRevision(), is("qa-branch"));
    }

    @Test
    public void given__branch_norev_userkey__when__build__then__scmBuilt() throws Exception {
        BranchSCMHead head = new BranchSCMHead("test-branch", BitbucketRepositoryType.MERCURIAL);
        BitbucketHgSCMBuilder instance = new BitbucketHgSCMBuilder(source,
                head, null, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketSource called",
                instance.source(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitBucket.class));
        assertThat(instance.browser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.source(), is("ssh://hg@bitbucket.org/tester/test-repo"));

        MercurialSCM actual = instance.build();
        assertThat(actual.getCredentialsId(), is("user-key"));
        assertThat(actual.getBrowser(), instanceOf(BitBucket.class));
        assertThat(actual.getBrowser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));
        assertThat(actual.getSource(), is("ssh://hg@bitbucket.org/tester/test-repo"));
        assertThat(actual.getRevisionType(), is(MercurialSCM.RevisionType.BRANCH));
        assertThat(actual.getRevision(), is("test-branch"));
    }

    @Test
    public void given__pullHead_norev_userkey__when__build__then__scmBuilt() throws Exception {
        PullRequestSCMHead head = new PullRequestSCMHead("PR-1", "qa", "qa-repo", "qa-branch", "1", "a fake title",
                new BranchSCMHead("test-branch", BitbucketRepositoryType.MERCURIAL),
                new SCMHeadOrigin.Fork("qa/qa-repo"),
                ChangeRequestCheckoutStrategy.HEAD);
        BitbucketHgSCMBuilder instance = new BitbucketHgSCMBuilder(source,
                head, null, "user-key");
        assertThat(instance.credentialsId(), is("user-key"));
        assertThat(instance.head(), is((SCMHead) head));
        assertThat(instance.revision(), is(nullValue()));
        assertThat(instance.scmSource(), is(source));
        assertThat(instance.cloneLinks(), is(Collections.<BitbucketHref>emptyList()));
        assertThat("expecting dummy value until clone links provided or withBitbucketSource called",
                instance.source(), is("https://bitbucket.org"));
        assertThat(instance.browser(), instanceOf(BitBucket.class));
        assertThat(instance.browser().getUrl().toString(), is("https://bitbucket.org/tester/test-repo/"));

        instance.withCloneLinks(Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/tester/test-repo.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/tester/test-repo.git")
        ));
        assertThat(instance.source(), is("ssh://hg@bitbucket.org/qa/qa-repo"));

        MercurialSCM actual = instance.build();
        assertThat(actual.getCredentialsId(), is("user-key"));
        assertThat(actual.getBrowser(), instanceOf(BitBucket.class));
        assertThat(actual.getBrowser().getUrl().toString(), is("https://bitbucket.org/qa/qa-repo/"));
        assertThat(actual.getSource(), is("ssh://hg@bitbucket.org/qa/qa-repo"));
        assertThat(actual.getRevisionType(), is(MercurialSCM.RevisionType.BRANCH));
        assertThat(actual.getRevision(), is("qa-branch"));
    }

}
