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
import hudson.Util;
import hudson.model.TaskListener;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.trait.SCMHeadAuthority;
import jenkins.scm.api.trait.SCMSourceTrait;
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
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(Parameterized.class)
public class BitbucketGitSCMRevisionTest {

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

    public BitbucketGitSCMRevisionTest(String testName, SCMSourceTrait trait, String serverURL) {
        this.trait = trait;
        this.serverURL = serverURL;
    }

    @Before
    public void setup() {
        BitbucketMockApiFactory.clear();
    }

    @Test
    public void verify_revision_informations_are_valued() throws Exception {
        BitbucketMockApiFactory.add(serverURL, getApiMockClient(serverURL));
        BitbucketSCMSource source = new BitbucketSCMSource("amuniz", "test-repos");
        source.setServerUrl(serverURL);
        source.setTraits(Arrays.<SCMSourceTrait> asList(trait));

        TaskListener listener = BitbucketClientMockUtils.getTaskListenerMock();
        Set<SCMHead> heads = source.fetch(listener);

        assertThat(heads.size(), Matchers.greaterThan(0));

        for (SCMHead head : heads) {
            if (head instanceof BranchSCMHead) {
                BitbucketGitSCMRevision revision = (BitbucketGitSCMRevision) source.retrieve(head, listener);
                assertRevision(revision);
            } else if (head instanceof PullRequestSCMHead) {
                @SuppressWarnings("unchecked")
                PullRequestSCMRevision<BitbucketGitSCMRevision> revision = (PullRequestSCMRevision<BitbucketGitSCMRevision>) source.retrieve(head, listener);
                assertRevision(revision.getPull());
                assertRevision((BitbucketGitSCMRevision) revision.getTarget());
            }
        }
    }

    private void assertRevision(BitbucketGitSCMRevision revision) {
        assertThat("commit message is not valued for revision " + revision.getHash(), Util.fixEmptyAndTrim(revision.getMessage()), notNullValue());
        assertThat("commit author is not valued for revision " + revision.getHash(), Util.fixEmptyAndTrim(revision.getAuthor()), notNullValue());
        assertThat("commit date is not valued for revision " + revision.getHash(), revision.getDate(), notNullValue());
    }

}
