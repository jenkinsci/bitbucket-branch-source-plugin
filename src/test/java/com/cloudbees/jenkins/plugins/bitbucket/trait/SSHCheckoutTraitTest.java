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
package com.cloudbees.jenkins.plugins.bitbucket.trait;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketGitSCMBuilder;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.BranchSCMHead;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AuthorizationStrategy;
import hudson.security.SecurityRealm;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

@WithJenkins
class SSHCheckoutTraitTest {

    private static JenkinsRule j;

    @BeforeAll
    static void init(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void given__legacyConfig__when__creatingTrait__then__convertedToModern() throws Exception {
        assertThat(new SSHCheckoutTrait(BitbucketSCMSource.DescriptorImpl.ANONYMOUS).getCredentialsId()).isNull();
    }

    @Test
    void given__sshCheckoutWithCredentials__when__decoratingGit__then__credentialsApplied() throws Exception {
        SSHCheckoutTrait instance = new SSHCheckoutTrait("keyId");
        BitbucketGitSCMBuilder probe = new BitbucketGitSCMBuilder(new BitbucketSCMSource("example", "does-not-exist"), new BranchSCMHead("main"), null, "scanId");
        assumeThat(probe.credentialsId()).isEqualTo("scanId");

        instance.decorateBuilder(probe);
        assertThat(probe.credentialsId()).isEqualTo("keyId");
    }

    @Test
    void given__sshCheckoutWithAgentKey__when__decoratingGit__then__useAgentKeyApplied() throws Exception {
        SSHCheckoutTrait instance = new SSHCheckoutTrait(null);
        BitbucketGitSCMBuilder probe = new BitbucketGitSCMBuilder(new BitbucketSCMSource( "example", "does-not-exist"), new BranchSCMHead("main"), null, "scanId");
        assumeThat(probe.credentialsId()).isEqualTo("scanId");

        instance.decorateBuilder(probe);
        assertThat(probe.credentialsId()).isNull();
    }

    @Test
    void given__descriptor__when__displayingCredentials__then__contractEnforced() throws Exception {
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

            try (ACLContext context = ACL.as(User.getOrCreateByIdOrFullName("admin"))) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat(rsp).describedAs("Expecting only the provided value so that form config unchanged")
                    .hasSize(1)
                    .element(0).satisfies(el -> assertThat(el.value).isEqualTo("does-not-exist"));

                rsp = d.doFillCredentialsIdItems(null, "", "does-not-exist");
                assertThat(rsp).describedAs("Expecting just the empty entry")
                    .hasSize(1)
                    .element(0).satisfies(el -> assertThat(el.value).isEmpty());
            }
            try (ACLContext context = ACL.as(User.getOrCreateByIdOrFullName("bob"))) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat(rsp).describedAs("Expecting just the empty entry")
                    .hasSize(1)
                    .element(0).satisfies(el -> assertThat(el.value).isEmpty());

                rsp = d.doFillCredentialsIdItems(null, "", "does-not-exist");
                assertThat(rsp).describedAs("Expecting only the provided value so that form config unchanged")
                    .hasSize(1)
                    .element(0).satisfies(el -> assertThat(el.value).isEqualTo("does-not-exist"));
            }
            try (ACLContext context = ACL.as(User.getOrCreateByIdOrFullName("jim"))) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat(rsp).describedAs("Expecting just the empty entry")
                    .hasSize(1)
                    .element(0).satisfies(el -> assertThat(el.value).isEmpty());
            }
            try (ACLContext context = ACL.as(User.getOrCreateByIdOrFullName("sue"))) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat(rsp).describedAs("Expecting only the provided value so that form config unchanged")
                    .hasSize(1)
                    .element(0).satisfies(el -> assertThat(el.value).isEqualTo("does-not-exist"));
            }
        } finally {
            j.jenkins.setSecurityRealm(realm);
            j.jenkins.setAuthorizationStrategy(strategy);
            j.jenkins.remove(dummy);
        }
    }
}
