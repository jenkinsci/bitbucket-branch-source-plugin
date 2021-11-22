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

import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import jenkins.plugins.git.AbstractGitSCMSource.SCMRevisionImpl;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BranchScanningTest {

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    private static final String repoOwner = "amuniz";
    private static final String repoName = "test";
    private static final String branchName = "branch1";

    @Before
    public void clearMockFactory() {
        BitbucketMockApiFactory.clear();
    }

    @Test
    public void uriResolverTest() throws Exception {

        // When there is no checkout credentials set, https must be resolved
        assertThat(new BitbucketGitSCMBuilder(getBitbucketSCMSourceMock(false),
                new BranchSCMHead("branch1"), null,
                null).withBitbucketRemote().remote(), is("https://bitbucket.org/amuniz/test-repos.git"));
    }

    @Test
    public void remoteConfigsTest() throws Exception {
        BitbucketSCMSource source = getBitbucketSCMSourceMock(false);
        BitbucketGitSCMBuilder builder =
                new BitbucketGitSCMBuilder(source, new BranchSCMHead("branch1"), null,
                        null);
        assertThat(builder.refSpecs(), Matchers.contains("+refs/heads/branch1:refs/remotes/@{remote}/branch1"));
    }

    @Test
    public void retrieveTest() throws Exception {
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL,
                BitbucketClientMockUtils.getAPIClientMock(false, false));
        BitbucketSCMSource source = getBitbucketSCMSourceMock(false);

        BranchSCMHead head = new BranchSCMHead(branchName);
        SCMRevision rev = source.retrieve(head, BitbucketClientMockUtils.getTaskListenerMock());

        // Last revision on branch1 must be returned
        assertEquals("52fc8e220d77ec400f7fc96a91d2fd0bb1bc553a", ((SCMRevisionImpl) rev).getHash());

    }

    @Test
    public void scanTest() throws Exception {
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL,
                BitbucketClientMockUtils.getAPIClientMock(false, false));
        BitbucketSCMSource source = getBitbucketSCMSourceMock(false);
        SCMHeadObserverImpl observer = new SCMHeadObserverImpl();
        source.fetch(observer, BitbucketClientMockUtils.getTaskListenerMock());

        // Only branch1 must be observed
        assertEquals(1, observer.getBranches().size());
        assertEquals("branch1", observer.getBranches().get(0));
    }

    @Test
    public void scanTestPullRequests() throws Exception {
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL,
                BitbucketClientMockUtils.getAPIClientMock(true, false));
        BitbucketSCMSource source = getBitbucketSCMSourceMock(true);
        SCMHeadObserverImpl observer = new SCMHeadObserverImpl();
        source.fetch(observer, BitbucketClientMockUtils.getTaskListenerMock());

        // Only branch1 and my-feature-branch PR must be observed
        assertEquals(2, observer.getBranches().size());
        assertEquals("branch1", observer.getBranches().get(0));
        assertEquals("PR-23", observer.getBranches().get(1));
    }

    @Test
    public void gitSCMTest() throws Exception {
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL,
                BitbucketClientMockUtils.getAPIClientMock(false, false));

        BitbucketSCMSource source = getBitbucketSCMSourceMock(false);
        SCM scm = source.build(new BranchSCMHead("branch1"));
        assertTrue("SCM must be an instance of GitSCM", scm instanceof GitSCM);
    }

    private BitbucketSCMSource getBitbucketSCMSourceMock(boolean includePullRequests)
            throws IOException, InterruptedException {
        BitbucketCloudApiClient mock = BitbucketClientMockUtils.getAPIClientMock(includePullRequests, false);

        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, mock);

        BitbucketSCMSource source = new BitbucketSCMSource("amuniz", "test-repos");
        source.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, true),
                new OriginPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD)),
                new ForkPullRequestDiscoveryTrait(
                        EnumSet.of(ChangeRequestCheckoutStrategy.HEAD),
                        new ForkPullRequestDiscoveryTrait.TrustTeamForks()
                )
        ));
        source.setOwner(getSCMSourceOwnerMock());
        return source;
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

        public List<String> branches = new ArrayList<>();

        public void observe(@NonNull SCMHead head, @NonNull SCMRevision revision) {
            branches.add(head.getName());
        }

        public List<String> getBranches() {
            return branches;
        }
    }
}
