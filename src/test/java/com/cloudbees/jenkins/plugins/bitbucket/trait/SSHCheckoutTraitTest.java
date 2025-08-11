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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@WithJenkins
class SSHCheckoutTraitTest {

    private static JenkinsRule j;

    @BeforeAll
    static void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void given__legacyConfig__when__creatingTrait__then__convertedToModern() {
        assertThat(new SSHCheckoutTrait(BitbucketSCMSource.DescriptorImpl.ANONYMOUS).getCredentialsId(),
                is(nullValue()));
    }

    @Test
    void given__sshCheckoutWithCredentials__when__decoratingGit__then__credentialsApplied() {
        SSHCheckoutTrait instance = new SSHCheckoutTrait("keyId");
        BitbucketGitSCMBuilder probe =
                new BitbucketGitSCMBuilder(new BitbucketSCMSource("example", "does-not-exist"),
                        new BranchSCMHead("main"), null, "scanId");
        assumeTrue("scanId".equals(probe.credentialsId()));
        instance.decorateBuilder(probe);
        assertThat(probe.credentialsId(), is("keyId"));
    }

    @Test
    void given__sshCheckoutWithAgentKey__when__decoratingGit__then__useAgentKeyApplied() {
        SSHCheckoutTrait instance = new SSHCheckoutTrait(null);
        BitbucketGitSCMBuilder probe =
                new BitbucketGitSCMBuilder(new BitbucketSCMSource( "example", "does-not-exist"),
                        new BranchSCMHead("main"), null, "scanId");
        assumeTrue("scanId".equals(probe.credentialsId()));
        instance.decorateBuilder(probe);
        assertThat(probe.credentialsId(), is(nullValue()));
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
                assertThat("Expecting only the provided value so that form config unchanged", rsp, hasSize(1));
                assertThat("Expecting only the provided value so that form config unchanged", rsp.get(0).value,
                        is("does-not-exist"));
                rsp = d.doFillCredentialsIdItems(null, "", "does-not-exist");
                assertThat("Expecting just the empty entry", rsp, hasSize(1));
                assertThat("Expecting just the empty entry", rsp.get(0).value, is(""));
            }
            try (ACLContext context = ACL.as(User.getOrCreateByIdOrFullName("bob"))) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat("Expecting just the empty entry", rsp, hasSize(1));
                assertThat("Expecting just the empty entry", rsp.get(0).value, is(""));
                rsp = d.doFillCredentialsIdItems(null, "", "does-not-exist");
                assertThat("Expecting only the provided value so that form config unchanged", rsp, hasSize(1));
                assertThat("Expecting only the provided value so that form config unchanged", rsp.get(0).value,
                        is("does-not-exist"));
            }
            try (ACLContext context = ACL.as(User.getOrCreateByIdOrFullName("jim"))) {
                ListBoxModel rsp = d.doFillCredentialsIdItems(dummy, "", "does-not-exist");
                assertThat("Expecting just the empty entry", rsp, hasSize(1));
                assertThat("Expecting just the empty entry", rsp.get(0).value, is(""));
            }
            try (ACLContext context = ACL.as(User.getOrCreateByIdOrFullName("sue"))) {
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
