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

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.acegisecurity.Authentication;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import hudson.Extension;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import jenkins.branch.Branch;
import jenkins.branch.BranchProjectFactory;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.MultiBranchProjectDescriptor;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;

public class BranchScanningIntegrationTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void indexingTest() throws Exception {
        MultiBranchProjectImpl p = j.jenkins.createProject(MultiBranchProjectImpl.class, "test");
        BitbucketSCMSource source = getTestSCMSource(RepositoryType.GIT);
        source.setOwner(p);
        p.getSourcesList().add(new BranchSource(source, new DefaultBranchPropertyStrategy(null)));
        p.scheduleBuild2(0);
        j.waitUntilNoActivity();

        // Only branch1 contains the marker file (branch2 does not meet the criteria)
        assertEquals(1, p.getAllJobs().size());
        assertEquals("branch1", p.getAllJobs().iterator().next().getName());
    }

    @Test
    public void uriResolverByCredentialsTest() throws Exception {
        BitbucketSCMSource source = getTestSCMSource(RepositoryType.GIT);
        IdCredentials c = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, null, null, "user", "pass");
        CredentialsProvider.lookupStores(j.jenkins).iterator().next()
                .addCredentials(Domain.global(), c);

        BitbucketApiConnector connector = new BitbucketApiConnector();
        StandardCredentials creds = connector.lookupCredentials(source.getOwner(), c.getId(), UsernamePasswordCredentialsImpl.class);
        assertTrue(creds instanceof UsernamePasswordCredentialsImpl);

        c = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, null, "user", null, null, null);
        CredentialsProvider.lookupStores(j.jenkins).iterator().next()
                .addCredentials(Domain.global(), c);

        creds = connector.lookupCredentials(source.getOwner(), c.getId(), BasicSSHUserPrivateKey.class);
        assertTrue(creds instanceof BasicSSHUserPrivateKey);
    }

    public static class MultiBranchProjectImpl extends MultiBranchProject<FreeStyleProject, FreeStyleBuild> {

        static final SCMSourceCriteria CRITERIA = new SCMSourceCriteria() {
            @Override public boolean isHead(SCMSourceCriteria.Probe probe, TaskListener listener) throws IOException {
                return probe.exists("markerfile.txt");
            }
        };

        protected MultiBranchProjectImpl(ItemGroup parent, String name) {
            super(parent, name);
        }

        @Override
        public Authentication getDefaultAuthentication(hudson.model.Queue.Item item) {
            return getDefaultAuthentication();
        }

        @Override
        protected BranchProjectFactory<FreeStyleProject, FreeStyleBuild> newProjectFactory() {
            return new BranchProjectFactoryImpl();
        }

        @Override 
        public SCMSourceCriteria getSCMSourceCriteria(SCMSource source) {
            return CRITERIA;
        }
        
        @Override
        public List<SCMSource> getSCMSources() {
            if (getSourcesList() == null) {
                // test code is generating a NullPointer when calling it from an ItemListener on the SCMSourceOwner
                // It seems that the object is not fully initialized when the ItemListener uses it.
                // Perhaps it needs to be reproduced and investigated in a branch-api test.
                return new ArrayList<SCMSource>();
            }
            return super.getSCMSources();
        }

        public static class BranchProjectFactoryImpl extends BranchProjectFactory<FreeStyleProject, FreeStyleBuild> {

            @Override
            public FreeStyleProject newInstance(Branch branch) {
                FreeStyleProject job = new FreeStyleProject(getOwner(), branch.getName());
                job.onCreatedFromScratch();
                FreeStyleProject spied = spy(job);
                // Do nothing.. Running the actual build is not desired/required (and not possible) in this tests.
                when(spied.scheduleBuild()).thenReturn(false);
                setBranch(spied, branch);
                return spied;
            }

            @Override
            public Branch getBranch(FreeStyleProject project) {
                return project.getProperty(BranchProperty.class).getBranch();
            }

            @Override
            public FreeStyleProject setBranch(FreeStyleProject project, Branch branch) {
                try {
                    project.addProperty(new BranchProperty(branch));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return project;
            }

            @Override
            public boolean isProject(Item item) {
                return item instanceof FreeStyleProject && ((FreeStyleProject) item).getProperty(BranchProperty.class) != null;
            }

        }

        @Extension
        public static class DescriptorImpl extends MultiBranchProjectDescriptor {

            @Override 
            public String getDisplayName() {
                return "Test Multibranch";
            }

            @Override 
            public TopLevelItem newInstance(ItemGroup parent, String name) {
                return new MultiBranchProjectImpl(parent, name);
            }

        }
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

        @Extension
        public static class DescriptorImpl extends JobPropertyDescriptor {

            @Override
            public String getDisplayName() {
                return "Branch property";
            }
        }
    }

    public static BitbucketSCMSource getTestSCMSource(RepositoryType type) {
        return getTestSCMSource(type, false);
    }

    public static BitbucketSCMSource getTestSCMSource(RepositoryType type, boolean includeWebHooks) {
        BitbucketSCMSource source = new BitbucketSCMSource(null, "amuniz", "test-repos");
        BitbucketApiConnector mockFactory = mock(BitbucketApiConnector.class);
        BitbucketCloudApiClient mockedApi = BitbucketClientMockUtils.getAPIClientMock(type, false);
        when(mockFactory.create(anyString(), anyString(), any(StandardUsernamePasswordCredentials.class))).thenReturn(mockedApi);
        source.setBitbucketConnector(mockFactory);
        return source;
    }
}
