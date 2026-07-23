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
import com.cloudbees.jenkins.plugins.bitbucket.api.HasPullRequests;
import com.cloudbees.jenkins.plugins.bitbucket.api.HasTags;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudBranch;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
    private static final String TAG_NAME = "test-tag";
    private static final Integer PR_ID = 1;

    @SuppressWarnings("unused")
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
    private BitbucketBranch tag;
    @Mock
    private SCMSourceCriteria criteria;
    private SCMHeadObserver.Collector headObserver;

    @BeforeAll
    static void init(JenkinsRule r) {
        jenkinsRule = r;
    }

    @BeforeEach
    void setUp() {
        when(prDestination.getRepository()).thenReturn(repository);
        when(prDestination.getBranch()).thenReturn(destinationBranch);
        when(destinationBranch.getName()).thenReturn("main");

        when(commit.getHash()).thenReturn(COMMIT_HASH);
        when(sourceBranch.getName()).thenReturn(BRANCH_NAME);
        when(sourceBranch.getRawNode()).thenReturn(COMMIT_HASH);

        when(prSource.getRepository()).thenReturn(repository);
        when(prSource.getBranch()).thenReturn(sourceBranch);
        when(prSource.getCommit()).thenReturn(commit);

        when(pullRequest.getSource()).thenReturn(prSource);
        when(pullRequest.getDestination()).thenReturn(prDestination);
        when(pullRequest.getId()).thenReturn(PR_ID.toString());

        when(tag.getAuthor()).thenReturn("acme");
        when(tag.getRawNode()).thenReturn(COMMIT_HASH);
        when(tag.getName()).thenReturn(TAG_NAME);

        headObserver = new SCMHeadObserver.Collector();
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

        List<BitbucketBranch> branches = Collections.singletonList(new BitbucketCloudBranch(BRANCH_NAME, COMMIT_HASH, 0));
        when(client.getBranches()).thenReturn(branches);

        SCMHeadEvent<?> event = new HeadEvent(List.of(pullRequest), List.of(tag));
        dryRun(instance, event, client);

        // Expect the observer to collect the branch and the PR
        Set<String> heads = headObserver.result().keySet().stream().map(SCMHead::getName).collect(Collectors.toSet());
        assertThat(heads).containsExactlyInAnyOrder("PR-1", BRANCH_NAME, TAG_NAME);

        // Ensures PR is properly initialized, especially fork-based PRs
        // see BitbucketServerAPIClient.setupPullRequest()
        verify(client).getPullRequestById(PR_ID);
        verify(client).getTag(TAG_NAME);
        // The event is a HasPullRequests, so this call should be skipped in favor of getting PRs from the event itself
        verify(client, never()).getPullRequests();
        // Fetch tags trait was not enabled on the BitbucketSCMSource
        verify(client, never()).getTags();
    }

    @Test
    void retrieve_fetches_primary_clone_links_by_default() throws Exception {
        BitbucketSCMSource instance = load("retrieve_prs_test_cloud");

        BitbucketCloudApiClient client = BitbucketClientMockUtils.getAPIClientMock(true, false);
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, client);

        List<BitbucketBranch> branches = Collections.singletonList(new BitbucketCloudBranch(BRANCH_NAME, COMMIT_HASH, 0));
        when(client.getBranches()).thenReturn(branches);

        SCMHeadEvent<?> event = new HeadEvent(List.of(pullRequest), List.of(tag));
        dryRun(instance, event, client);

        verify(client).getRepository();
    }

    @Test
    void retrieve_does_not_fetch_primary_clone_links_when_skip_property_is_set() throws Exception {
        BitbucketSCMSource instance = load("retrieve_prs_test_cloud");

        BitbucketCloudApiClient client = BitbucketClientMockUtils.getAPIClientMock(true, false);
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, client);

        List<BitbucketBranch> branches = Collections.singletonList(new BitbucketCloudBranch(BRANCH_NAME, COMMIT_HASH, 0));
        when(client.getBranches()).thenReturn(branches);

        SCMHeadEvent<?> event = new HeadEvent(List.of(pullRequest), List.of(tag));
        System.setProperty(BitbucketSCMSource.SKIP_PRIMARY_CLONE_LINKS_PROPERTY_NAME, "true");
        try {
            dryRun(instance, event, client);
        } finally {
            System.clearProperty(BitbucketSCMSource.SKIP_PRIMARY_CLONE_LINKS_PROPERTY_NAME);
        }

        // heads are still discovered as usual
        Set<String> heads = headObserver.result().keySet().stream().map(SCMHead::getName).collect(Collectors.toSet());
        assertThat(heads).containsExactlyInAnyOrder("PR-1", BRANCH_NAME, TAG_NAME);
        // but the per-source repository lookup used to gather primary clone links is skipped
        verify(client, never()).getRepository();
    }

    @Test
    void prEvent_does_not_trigger_tag_API_endpoints_cloud() throws Exception {
        BitbucketSCMSource instance = load("retrieve_prs_test_cloud");
        assertThat(instance.getId()).isEqualTo("retrieve_prs_test_cloud");
        assertThat(instance.getServerUrl()).isEqualTo(BitbucketCloudEndpoint.SERVER_URL);
        assertThat(instance.getRepoOwner()).isEqualTo(CLOUD_REPO_OWNER);
        assertThat(instance.getRepository()).isEqualTo(REPO_NAME);

        BitbucketCloudApiClient client = BitbucketClientMockUtils.getAPIClientMock(true, false);
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, client);

        List<BitbucketBranch> branches = Collections.singletonList(new BitbucketCloudBranch(BRANCH_NAME, COMMIT_HASH, 0));
        when(client.getBranches()).thenReturn(branches);

        SCMHeadEvent<?> event = new HeadEvent(List.of(pullRequest), Collections.emptyList());
        dryRun(instance, event, client);

        // Expect the observer to collect the branch and the PR
        Set<String> heads = headObserver.result().keySet().stream().map(SCMHead::getName).collect(Collectors.toSet());
        assertThat(heads).containsExactlyInAnyOrder("PR-1", BRANCH_NAME);

        verify(client, never()).getTag(anyString());
        // The event is a HasTags, so this call should be skipped in favor of getting tags from the event itself
        verify(client, never()).getTags();
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

        List<BitbucketBranch> branches = Collections.singletonList(new BitbucketServerBranch(BRANCH_NAME, COMMIT_HASH));
        when(client.getBranches()).thenReturn(branches);
        when(client.getRepository()).thenReturn(repository);
        when(client.getTag(TAG_NAME)).thenReturn(tag);

        SCMHeadEvent<?> event = new HeadEvent(List.of(pullRequest), List.of(tag));
        dryRun(instance, event, client);

        // Expect the observer to collect the branch and the PR
        Set<String> heads = headObserver.result().keySet().stream().map(SCMHead::getName).collect(Collectors.toSet());
        assertThat(heads).containsExactlyInAnyOrder("PR-1", BRANCH_NAME, TAG_NAME);

        // Ensures PR is properly initialized, especially fork-based PRs
        // see BitbucketServerAPIClient.setupPullRequest()
        verify(client).getPullRequestById(PR_ID);
        verify(client).getTag(TAG_NAME);
        // The event is a HasPullRequests, so this call should be skipped in favor of getting PRs from the event itself
        verify(client, never()).getPullRequests();
        // Fetch tags trait was not enabled on the BitbucketSCMSource
        verify(client, never()).getTags();
    }

    @Test
    void prEvent_does_not_trigger_tag_API_endpoints_server() throws Exception {
        BitbucketSCMSource instance = load("retrieve_prs_test_server");
        assertThat(instance.getId()).isEqualTo("retrieve_prs_test_server");
        assertThat(instance.getServerUrl()).isEqualTo(SERVER_REPO_URL);
        assertThat(instance.getRepoOwner()).isEqualTo(SERVER_REPO_OWNER);
        assertThat(instance.getRepository()).isEqualTo(REPO_NAME);

        BitbucketServerAPIClient client = mock(BitbucketServerAPIClient.class);
        BitbucketMockApiFactory.add(SERVER_REPO_URL, client);

        List<BitbucketBranch> branches = Collections.singletonList(new BitbucketServerBranch(BRANCH_NAME, COMMIT_HASH));
        when(client.getBranches()).thenReturn(branches);
        when(client.getRepository()).thenReturn(repository);
        when(client.getTag(TAG_NAME)).thenReturn(tag);

        SCMHeadEvent<?> event = new HeadEvent(List.of(pullRequest), Collections.emptyList());
        dryRun(instance, event, client);

        // Expect the observer to collect the branch and the PR
        Set<String> heads = headObserver.result().keySet().stream().map(SCMHead::getName).collect(Collectors.toSet());
        assertThat(heads).containsExactlyInAnyOrder("PR-1", BRANCH_NAME);

        verify(client, never()).getTag(anyString());
        // The event is a HasTags, so this call should be skipped in favor of getting tags from the event itself
        verify(client, never()).getTags();
    }

    @Test
    void push_does_not_trigger_tag_or_PR_API_endpoints_server() throws Exception {
        BitbucketSCMSource instance = load("retrieve_prs_test_server");
        assertThat(instance.getId()).isEqualTo("retrieve_prs_test_server");
        assertThat(instance.getServerUrl()).isEqualTo(SERVER_REPO_URL);
        assertThat(instance.getRepoOwner()).isEqualTo(SERVER_REPO_OWNER);
        assertThat(instance.getRepository()).isEqualTo(REPO_NAME);

        BitbucketServerAPIClient client = mock(BitbucketServerAPIClient.class);
        BitbucketMockApiFactory.add(SERVER_REPO_URL, client);

        List<BitbucketBranch> branches = Collections.singletonList(new BitbucketServerBranch(BRANCH_NAME, COMMIT_HASH));
        when(client.getBranches()).thenReturn(branches);
        when(client.getRepository()).thenReturn(repository);
        when(client.getTag(TAG_NAME)).thenReturn(tag);

        SCMHeadEvent<?> event = new HeadEvent(Collections.emptyList(), Collections.emptyList());
        dryRun(instance, event, client);

        // Expect the observer to collect the branch and the PR
        Set<String> heads = headObserver.result().keySet().stream().map(SCMHead::getName).collect(Collectors.toSet());
        assertThat(heads).containsExactlyInAnyOrder(BRANCH_NAME);

        verify(client, never()).getPullRequestById(anyInt());
        verify(client, never()).getTag(anyString());
        // The event is a HasPullRequests, so this call should be skipped in favor of getting PRs from the event itself
        verify(client, never()).getPullRequests();
        // The event is a HasTags, so this call should be skipped in favor of getting tags from the event itself
        verify(client, never()).getTags();
    }

    /*
     * Given a BitbucketSCMSource, call the retrieve(SCMSourceCriteria, SCMHeadObserver, SCMHeadEvent, TaskListener)
     * method with an event having a PR and verify the expected client API calls
     */
    private void dryRun(BitbucketSCMSource instance, SCMHeadEvent<?> event, BitbucketApi apiClient) throws Exception {
        String fullRepoName = instance.getRepoOwner() + '/' + instance.getRepository();
        when(repository.getFullName()).thenReturn(fullRepoName);
        when(repository.getRepositoryName()).thenReturn(instance.getRepository());

        when(pullRequest.getLink()).thenReturn(instance.getServerUrl() + '/' + fullRepoName + "/pull-requests/" + PR_ID);
        when(apiClient.getPullRequestById(PR_ID)).thenReturn(pullRequest);
        when(apiClient.getTag(TAG_NAME)).thenReturn(tag);
        when(apiClient.resolveCommit(COMMIT_HASH)).thenReturn(commit);

        TaskListener taskListener = BitbucketClientMockUtils.getTaskListenerMock();
        when(criteria.isHead(Mockito.any(), Mockito.same(taskListener))).thenReturn(true);

        instance.retrieve(criteria, headObserver, event, taskListener);
    }

    private static final class HeadEvent extends SCMHeadEvent<BitbucketPullRequestEvent> implements HasPullRequests, HasTags {
        private final Collection<BitbucketPullRequest> pullRequests;
        private final Collection<BitbucketBranch> tags;

        private HeadEvent(Collection<BitbucketPullRequest> pullRequests, Collection<BitbucketBranch> tags) {
            super(Type.UPDATED, 0, mock(BitbucketPullRequestEvent.class), "origin");
            this.pullRequests = pullRequests;
            this.tags = tags;
        }

        @Override
        public Iterable<BitbucketPullRequest> getPullRequests(BitbucketSCMSource src) {
            return pullRequests;
        }

        @Override
        public Iterable<BitbucketBranch> getTags(BitbucketSCMSource src) {
            return tags;
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
