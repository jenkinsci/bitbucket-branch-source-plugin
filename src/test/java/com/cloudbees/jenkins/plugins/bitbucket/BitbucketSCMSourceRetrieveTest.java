package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBranch;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestDestination;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestEvent;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudBranch;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.HasPullRequests;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerAPIClient;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.branch.BitbucketServerBranch;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests different scenarios of the
 * {@link BitbucketSCMSource#retrieve(SCMSourceCriteria, SCMHeadObserver, SCMHeadEvent, TaskListener)} method.
 *
 * This test was created to validate a fix for the issue described in:
 * https://github.com/jenkinsci/bitbucket-branch-source-plugin/issues/469
 */
@RunWith(MockitoJUnitRunner.class)
public class BitbucketSCMSourceRetrieveTest {

    private static final String CLOUD_REPO_OWNER = "cloudbeers";
    private static final String SERVER_REPO_OWNER = "DUB";
    private static final String SERVER_REPO_URL = "https://bitbucket.test";
    private static final String REPO_NAME = "stunning-adventure";
    private static final String BRANCH_NAME = "branch1";
    private static final String COMMIT_HASH = "e851558f77c098d21af6bb8cc54a423f7cf12147";
    private static final Integer PR_ID = 1;

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule();

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

    @Before
    public void setUp() {
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
    public void retrieveTriggersRequiredApiCalls_cloud() throws Exception {
        BitbucketSCMSource instance = load("retrieve_prs_test_cloud");
        assertThat(instance.getId(), is("retrieve_prs_test_cloud"));
        assertThat(instance.getServerUrl(), is(BitbucketCloudEndpoint.SERVER_URL));
        assertThat(instance.getRepoOwner(), is(CLOUD_REPO_OWNER));
        assertThat(instance.getRepository(), is(REPO_NAME));
        BitbucketCloudApiClient client = BitbucketClientMockUtils.getAPIClientMock(true, false);
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, client);

        List<BitbucketCloudBranch> branches =
            Collections.singletonList(new BitbucketCloudBranch(BRANCH_NAME, COMMIT_HASH, 0));
        when(client.getBranches()).thenReturn(branches);

        verifyExpectedClientApiCalls(instance, client);
    }

    @Test
    public void retrieveTriggersRequiredApiCalls_server() throws Exception {
        BitbucketSCMSource instance = load("retrieve_prs_test_server");
        assertThat(instance.getServerUrl(), is(SERVER_REPO_URL));
        assertThat(instance.getRepoOwner(), is(SERVER_REPO_OWNER));
        assertThat(instance.getRepository(), is(REPO_NAME));

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
        assertThat(heads, Matchers.containsInAnyOrder("PR-1", BRANCH_NAME));

        // Ensures PR is properly initialized, especially fork-based PRs
        // see BitbucketServerAPIClient.setupPullRequest()
        verify(apiClient, Mockito.times(1)).getPullRequestById(PR_ID);
        // The event is a HasPullRequests, so this call should be skipped in favor of getting PRs from the event itself
        verify(apiClient, Mockito.never()).getPullRequests();
        // Fetch tags trait was not enabled on the BitbucketSCMSource
        verify(apiClient, Mockito.never()).getTags();
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
        String path = getClass().getSimpleName() + "/" + configuration + ".xml";
        URL url = getClass().getResource(path);
        return (BitbucketSCMSource) Jenkins.XSTREAM2.fromXML(url);
    }
}
