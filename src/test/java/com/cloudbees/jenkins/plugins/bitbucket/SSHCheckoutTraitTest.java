package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryType;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AuthorizationStrategy;
import hudson.security.SecurityRealm;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockFolder;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

public class SSHCheckoutTraitTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Test
    public void given_legacyConfig_when_creatingTrait_then_convertedToModern() throws Exception {
        assertThat(new SSHCheckoutTrait(BitbucketSCMSource.DescriptorImpl.ANONYMOUS).getCredentialsId(),
                is(nullValue()));
    }

    @Test
    public void given_sshCheckoutWithCredentials_when_decoratingGit_then_credentialsApplied() throws Exception {
        SSHCheckoutTrait instance = new SSHCheckoutTrait("keyId");
        BitbucketGitSCMBuilder probe =
                new BitbucketGitSCMBuilder(new BitbucketSCMSource("example", "does-not-exist"),
                        new BranchSCMHead("master", BitbucketRepositoryType.GIT), null, "scanId");
        assumeThat(probe.credentialsId(), is("scanId"));
        instance.decorateBuilder(probe);
        assertThat(probe.credentialsId(), is("keyId"));
    }

    @Test
    public void given_sshCheckoutWithAgentKey_when_decoratingGit_then_useAgentKeyApplied() throws Exception {
        SSHCheckoutTrait instance = new SSHCheckoutTrait(null);
        BitbucketGitSCMBuilder probe =
                new BitbucketGitSCMBuilder(new BitbucketSCMSource( "example", "does-not-exist"),
                        new BranchSCMHead("master", BitbucketRepositoryType.GIT), null, "scanId");
        assumeThat(probe.credentialsId(), is("scanId"));
        instance.decorateBuilder(probe);
        assertThat(probe.credentialsId(), is(nullValue()));
    }

    @Test
    public void given_sshCheckoutWithCredentials_when_decoratingHg_then_credentialsApplied() throws Exception {
        SSHCheckoutTrait instance = new SSHCheckoutTrait("keyId");
        BitbucketHgSCMBuilder probe =
                new BitbucketHgSCMBuilder(new BitbucketSCMSource( "example", "does-not-exist"),
                        new BranchSCMHead("master", BitbucketRepositoryType.MERCURIAL), null, "scanId");
        assumeThat(probe.credentialsId(), is("scanId"));
        instance.decorateBuilder(probe);
        assertThat(probe.credentialsId(), is("keyId"));
    }

    @Test
    public void given_sshCheckoutWithAgentKey_when_decoratingHg_then_useAgentKeyApplied() throws Exception {
        SSHCheckoutTrait instance = new SSHCheckoutTrait(null);
        BitbucketHgSCMBuilder probe =
                new BitbucketHgSCMBuilder(new BitbucketSCMSource( "example", "does-not-exist"),
                        new BranchSCMHead("master", BitbucketRepositoryType.MERCURIAL), null, "scanId");
        assumeThat(probe.credentialsId(), is("scanId"));
        instance.decorateBuilder(probe);
        assertThat(probe.credentialsId(), is(nullValue()));
    }

    @Test
    public void given_descriptor_when_displayingCredentials_then_contractEnforced() throws Exception {
        final SSHCheckoutTrait.DescriptorImpl d = j.jenkins.getDescriptorByType(SSHCheckoutTrait.DescriptorImpl.class);
        final MockFolder dummy = j.createFolder("dummy");
        SecurityRealm realm = j.jenkins.getSecurityRealm();
        AuthorizationStrategy strategy = j.jenkins.getAuthorizationStrategy();
        try {
            j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
            MockAuthorizationStrategy mockStrategy = new MockAuthorizationStrategy();
            mockStrategy.grant(Jenkins.ADMINISTER).onRoot().to("admin");
            mockStrategy.grant(Item.CONFIGURE).onItems(dummy).to("bob");
            mockStrategy.grant(Item.EXTENDED_READ).onItems(dummy).to("jim");
            j.jenkins.setAuthorizationStrategy(mockStrategy);
            try (ACLContext context = ACL.as(User.get("admin"))) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat("Expecting only the provided value so that form config unchanged", rsp, hasSize(1));
                assertThat("Expecting only the provided value so that form config unchanged", rsp.get(0).value,
                        is("does-not-exist"));
                rsp = d.doFillCredentialsIdItems(null, "", "does-not-exist");
                assertThat("Expecting just the empty entry", rsp, hasSize(1));
                assertThat("Expecting just the empty entry", rsp.get(0).value, is(""));
            }
            try (ACLContext context = ACL.as(User.get("bob"))) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat("Expecting just the empty entry", rsp, hasSize(1));
                assertThat("Expecting just the empty entry", rsp.get(0).value, is(""));
                rsp = d.doFillCredentialsIdItems(null, "", "does-not-exist");
                assertThat("Expecting only the provided value so that form config unchanged", rsp, hasSize(1));
                assertThat("Expecting only the provided value so that form config unchanged", rsp.get(0).value,
                        is("does-not-exist"));
            }
            try (ACLContext context = ACL.as(User.get("jim"))) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat("Expecting just the empty entry", rsp, hasSize(1));
                assertThat("Expecting just the empty entry", rsp.get(0).value, is(""));
            }
            try (ACLContext context = ACL.as(User.get("sue"))) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat("Expecting only the provided value so that form config unchanged", rsp, hasSize(1));
                assertThat("Expecting only the provided value so that form config unchanged", rsp.get(0).value,
                        is("does-not-exist"));
            }
        } finally {
            j.jenkins.setSecurityRealm(realm);
            j.jenkins.setAuthorizationStrategy(strategy);
            j.jenkins.remove(dummy);
        }
    }
}
