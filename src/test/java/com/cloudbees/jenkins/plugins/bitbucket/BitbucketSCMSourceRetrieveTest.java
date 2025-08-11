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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBranch;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketMockApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestDestination;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestEvent;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudBranch;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.HasPullRequests;
import com.cloudbees.jenkins.plugins.bitbucket.impl.BitbucketPlugin;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerAPIClient;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.branch.BitbucketServerBranch;
import com.cloudbees.jenkins.plugins.bitbucket.test.util.BitbucketClientMockUtils;
import com.cloudbees.jenkins.plugins.bitbucket.trait.ForkPullRequestDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.ForkPullRequestDiscoveryTrait.TrustEveryone;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Items;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests different scenarios of the
 * {@link BitbucketSCMSource#retrieve(SCMSourceCriteria, SCMHeadObserver, SCMHeadEvent, TaskListener)} method.
 *
 * This test was created to validate a fix for the issue described in:
 * https://github.com/jenkinsci/bitbucket-branch-source-plugin/issues/469
 */
@WithJenkins
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BitbucketSCMSourceRetrieveTest {

    private static final String CLOUD_REPO_OWNER = "cloudbeers";
    private static final String SERVER_REPO_OWNER = "DUB";
    private static final String SERVER_REPO_URL = "https://bitbucket.test";
    private static final String REPO_NAME = "stunning-adventure";
    private static final String BRANCH_NAME = "branch1";
    private static final String COMMIT_HASH = "e851558f77c098d21af6bb8cc54a423f7cf12147";
    private static final Integer PR_ID = 1;

    private static JenkinsRule jenkinsRule;

    @Mock
    private BitbucketRepository repository;
    @Mock
    private BitbucketBranch sourceBranch;
    @Mock
    private BitbucketBranch destinationBranch;
    @Mock
    private BitbucketPullRequestDestination prDestination;
    @Mock
    private BitbucketPullRequestSource prSource;
    @Mock
    private BitbucketCommit commit;
    @Mock
    private BitbucketPullRequest pullRequest;
    @Mock
    private SCMSourceCriteria criteria;

    @BeforeAll
    static void init(JenkinsRule r) {
        jenkinsRule = r;
    }

    @BeforeEach
    void setUp() {
        when(prDestination.getRepository()).thenReturn(repository);
        when(prDestination.getBranch()).thenReturn(destinationBranch);
        when(destinationBranch.getName()).thenReturn("main");

        when(sourceBranch.getName()).thenReturn(BRANCH_NAME);
        when(prSource.getRepository()).thenReturn(repository);
        when(prSource.getBranch()).thenReturn(sourceBranch);
        when(commit.getHash()).thenReturn(COMMIT_HASH);
        when(prSource.getCommit()).thenReturn(commit);

        when(pullRequest.getSource()).thenReturn(prSource);
        when(pullRequest.getDestination()).thenReturn(prDestination);
        when(pullRequest.getId()).thenReturn(PR_ID.toString());
    }

    @Test
    void retrieveTriggersRequiredApiCalls_cloud() throws Exception {
        BitbucketSCMSource instance = load("retrieve_prs_test_cloud");
        assertThat(instance.getId()).isEqualTo("retrieve_prs_test_cloud");
        assertThat(instance.getServerUrl()).isEqualTo(BitbucketCloudEndpoint.SERVER_URL);
        assertThat(instance.getRepoOwner()).isEqualTo(CLOUD_REPO_OWNER);
        assertThat(instance.getRepository()).isEqualTo(REPO_NAME);
        assertThat(instance.getTraits())
            .usingRecursiveFieldByFieldElementComparator()
            .contains(new ForkPullRequestDiscoveryTrait(1, new TrustEveryone()));

        BitbucketCloudApiClient client = BitbucketClientMockUtils.getAPIClientMock(true, false);
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, client);

        List<BitbucketCloudBranch> branches =
            Collections.singletonList(new BitbucketCloudBranch(BRANCH_NAME, COMMIT_HASH, 0));
        when(client.getBranches()).thenReturn(branches);

        verifyExpectedClientApiCalls(instance, client);
    }

    @Test
    void retrieveTriggersRequiredApiCalls_server() throws Exception {
        BitbucketSCMSource instance = load("retrieve_prs_test_server");
        assertThat(instance.getId()).isEqualTo("retrieve_prs_test_server");
        assertThat(instance.getServerUrl()).isEqualTo(SERVER_REPO_URL);
        assertThat(instance.getRepoOwner()).isEqualTo(SERVER_REPO_OWNER);
        assertThat(instance.getRepository()).isEqualTo(REPO_NAME);
        assertThat(instance.getTraits())
            .usingRecursiveFieldByFieldElementComparator()
            .contains(new ForkPullRequestDiscoveryTrait(1, new TrustEveryone()));

        BitbucketServerAPIClient client = mock(BitbucketServerAPIClient.class);
        BitbucketMockApiFactory.add(SERVER_REPO_URL, client);

        List<BitbucketServerBranch> branches =
            Collections.singletonList(new BitbucketServerBranch(BRANCH_NAME, COMMIT_HASH));
        when(client.getBranches()).thenReturn(branches);
        when(client.getRepository()).thenReturn(repository);

        verifyExpectedClientApiCalls(instance, client);
    }

    /**
     * Given a BitbucketSCMSource, call the retrieve(SCMSourceCriteria, SCMHeadObserver, SCMHeadEvent, TaskListener)
     * method with an event having a PR and verify the expected client API calls
     *
     * @param instance The BitbucketSCMSource instance that has been configured with the traits required
     *                 for testing this code path.
     */
    private void verifyExpectedClientApiCalls(BitbucketSCMSource instance, BitbucketApi apiClient) throws Exception {
        String fullRepoName = instance.getRepoOwner() + '/' + instance.getRepository();
        when(repository.getFullName()).thenReturn(fullRepoName);
        when(repository.getRepositoryName()).thenReturn(instance.getRepository());

        when(pullRequest.getLink()).thenReturn(instance.getServerUrl() + '/' + fullRepoName + "/pull-requests/" + PR_ID);
        when(apiClient.getPullRequestById(PR_ID)).thenReturn(pullRequest);

        SCMHeadEvent<?> event = new HeadEvent(Collections.singleton(pullRequest));
        TaskListener taskListener = BitbucketClientMockUtils.getTaskListenerMock();
        SCMHeadObserver.Collector headObserver = new SCMHeadObserver.Collector();
        when(criteria.isHead(Mockito.any(), Mockito.same(taskListener))).thenReturn(true);

        instance.retrieve(criteria, headObserver, event, taskListener);

        // Expect the observer to collect the branch and the PR
        Set<String> heads =
            headObserver.result().keySet().stream().map(SCMHead::getName).collect(Collectors.toSet());
        assertThat(heads).containsExactlyInAnyOrder("PR-1", BRANCH_NAME);

        // Ensures PR is properly initialized, especially fork-based PRs
        // see BitbucketServerAPIClient.setupPullRequest()
        verify(apiClient).getPullRequestById(PR_ID);
        // The event is a HasPullRequests, so this call should be skipped in favor of getting PRs from the event itself
        verify(apiClient, never()).getPullRequests();
        // Fetch tags trait was not enabled on the BitbucketSCMSource
        verify(apiClient, never()).getTags();
    }

    private static final class HeadEvent extends SCMHeadEvent<BitbucketPullRequestEvent> implements HasPullRequests {
        private final Collection<BitbucketPullRequest> pullRequests;

        private HeadEvent(Collection<BitbucketPullRequest> pullRequests) {
            super(Type.UPDATED, 0, mock(BitbucketPullRequestEvent.class), "origin");
            this.pullRequests = pullRequests;
        }

        @Override
        public Iterable<BitbucketPullRequest> getPullRequests(BitbucketSCMSource src) {
            return pullRequests;
        }

        @Override
        public boolean isMatch(@NonNull SCMNavigator navigator) {
            return false;
        }

        @NonNull
        @Override
        public String getSourceName() {
            return REPO_NAME;
        }

        @NonNull
        @Override
        public Map<SCMHead, SCMRevision> heads(@NonNull SCMSource source) {
            return Collections.emptyMap();
        }

        @Override
        public boolean isMatch(@NonNull SCM scm) {
            return false;
        }
    }

    private BitbucketSCMSource load(String configuration) {
        BitbucketPlugin.aliases();
        String resource = this.getClass().getSimpleName() + "/" + configuration + ".xml";
        return (BitbucketSCMSource) Items.XSTREAM2.fromXML(this.getClass().getResource(resource));
    }
}
