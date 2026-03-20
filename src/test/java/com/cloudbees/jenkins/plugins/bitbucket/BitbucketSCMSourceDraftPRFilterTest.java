/*
 * The MIT License
 *
 * Copyright (c) 2026, Brevium, Inc.
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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBranch;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketMockApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestDestination;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestEvent;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.api.HasPullRequests;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudBranch;
import com.cloudbees.jenkins.plugins.bitbucket.impl.BitbucketPlugin;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.test.util.BitbucketClientMockUtils;
import com.cloudbees.jenkins.plugins.bitbucket.trait.SkipDraftPullRequestFilterTrait;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Items;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import java.util.Arrays;
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
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.Mockito.when;

/**
 * Tests that the {@link SkipDraftPullRequestFilterTrait}
 * correctly filters draft pull requests during source retrieval.
 */
@WithJenkins
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BitbucketSCMSourceDraftPRFilterTest {

    private static final String REPO_OWNER = "cloudbeers";
    private static final String REPO_NAME = "stunning-adventure";
    private static final String BRANCH_NAME = "main";
    private static final String COMMIT_HASH = "e851558f77c098d21af6bb8cc54a423f7cf12147";

    /** A regular (non-draft) pull request */
    private static final Integer PR_NON_DRAFT_1_ID = 10;
    /** A draft pull request */
    private static final Integer PR_DRAFT_ID = 11;
    /** Another regular (non-draft) pull request */
    private static final Integer PR_NON_DRAFT_2_ID = 12;

    @SuppressWarnings("unused")
    private static JenkinsRule jenkinsRule;

    @Mock private BitbucketRepository repository;
    @Mock private BitbucketBranch sourceBranch1;
    @Mock private BitbucketBranch sourceBranch2;
    @Mock private BitbucketBranch sourceBranch3;
    @Mock private BitbucketBranch destinationBranch;
    @Mock private BitbucketPullRequestDestination prDestination;
    @Mock private BitbucketPullRequestSource prSource1;
    @Mock private BitbucketPullRequestSource prSource2;
    @Mock private BitbucketPullRequestSource prSource3;
    @Mock private BitbucketCommit commit;
    @Mock private BitbucketPullRequest nonDraftPR1;
    @Mock private BitbucketPullRequest draftPR;
    @Mock private BitbucketPullRequest nonDraftPR2;
    @Mock private SCMSourceCriteria criteria;

    @BeforeAll
    static void init(JenkinsRule r) {
        jenkinsRule = r;
    }

    @BeforeEach
    void setUp() {
        String fullName = REPO_OWNER + "/" + REPO_NAME;
        when(repository.getFullName()).thenReturn(fullName);
        when(repository.getRepositoryName()).thenReturn(REPO_NAME);
        when(repository.getOwnerName()).thenReturn(REPO_OWNER);

        when(commit.getHash()).thenReturn(COMMIT_HASH);
        when(destinationBranch.getName()).thenReturn(BRANCH_NAME);
        when(prDestination.getRepository()).thenReturn(repository);
        when(prDestination.getBranch()).thenReturn(destinationBranch);

        // PR-10: non-draft, source branch "feature-1"
        when(sourceBranch1.getName()).thenReturn("feature-1");
        when(prSource1.getRepository()).thenReturn(repository);
        when(prSource1.getBranch()).thenReturn(sourceBranch1);
        when(prSource1.getCommit()).thenReturn(commit);
        when(nonDraftPR1.getId()).thenReturn(PR_NON_DRAFT_1_ID.toString());
        when(nonDraftPR1.getSource()).thenReturn(prSource1);
        when(nonDraftPR1.getDestination()).thenReturn(prDestination);
        when(nonDraftPR1.isDraft()).thenReturn(false);
        when(nonDraftPR1.getLink()).thenReturn("https://bitbucket.org/" + fullName + "/pull-requests/" + PR_NON_DRAFT_1_ID);

        // PR-11: draft, source branch "feature-draft"
        when(sourceBranch2.getName()).thenReturn("feature-draft");
        when(prSource2.getRepository()).thenReturn(repository);
        when(prSource2.getBranch()).thenReturn(sourceBranch2);
        when(prSource2.getCommit()).thenReturn(commit);
        when(draftPR.getId()).thenReturn(PR_DRAFT_ID.toString());
        when(draftPR.getSource()).thenReturn(prSource2);
        when(draftPR.getDestination()).thenReturn(prDestination);
        when(draftPR.isDraft()).thenReturn(true);
        when(draftPR.getLink()).thenReturn("https://bitbucket.org/" + fullName + "/pull-requests/" + PR_DRAFT_ID);

        // PR-12: non-draft, source branch "feature-2"
        when(sourceBranch3.getName()).thenReturn("feature-2");
        when(prSource3.getRepository()).thenReturn(repository);
        when(prSource3.getBranch()).thenReturn(sourceBranch3);
        when(prSource3.getCommit()).thenReturn(commit);
        when(nonDraftPR2.getId()).thenReturn(PR_NON_DRAFT_2_ID.toString());
        when(nonDraftPR2.getSource()).thenReturn(prSource3);
        when(nonDraftPR2.getDestination()).thenReturn(prDestination);
        when(nonDraftPR2.isDraft()).thenReturn(false);
        when(nonDraftPR2.getLink()).thenReturn("https://bitbucket.org/" + fullName + "/pull-requests/" + PR_NON_DRAFT_2_ID);
    }

    @AfterEach
    void tearDown() {
        BitbucketMockApiFactory.clear();
    }

    @Test
    void withoutIgnoreDraftTrait_allPRsAreDiscovered() throws Exception {
        BitbucketSCMSource instance = load("draft_pr_no_ignore_trait");

        BitbucketCloudApiClient client = BitbucketClientMockUtils.getAPIClientMock(false, false);
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, client);

        when(client.getBranches()).thenReturn(Collections.singletonList(new BitbucketCloudBranch(BRANCH_NAME, COMMIT_HASH, 0)));
        when(client.getPullRequestById(PR_NON_DRAFT_1_ID)).thenReturn(nonDraftPR1);
        when(client.getPullRequestById(PR_DRAFT_ID)).thenReturn(draftPR);
        when(client.getPullRequestById(PR_NON_DRAFT_2_ID)).thenReturn(nonDraftPR2);

        SCMHeadEvent<?> event = new PullRequestHeadEvent(Arrays.asList(nonDraftPR1, draftPR, nonDraftPR2));
        TaskListener taskListener = BitbucketClientMockUtils.getTaskListenerMock();
        SCMHeadObserver.Collector headObserver = new SCMHeadObserver.Collector();
        when(criteria.isHead(Mockito.any(), Mockito.same(taskListener))).thenReturn(true);

        instance.retrieve(criteria, headObserver, event, taskListener);

        Set<String> heads = headObserver.result().keySet().stream().map(SCMHead::getName).collect(Collectors.toSet());
        assertThat(heads).contains("PR-" + PR_NON_DRAFT_1_ID, "PR-" + PR_DRAFT_ID, "PR-" + PR_NON_DRAFT_2_ID);
    }

    @Test
    void withIgnoreDraftTrait_draftPRsAreExcluded() throws Exception {
        BitbucketSCMSource instance = load("draft_pr_with_ignore_trait");

        BitbucketCloudApiClient client = BitbucketClientMockUtils.getAPIClientMock(false, false);
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, client);

        when(client.getBranches()).thenReturn(Collections.singletonList(new BitbucketCloudBranch(BRANCH_NAME, COMMIT_HASH, 0)));
        when(client.getPullRequestById(PR_NON_DRAFT_1_ID)).thenReturn(nonDraftPR1);
        when(client.getPullRequestById(PR_DRAFT_ID)).thenReturn(draftPR);
        when(client.getPullRequestById(PR_NON_DRAFT_2_ID)).thenReturn(nonDraftPR2);

        SCMHeadEvent<?> event = new PullRequestHeadEvent(Arrays.asList(nonDraftPR1, draftPR, nonDraftPR2));
        TaskListener taskListener = BitbucketClientMockUtils.getTaskListenerMock();
        SCMHeadObserver.Collector headObserver = new SCMHeadObserver.Collector();
        when(criteria.isHead(Mockito.any(), Mockito.same(taskListener))).thenReturn(true);

        instance.retrieve(criteria, headObserver, event, taskListener);

        Set<String> heads = headObserver.result().keySet().stream().map(SCMHead::getName).collect(Collectors.toSet());
        assertThat(heads).contains("PR-" + PR_NON_DRAFT_1_ID, "PR-" + PR_NON_DRAFT_2_ID);
        assertThat(heads).doesNotContain("PR-" + PR_DRAFT_ID);
    }

    private static final class PullRequestHeadEvent extends SCMHeadEvent<BitbucketPullRequestEvent> implements HasPullRequests {
        private final List<BitbucketPullRequest> pullRequests;

        private PullRequestHeadEvent(List<BitbucketPullRequest> pullRequests) {
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
