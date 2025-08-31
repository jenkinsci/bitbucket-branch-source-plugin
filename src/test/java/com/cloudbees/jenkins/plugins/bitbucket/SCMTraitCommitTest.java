/*
 * The MIT License
 *
 * Copyright (c) 2018, Nikolas Falco
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
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.test.util.BitbucketClientMockUtils;
import com.cloudbees.jenkins.plugins.bitbucket.trait.BranchDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.ForkPullRequestDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.OriginPullRequestDiscoveryTrait;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.trait.SCMHeadAuthority;
import jenkins.scm.api.trait.SCMHeadFilter;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mockito;

import static com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory.getApiMockClient;
import static org.assertj.core.api.Assertions.assertThat;

@WithJenkins
@ParameterizedClass(name = "verify revision information from {0}")
@MethodSource("data")
class SCMTraitCommitTest {

    private static final class SCMHeadObserverImpl extends SCMHeadObserver {

        public List<SCMRevision> revisions = new ArrayList<>();

        @Override
        public void observe(@NonNull SCMHead head, @NonNull SCMRevision revision) {
            revisions.add(revision);
        }

        public List<SCMRevision> getRevisions() {
            return revisions;
        }
    }

    private static class CommitVerifierTrait extends SCMSourceTrait {
        private Set<String> verified = new HashSet<>();

        @Override
        protected void decorateContext(SCMSourceContext<?, ?> context) {
            context.withFilter(new SCMHeadFilter() {

                @Override
                public boolean isExcluded(SCMSourceRequest request, SCMHead head) throws IOException, InterruptedException {
                    BitbucketSCMSourceRequest bbRequest = (BitbucketSCMSourceRequest) request;
                    if (head instanceof PullRequestSCMHead prHead) {
                        for (BitbucketPullRequest pr : bbRequest.getPullRequests()) {
                            if (prHead.getId().equals(pr.getId())) {
                                verify(pr.getSource().getBranch());
                                verify(pr.getSource().getCommit());
                                verify(pr.getDestination().getBranch());
                                verify(pr.getDestination().getCommit());

                                verified.add(head.getName());
                                break;
                            }
                        }
                    } else if (head instanceof BranchSCMHead) {
                        for (BitbucketBranch branch : bbRequest.getBranches()) {
                            if (head.getName().equals(branch.getName())) {
                                verify(branch);

                                verified.add(head.getName());
                                break;
                            }
                        }
                    }
                    return false;
                }

                private void verify(BitbucketBranch branch) {
                    assertThat(branch.getMessage())
                        .describedAs("commit message is not valued")
                        .isNotEmpty();
                    assertThat(branch.getAuthor())
                        .describedAs("commit author is not valued")
                        .isNotNull();
                    assertThat(branch.getDateMillis())
                        .describedAs("commit date is not valued")
                        .isGreaterThan(0);
                }

                private void verify(BitbucketCommit commit) {
                    assertThat(commit.getMessage())
                        .describedAs("commit message is not valued")
                        .isNotEmpty();
                    assertThat(commit.getAuthor())
                        .describedAs("commit author is not valued")
                        .isNotNull();
                    assertThat(commit.getCommitterDate())
                        .describedAs("commit date is not valued")
                        .isNotNull();
                }
            });
        }

        public int getMatches() {
            return verified.size();
        }

        @TestExtension
        public static class DescriptorImpl extends SCMSourceTraitDescriptor {
        }
    }

    @SuppressWarnings({ "unchecked", "deprecation" })
    static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] { //
            { "branch on cloud", new BranchDiscoveryTrait(true, true), BitbucketCloudEndpoint.SERVER_URL }, //
            { "branch on server", new BranchDiscoveryTrait(true, true), "localhost" }, //
            { "PR on cloud", new OriginPullRequestDiscoveryTrait(2), BitbucketCloudEndpoint.SERVER_URL }, //
            { "PR on server", new OriginPullRequestDiscoveryTrait(2), "localhost" }, //
            { "forked on cloud", new ForkPullRequestDiscoveryTrait(2, Mockito.mock(SCMHeadAuthority.class)), BitbucketCloudEndpoint.SERVER_URL }, //
            { "forked on server", new ForkPullRequestDiscoveryTrait(2, Mockito.mock(SCMHeadAuthority.class)), "localhost" } //
        });
    }

    @SuppressWarnings("unused")
    private static JenkinsRule rule;

    @Parameter(0)
    private String testName;
    @Parameter(1)
    private SCMSourceTrait trait;
    @Parameter(2)
    private String serverURL;

    @BeforeAll
    static void setUp(JenkinsRule rule) {
        SCMTraitCommitTest.rule = rule;
    }

    @BeforeEach
    void setup() {
        BitbucketMockApiFactory.clear();
    }

    @Test
    void verify_commit_info_are_valued() throws Exception {
        CommitVerifierTrait commitTrait = new CommitVerifierTrait();

        BitbucketMockApiFactory.add(serverURL, getApiMockClient(serverURL));
        BitbucketSCMSource source = new BitbucketSCMSource("amuniz", "test-repos");
        source.setServerUrl(serverURL);
        source.setTraits(Arrays.asList(trait, commitTrait));

        TaskListener listener = BitbucketClientMockUtils.getTaskListenerMock();
        Set<SCMHead> heads = source.fetch(listener);

        assertThat(heads).isNotEmpty();

        SCMHeadObserverImpl observer = new SCMHeadObserverImpl();
        source.fetch(observer, BitbucketClientMockUtils.getTaskListenerMock());

        // the head branch should observe only branches which head commit was not filtered out
        assertThat(observer.getRevisions()).hasSize(commitTrait.getMatches());
    }

}
