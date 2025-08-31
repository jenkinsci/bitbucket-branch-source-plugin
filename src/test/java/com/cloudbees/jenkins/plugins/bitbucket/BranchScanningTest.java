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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketMockApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.test.util.BitbucketClientMockUtils;
import com.cloudbees.jenkins.plugins.bitbucket.trait.BranchDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.ForkPullRequestDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.OriginPullRequestDiscoveryTrait;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WithJenkins
class BranchScanningTest {

    private static final String BRANCH_NAME = "branch1";
    @SuppressWarnings("unused")
    private static JenkinsRule rule;

    @BeforeAll
    static void init(JenkinsRule rule) {
        BranchScanningTest.rule = rule;
    }

    @BeforeEach
    void clearMockFactory() {
        BitbucketMockApiFactory.clear();
    }

    @Test
    void uriResolverTest() throws Exception {

        // When there is no checkout credentials set, https must be resolved
        BitbucketGitSCMBuilder builder = new BitbucketGitSCMBuilder(
            getBitbucketSCMSourceMock(false),
            new BranchSCMHead("branch1"), null,
            null
        ).withCloneLinks(
            List.of(
                new BitbucketHref("http", "https://bitbucket.org/amuniz/test-repos.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/amuniz/test-repo.git")
            ),
            List.of()
        );
        assertThat(builder.remote()).isEqualTo("https://bitbucket.org/amuniz/test-repos.git");
    }

    @Test
    void retrieveTest() throws Exception {
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL,
                BitbucketClientMockUtils.getAPIClientMock(false, false));
        BitbucketSCMSource source = getBitbucketSCMSourceMock(false);

        BranchSCMHead head = new BranchSCMHead(BRANCH_NAME);
        SCMRevision rev = source.retrieve(head, BitbucketClientMockUtils.getTaskListenerMock());

        // Last revision on branch1 must be returned
        assertThat(((SCMRevisionImpl) rev).getHash()).isEqualTo("52fc8e220d77ec400f7fc96a91d2fd0bb1bc553a");

    }

    @Test
    void scanTest() throws Exception {
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL,
                BitbucketClientMockUtils.getAPIClientMock(false, false));
        BitbucketSCMSource source = getBitbucketSCMSourceMock(false);
        SCMHeadObserverImpl observer = new SCMHeadObserverImpl();
        source.fetch(observer, BitbucketClientMockUtils.getTaskListenerMock());

        // Only branch1 must be observed
        assertThat(observer.getBranches())
            .hasSize(1)
            .contains("branch1");
    }

    @Test
    void scanTestPullRequests() throws Exception {
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL,
                BitbucketClientMockUtils.getAPIClientMock(true, false));
        BitbucketSCMSource source = getBitbucketSCMSourceMock(true);
        SCMHeadObserverImpl observer = new SCMHeadObserverImpl();
        source.fetch(observer, BitbucketClientMockUtils.getTaskListenerMock());

        // Only branch1 and my-feature-branch PR must be observed
        assertThat(observer.getBranches())
            .hasSize(2)
            .contains("branch1", "PR-23");
    }

    @Test
    void gitSCMTest() throws Exception {
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL,
                BitbucketClientMockUtils.getAPIClientMock(false, false));

        BitbucketSCMSource source = getBitbucketSCMSourceMock(false);
        SCM scm = source.build(new BranchSCMHead("branch1"));
        assertThat(scm).as("SCM must be an instance of GitSCM").isInstanceOf(GitSCM.class);
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

    @SuppressWarnings("serial")
    private SCMSourceOwner getSCMSourceOwnerMock() {
        SCMSourceOwner mocked = mock(SCMSourceOwner.class);
        when(mocked.getSCMSourceCriteria(any(SCMSource.class))).thenReturn(new SCMSourceCriteria() {

            @Override
            public boolean isHead(Probe probe, TaskListener listener) throws IOException {
                return probe.stat("markerfile.txt").exists();
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

    public static final class SCMHeadObserverImpl extends SCMHeadObserver {

        public List<String> branches = new ArrayList<>();

        @Override
        public void observe(@NonNull SCMHead head, @NonNull SCMRevision revision) {
            branches.add(head.getName());
        }

        public List<String> getBranches() {
            return branches;
        }
    }
}
