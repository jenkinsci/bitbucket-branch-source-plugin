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

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketClientMockUtils;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketMockApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMNavigator;
import com.cloudbees.jenkins.plugins.bitbucket.MockMultiBranchProjectImpl;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketServerEndpoint;
import hudson.model.ItemGroup;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.MultiBranchProjectFactory;
import jenkins.branch.MultiBranchProjectFactoryDescriptor;
import jenkins.branch.OrganizationFolder;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.impl.trait.RegexSCMSourceFilterTrait;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import static org.assertj.core.api.Assertions.assertThat;

class SCMNavigatorIntegrationTest {

    @WithJenkins
    @Test
    void teamDiscoveringTest(JenkinsRule j) throws Exception {
        BitbucketEndpointConfiguration
                .get().addEndpoint(new BitbucketServerEndpoint("test", "http://bitbucket.test", false, null));
        BitbucketMockApiFactory.add("http://bitbucket.test", BitbucketClientMockUtils.getAPIClientMock(true, false));

        OrganizationFolder teamFolder = j.jenkins.createProject(OrganizationFolder.class, "test");
        BitbucketSCMNavigator navigator = new BitbucketSCMNavigator("myteam");
        navigator.setServerUrl("http://bitbucket.test");
        navigator.setTraits(List.of(
                new RegexSCMSourceFilterTrait("test-repos"),
                new BranchDiscoveryTrait(true, true),
                new ForkPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD), new ForkPullRequestDiscoveryTrait.TrustEveryone())));
        teamFolder.getNavigators().add(navigator);
        teamFolder.scheduleBuild2(0).getFuture().get();
        teamFolder.getComputation().writeWholeLogTo(System.out);
        // One repository must be discovered
        assertThat(teamFolder.getItems()).hasSize(1);

        MultiBranchProject<?, ?> project = teamFolder.getItems().iterator().next();
        project.scheduleBuild2(0).getFuture().get();
        project.getComputation().writeWholeLogTo(System.out);
        // Two items (1 branch matching criteria + 1 pull request)
        assertThat(project.getItems()).hasSize(2);
    }

    public static class MultiBranchProjectFactoryImpl extends MultiBranchProjectFactory.BySCMSourceCriteria {

        @DataBoundConstructor
        public MultiBranchProjectFactoryImpl() {}

        @Override
        protected SCMSourceCriteria getSCMSourceCriteria(SCMSource source) {
            return MockMultiBranchProjectImpl.CRITERIA;
        }

        @Override
        protected MultiBranchProject<?,?> doCreateProject(ItemGroup<?> parent, String name, Map<String,Object> attributes) {
            return new MockMultiBranchProjectImpl(parent, name);
        }

        @TestExtension
        public static class DescriptorImpl extends MultiBranchProjectFactoryDescriptor {

            @Override
            public MultiBranchProjectFactory newInstance() {
                return new MultiBranchProjectFactoryImpl();
            }

            @Override
            public String getDisplayName() {
                return "Test multibranch factory";
            }

        }

    }

}
