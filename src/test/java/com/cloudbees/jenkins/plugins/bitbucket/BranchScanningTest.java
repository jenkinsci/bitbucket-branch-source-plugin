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
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryType;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.scm.SCM;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jenkins.plugins.git.AbstractGitSCMSource.SCMRevisionImpl;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.SCMSource;

import org.junit.ClassRule;
import org.junit.Test;

import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.jvnet.hudson.test.JenkinsRule;

public class BranchScanningTest {

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    private static final String repoOwner = "amuniz";
    private static final String repoName = "test";
    private static final String branchName = "branch1";

    @Test
    public void uriResolverTest() throws Exception {
        BitbucketSCMSource source = getBitbucketSCMSourceMock(BitbucketRepositoryType.GIT);
        String remote = source.getRemote("amuniz", "test", source.getRepositoryType());

        // When there is no checkout credentials set, https must be resolved
        assertEquals("https://bitbucket.org/amuniz/test.git", remote);

        source = getBitbucketSCMSourceMock(BitbucketRepositoryType.MERCURIAL);
        remote = source.getRemote("amuniz", "test", source.getRepositoryType());

        // Resolve URL for Mercurial repositories
        assertEquals("https://bitbucket.org/amuniz/test", remote);
    }

    @Test
    public void remoteConfigsTest() throws Exception {
        BitbucketSCMSource source = getBitbucketSCMSourceMock(BitbucketRepositoryType.GIT);
        List<UserRemoteConfig> remoteConfigs = source.getGitRemoteConfigs(new BranchSCMHead("branch1", BitbucketRepositoryType.GIT));
        assertEquals(1, remoteConfigs.size());
        assertEquals("+refs/heads/branch1", remoteConfigs.get(0).getRefspec());
    }

    @Test
    public void retrieveTest() throws Exception {
        BitbucketSCMSource source = getBitbucketSCMSourceMock(BitbucketRepositoryType.GIT);

        BranchSCMHead head = new BranchSCMHead(branchName, BitbucketRepositoryType.GIT);
        SCMRevision rev = source.retrieve(head, BitbucketClientMockUtils.getTaskListenerMock());

        // Last revision on branch1 must be returned
        assertEquals("52fc8e220d77ec400f7fc96a91d2fd0bb1bc553a", ((SCMRevisionImpl) rev).getHash());

    }

    @Test
    public void scanTest() throws Exception {
        BitbucketSCMSource source = getBitbucketSCMSourceMock(BitbucketRepositoryType.GIT);
        SCMHeadObserverImpl observer = new SCMHeadObserverImpl();
        source.fetch(observer, BitbucketClientMockUtils.getTaskListenerMock());

        // Only branch1 must be observed
        assertEquals(1, observer.getBranches().size());
        assertEquals("branch1", observer.getBranches().get(0));
    }

    @Test
    public void scanTestPullRequests() throws Exception {
        BitbucketSCMSource source = getBitbucketSCMSourceMock(BitbucketRepositoryType.GIT, true);
        SCMHeadObserverImpl observer = new SCMHeadObserverImpl();
        source.fetch(observer, BitbucketClientMockUtils.getTaskListenerMock());

        // Only branch1 and my-feature-branch PR must be observed
        assertEquals(2, observer.getBranches().size());
        assertEquals("branch1", observer.getBranches().get(0));
        assertEquals("PR-23", observer.getBranches().get(1));
    }

    @Test
    public void gitSCMTest() throws Exception {
        SCM scm = scmBuild(BitbucketRepositoryType.GIT);
        assertTrue("SCM must be an instance of GitSCM", scm instanceof GitSCM);
    }

    @Test
    public void mercurialSCMTest() throws Exception {
        SCM scm = scmBuild(BitbucketRepositoryType.MERCURIAL);
        assertTrue("SCM must be an instance of MercurialSCM", scm instanceof MercurialSCM);
    }

    private SCM scmBuild(BitbucketRepositoryType type) throws IOException, InterruptedException {
        BitbucketSCMSource source = getBitbucketSCMSourceMock(type);
        return source.build(new BranchSCMHead("branch1", type));
    }

    private BitbucketSCMSource getBitbucketSCMSourceMock(BitbucketRepositoryType type, boolean includePullRequests)
            throws IOException, InterruptedException {
        BitbucketCloudApiClient mock = BitbucketClientMockUtils.getAPIClientMock(type, includePullRequests);
        BitbucketMockApiFactory.add(null, mock);

        BitbucketSCMSource source = new BitbucketSCMSource(null, "amuniz", "test-repos");
        source.setOwner(getSCMSourceOwnerMock());
        return source;
    }

    private BitbucketSCMSource getBitbucketSCMSourceMock(BitbucketRepositoryType type) throws IOException, InterruptedException {
        return getBitbucketSCMSourceMock(type, false);
    }

    private SCMSourceOwner getSCMSourceOwnerMock() {
        SCMSourceOwner mocked = mock(SCMSourceOwner.class);
        when(mocked.getSCMSourceCriteria(any(SCMSource.class))).thenReturn(new SCMSourceCriteria() {

            @Override
            public boolean isHead(Probe probe, TaskListener listener) throws IOException {
                return probe.exists("markerfile.txt");
            }

            @Override
            public int hashCode() {
                return getClass().hashCode();
            }

            @Override
            public boolean equals(Object obj) {
                return getClass().isInstance(obj);
            }
        });
        return mocked;
    }

    public final class SCMHeadObserverImpl extends SCMHeadObserver {

        public List<String> branches = new ArrayList<String>();

        public void observe(@NonNull SCMHead head, @NonNull SCMRevision revision) {
            branches.add(head.getName());
        }

        public List<String> getBranches() {
            return branches;
        }
    }
}
