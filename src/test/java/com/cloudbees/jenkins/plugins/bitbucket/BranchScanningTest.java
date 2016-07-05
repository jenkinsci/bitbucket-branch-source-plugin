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

import org.junit.Test;

import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import edu.umd.cs.findbugs.annotations.NonNull;

public class BranchScanningTest {

    private static final String repoOwner = "amuniz";
    private static final String repoName = "test";
    private static final String branchName = "branch1";

    @Test
    public void uriResolverTest() {
        BitbucketSCMSource source = getBitbucketSCMSourceMock(RepositoryType.GIT);
        String remote = source.getRemote("amuniz", "test");

        // When there is no checkout credentials set, https must be resolved
        assertEquals("https://bitbucket.org/amuniz/test.git", remote);

        source = getBitbucketSCMSourceMock(RepositoryType.MERCURIAL);
        remote = source.getRemote("amuniz", "test");

        // Resolve URL for Mercurial repositories
        assertEquals("https://bitbucket.org/amuniz/test", remote);
    }

    @Test
    public void remoteConfigsTest() {
        BitbucketSCMSource source = getBitbucketSCMSourceMock(RepositoryType.GIT);
        List<UserRemoteConfig> remoteConfigs = source.getGitRemoteConfigs(new SCMHeadWithOwnerAndRepo("amuniz", "test-repos", "branch1"));
        assertEquals(1, remoteConfigs.size());
        assertEquals("+refs/heads/branch1", remoteConfigs.get(0).getRefspec());
    }

    @Test
    public void retrieveTest() throws IOException, InterruptedException {
        BitbucketSCMSource source = getBitbucketSCMSourceMock(RepositoryType.GIT);

        SCMHeadWithOwnerAndRepo head = new SCMHeadWithOwnerAndRepo(repoOwner, repoName, branchName);
        SCMRevision rev = source.retrieve(head, BitbucketClientMockUtils.getTaskListenerMock());

        // Last revision on branch1 must be returned
        assertEquals("52fc8e220d77ec400f7fc96a91d2fd0bb1bc553a", ((SCMRevisionImpl) rev).getHash());

    }

    @Test
    public void scanTest() throws IOException, InterruptedException {
        BitbucketSCMSource source = getBitbucketSCMSourceMock(RepositoryType.GIT);
        SCMHeadObserverImpl observer = new SCMHeadObserverImpl();
        source.retrieve(observer, BitbucketClientMockUtils.getTaskListenerMock());

        // Only branch1 must be observed
        assertEquals(1, observer.getBranches().size());
        assertEquals("branch1", observer.getBranches().get(0));
    }

    @Test
    public void scanTestPullRequests() throws IOException, InterruptedException {
        BitbucketSCMSource source = getBitbucketSCMSourceMock(RepositoryType.GIT, true);
        SCMHeadObserverImpl observer = new SCMHeadObserverImpl();
        source.retrieve(observer, BitbucketClientMockUtils.getTaskListenerMock());

        // Only branch1 and my-feature-branch PR must be observed
        assertEquals(2, observer.getBranches().size());
        assertEquals("branch1", observer.getBranches().get(0));
        assertEquals("PR-23", observer.getBranches().get(1));
    }

    @Test
    public void gitSCMTest() {
        SCM scm = scmBuild(RepositoryType.GIT);
        assertTrue("SCM must be an instance of GitSCM", scm instanceof GitSCM);
    }

    @Test
    public void mercurialSCMTest() {
        SCM scm = scmBuild(RepositoryType.MERCURIAL);
        assertTrue("SCM must be an instance of MercurialSCM", scm instanceof MercurialSCM);
    }

    private SCM scmBuild(RepositoryType type) {
        BitbucketSCMSource source = getBitbucketSCMSourceMock(type);
        return source.build(new SCMHeadWithOwnerAndRepo("amuniz", "test-repos", "branch1"));
    }

    private BitbucketSCMSource getBitbucketSCMSourceMock(RepositoryType type, boolean includePullRequests) {
        BitbucketSCMSource source = new BitbucketSCMSource(null, "amuniz", "test-repos");
        source.setOwner(getSCMSourceOwnerMock());
        BitbucketApiConnector mockFactory = mock(BitbucketApiConnector.class);
        BitbucketCloudApiClient mockedApi = BitbucketClientMockUtils.getAPIClientMock(type, includePullRequests);
        when(mockFactory.create(anyString(), anyString(), any(StandardUsernamePasswordCredentials.class))).thenReturn(mockedApi);
        source.setBitbucketConnector(mockFactory);
        return source;
    }

    private BitbucketSCMSource getBitbucketSCMSourceMock(RepositoryType type) {
        return getBitbucketSCMSourceMock(type, false);
    }

    private SCMSourceOwner getSCMSourceOwnerMock() {
        SCMSourceOwner mocked = mock(SCMSourceOwner.class);
        when(mocked.getSCMSourceCriteria(any(SCMSource.class))).thenReturn(new SCMSourceCriteria() {

            @Override
            public boolean isHead(Probe probe, TaskListener listener) throws IOException {
                return probe.exists("markerfile.txt");
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
