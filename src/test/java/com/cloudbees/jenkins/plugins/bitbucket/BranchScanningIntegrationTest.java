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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketMockApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketServerEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketCredentialsUtils;
import com.cloudbees.jenkins.plugins.bitbucket.test.util.BitbucketClientMockUtils;
import com.cloudbees.jenkins.plugins.bitbucket.trait.BranchDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.ForkPullRequestDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.OriginPullRequestDiscoveryTrait;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.FreeStyleProject;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import java.util.Arrays;
import java.util.EnumSet;
import jenkins.branch.Branch;
import jenkins.branch.BranchSource;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

@WithJenkins
class BranchScanningIntegrationTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void indexingTest() throws Exception {
        BitbucketEndpointConfiguration.get()
                .addEndpoint(new BitbucketServerEndpoint("test", "http://bitbucket.test"));
        BitbucketMockApiFactory.add("http://bitbucket.test", BitbucketClientMockUtils.getAPIClientMock(false, false));

        MockMultiBranchProjectImpl p = j.jenkins.createProject(MockMultiBranchProjectImpl.class, "test");
        BitbucketSCMSource source = new BitbucketSCMSource("amuniz", "test-repos");
        source.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, true),
                new OriginPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD)),
                new ForkPullRequestDiscoveryTrait(
                        EnumSet.of(ChangeRequestCheckoutStrategy.HEAD),
                        new ForkPullRequestDiscoveryTrait.TrustTeamForks()
                )
        ));
        source.setServerUrl("http://bitbucket.test");
        source.setOwner(p);
        p.getSourcesList().add(new BranchSource(source));
        p.scheduleBuild2(0);
        j.waitUntilNoActivity();

        // Only branch1 contains the marker file (branch2 does not meet the criteria)
        assertEquals(1, p.getAllJobs().size());
        assertEquals("branch1", p.getAllJobs().iterator().next().getName());
    }

    @Test
    void uriResolverByCredentialsTest() throws Exception {
        WorkflowMultiBranchProject context = j.jenkins.createProject(WorkflowMultiBranchProject.class, "context");
        BitbucketSCMSource source = new BitbucketSCMSource("amuniz", "test-repos");
        source.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, true),
                new OriginPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD)),
                new ForkPullRequestDiscoveryTrait(
                        EnumSet.of(ChangeRequestCheckoutStrategy.HEAD),
                        new ForkPullRequestDiscoveryTrait.TrustTeamForks()
                )
        ));
        source.setServerUrl("http://bitbucket.test");
        context.getSourcesList().add(new BranchSource(source));
        IdCredentials c = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, null, null, "user", "pass");
        CredentialsProvider.lookupStores(j.jenkins).iterator().next()
                .addCredentials(Domain.global(), c);

        StandardCredentials creds = BitbucketCredentialsUtils.lookupCredentials(
                source.getOwner(),
                null ,
                c.getId(),
                UsernamePasswordCredentialsImpl.class
        );
        assertThat(creds, instanceOf(UsernamePasswordCredentialsImpl.class));

        c = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, null, "user", null, null, null);
        CredentialsProvider.lookupStores(j.jenkins).iterator().next()
                .addCredentials(Domain.global(), c);

        creds = BitbucketCredentialsUtils.lookupCredentials(
                source.getOwner(),
                null,
                c.getId(),
                BasicSSHUserPrivateKey.class
        );
        assertThat(creds, instanceOf(BasicSSHUserPrivateKey.class));
    }

    public static class BranchProperty extends JobProperty<FreeStyleProject> {

        private Branch b;

        public BranchProperty(Branch b) {
            this.b = b;
        }

        public Branch getBranch() {
            return b;
        }

        @Override
        public JobPropertyDescriptor getDescriptor() {
            return new DescriptorImpl();
        }

        @TestExtension
        public static class DescriptorImpl extends JobPropertyDescriptor {

            @Override
            public String getDisplayName() {
                return "Branch property";
            }
        }
    }

}
