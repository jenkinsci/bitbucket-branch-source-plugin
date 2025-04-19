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

import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.trait.BranchDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.ForkPullRequestDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.ForkPullRequestDiscoveryTrait.TrustTeamForks;
import com.cloudbees.jenkins.plugins.bitbucket.trait.OriginPullRequestDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.TagDiscoveryTrait;
import hudson.model.TaskListener;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.TagSCMHead;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory.getApiMockClient;
import static org.assertj.core.api.Assertions.assertThat;

@WithJenkins
class BitbucketGitSCMRevisionTest {

    @SuppressWarnings("unused")
    private static JenkinsRule j;

    @BeforeAll
    static void init(JenkinsRule rule) {
        j = rule;
    }

    private static Stream<Arguments> revisionDataProvider() {
        return Stream.of(Arguments.of("branch on cloud", new BranchDiscoveryTrait(true, true), BitbucketCloudEndpoint.SERVER_URL), //
                Arguments.of("branch on server", new BranchDiscoveryTrait(true, true), "localhost"), //
                Arguments.of("PR on cloud", new OriginPullRequestDiscoveryTrait(2), BitbucketCloudEndpoint.SERVER_URL), //
                Arguments.of("PR on server", new OriginPullRequestDiscoveryTrait(2), "localhost"), //
                Arguments.of("forked on cloud", new ForkPullRequestDiscoveryTrait(2, new TrustTeamForks()), BitbucketCloudEndpoint.SERVER_URL), //
                Arguments.of("forked on server", new ForkPullRequestDiscoveryTrait(2, new TrustTeamForks()), "localhost"), //
                Arguments.of("Tags on cloud", new TagDiscoveryTrait(), BitbucketCloudEndpoint.SERVER_URL), //
                Arguments.of("Tags on server", new TagDiscoveryTrait(), "localhost") //
        );
    }

    @ParameterizedTest(name = "verify revision informations from {0}")
    @MethodSource("revisionDataProvider")
    void verify_revision_informations_are_valued(String testName, SCMSourceTrait trait, String serverURL) throws Exception {
        BitbucketMockApiFactory.add(serverURL, getApiMockClient(serverURL));
        BitbucketSCMSource source = new BitbucketSCMSource("amuniz", "test-repos");
        source.setServerUrl(serverURL);
        source.setTraits(Arrays.<SCMSourceTrait> asList(trait));

        TaskListener listener = BitbucketClientMockUtils.getTaskListenerMock();
        Set<SCMHead> heads = source.fetch(listener);

        assertThat(heads).isNotEmpty();

        for (SCMHead head : heads) {
            if (head instanceof BranchSCMHead) {
                BitbucketGitSCMRevision revision = (BitbucketGitSCMRevision) source.retrieve(head, listener);
                assertRevision(revision);
            } else if (head instanceof PullRequestSCMHead) {
                PullRequestSCMRevision revision = (PullRequestSCMRevision) source.retrieve(head, listener);
                assertRevision((BitbucketGitSCMRevision) revision.getPull());
                assertRevision((BitbucketGitSCMRevision) revision.getTarget());
            } else if (head instanceof TagSCMHead) {
                BitbucketTagSCMRevision revision = (BitbucketTagSCMRevision) source.retrieve(head, listener);
                assertRevision(revision);
            }
        }
    }

    private void assertRevision(BitbucketGitSCMRevision revision) {
        assertThat(revision.getMessage())
            .describedAs("commit message is not valued for revision {}", revision.getHash())
            .isNotEmpty();
        assertThat(revision.getAuthor())
            .describedAs("commit author is not valued for revision {}", revision.getHash())
            .isNotEmpty();
        assertThat(revision.getDate())
            .describedAs("commit date is not valued for revision {}", revision.getHash())
            .isNotNull();
    }

}
