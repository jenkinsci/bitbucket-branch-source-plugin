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
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
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
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

import static com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory.getApiMockClient;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class SCMTraitCommitTest {

    private final class SCMHeadObserverImpl extends SCMHeadObserver {

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
                    if (head instanceof PullRequestSCMHead) {
                        PullRequestSCMHead prHead = (PullRequestSCMHead) head;
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
                    assertThat("commit message is not valued", Util.fixEmptyAndTrim(branch.getMessage()), notNullValue());
                    assertThat("commit author is not valued", Util.fixEmptyAndTrim(branch.getAuthor()), notNullValue());
                    assertTrue("commit date is not valued", branch.getDateMillis() > 0);
                }

                private void verify(BitbucketCommit commit) {
                    assertThat("commit message is not valued", Util.fixEmptyAndTrim(commit.getMessage()), notNullValue());
                    assertThat("commit author is not valued", Util.fixEmptyAndTrim(commit.getAuthor()), notNullValue());
                    assertTrue("commit date is not valued", commit.getDateMillis() > 0);
                }
            });
        }

        public int getMatches() {
            return verified.size();
        }

        @Extension
        public static class DescriptorImpl extends SCMSourceTraitDescriptor {
        }
    }

    @SuppressWarnings("unchecked")
    @Parameters(name = "verify revision informations from {0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] { //
            { "branch on cloud", new BranchDiscoveryTrait(true, true), BitbucketCloudEndpoint.SERVER_URL }, //
            { "branch on server", new BranchDiscoveryTrait(true, true), "localhost" }, //
            { "PR on cloud", new OriginPullRequestDiscoveryTrait(2), BitbucketCloudEndpoint.SERVER_URL }, //
            { "PR on server", new OriginPullRequestDiscoveryTrait(2), "localhost" }, //
            { "forked on cloud", new ForkPullRequestDiscoveryTrait(2, Mockito.mock(SCMHeadAuthority.class)), BitbucketCloudEndpoint.SERVER_URL }, //
            { "forked on server", new ForkPullRequestDiscoveryTrait(2, Mockito.mock(SCMHeadAuthority.class)), "localhost" } //
        });
    }

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    private final SCMSourceTrait trait;
    private final String serverURL;

    public SCMTraitCommitTest(String testName, SCMSourceTrait trait, String serverURL) {
        this.trait = trait;
        this.serverURL = serverURL;
    }

    @Before
    public void setup() {
        BitbucketMockApiFactory.clear();
    }

    @Test
    public void verify_commit_info_are_valued() throws Exception {
        CommitVerifierTrait commitTrait = new CommitVerifierTrait();

        BitbucketMockApiFactory.add(serverURL, getApiMockClient(serverURL));
        BitbucketSCMSource source = new BitbucketSCMSource("amuniz", "test-repos");
        source.setServerUrl(serverURL);
        source.setTraits(Arrays.<SCMSourceTrait> asList(trait, commitTrait));

        TaskListener listener = BitbucketClientMockUtils.getTaskListenerMock();
        Set<SCMHead> heads = source.fetch(listener);

        assertThat(heads.size(), Matchers.greaterThan(0));

        SCMHeadObserverImpl observer = new SCMHeadObserverImpl();
        source.fetch(observer, BitbucketClientMockUtils.getTaskListenerMock());

        // the head branch should observe only branches which head commit was not filtered out
        assertThat(observer.getRevisions().size(), equalTo(commitTrait.getMatches()));
    }

}
