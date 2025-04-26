/*
 * The MIT License
 *
 * Copyright (c) 2025, Nikolas Falco
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
package com.cloudbees.jenkins.plugins.bitbucket.filesystem;

import com.cloudbees.jenkins.plugins.bitbucket.BranchSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.PullRequestBranchType;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import hudson.plugins.git.GitChangeLogParser;
import hudson.plugins.git.GitChangeSet;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import jenkins.plugins.git.AbstractGitSCMSource.SCMRevisionImpl;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.junit.jupiter.api.Test;

import static com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory.getApiMockClient;
import static org.assertj.core.api.Assertions.assertThat;

class BitbucketSCMFileSystemTest {

    @Test
    void verify_changeSince_of_pr_from_cloud() throws Exception {
        BitbucketApi client = getApiMockClient(BitbucketCloudEndpoint.SERVER_URL);
        PullRequestSCMHead prHead = new PullRequestSCMHead("PR-8", "amuniz", "test-repos",
                "feature/diffstat", PullRequestBranchType.BRANCH, "8",
                null, new BranchSCMHead("feature/diffstat"), SCMHeadOrigin.DEFAULT,
                ChangeRequestCheckoutStrategy.HEAD);

        BranchSCMHead masterHead = new BranchSCMHead("master");

        try (BitbucketSCMFileSystem sut = new BitbucketSCMFileSystem(client, "feature/diffstat", new SCMRevisionImpl(prHead, "251fce2"))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            sut.changesSince(new SCMRevisionImpl(masterHead, "174561d"), out);

            List<GitChangeSet> result = parseOutput(out);
            assertThat(result).isNotEmpty().hasSize(2);
            assertThat(result).element(0).satisfies(changeset -> {
                assertThat(changeset.getCommitId()).isEqualTo("251fce291f086cdde68ae2a896148abb5b58033e");
                assertThat(changeset.getParentCommit()).isEqualTo("2674e72983786424571e0c8fc5ac8eca17e0583f");
                assertThat(changeset.getAuthorName()).isEqualTo("Nikolas Falco");
                assertThat(changeset.getAuthorEmail()).isEqualTo("amuniz@acme.com");
                assertThat(changeset.getComment()).isEqualTo("Provide some change to work diffstats:\n* add a new\n");
                assertThat(changeset.getTimestamp()).isEqualTo(1745661059000L);
            });
            assertThat(result).element(1).satisfies(changeset -> {
                assertThat(changeset.getCommitId()).isEqualTo("2674e72983786424571e0c8fc5ac8eca17e0583f");
                assertThat(changeset.getParentCommit()).isEqualTo("174561d625c9623b60d8aba09b7f08ddc9df45cd");
                assertThat(changeset.getAuthorName()).isEqualTo("Nikolas Falco");
                assertThat(changeset.getAuthorEmail()).isEqualTo("amuniz@acme.com");
                assertThat(changeset.getComment()).isEqualTo("Provide some change to work diffstats:\n* add a new\n* modify a file\n* rename a file\n");
                assertThat(changeset.getTimestamp()).isEqualTo(1745660941000L);
            });
        }
    }

    @Test
    void verify_changeSince_of_pr_from_server() throws Exception {
        BitbucketApi client = getApiMockClient("https://bitbucket.server");
        PullRequestSCMHead prHead = new PullRequestSCMHead("PR-1", "amuniz", "test-repos",
                "feature/diffstat", PullRequestBranchType.BRANCH, "1",
                null, new BranchSCMHead("feature/diffstat"), SCMHeadOrigin.DEFAULT,
                ChangeRequestCheckoutStrategy.HEAD);

        BranchSCMHead masterHead = new BranchSCMHead("master");

        try (BitbucketSCMFileSystem sut = new BitbucketSCMFileSystem(client, "feature/diffstat", new SCMRevisionImpl(prHead, "251fce291f086cdde68ae2a896148abb5b58033e"))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            sut.changesSince(new SCMRevisionImpl(masterHead, "174561d625c9623b60d8aba09b7f08ddc9df45cd"), out);

            List<GitChangeSet> result = parseOutput(out);
            assertThat(result).isNotEmpty().hasSize(2);
            assertThat(result).element(0).satisfies(changeset -> {
                assertThat(changeset.getCommitId()).isEqualTo("251fce291f086cdde68ae2a896148abb5b58033e");
                assertThat(changeset.getParentCommit()).isEqualTo("2674e72983786424571e0c8fc5ac8eca17e0583f");
                assertThat(changeset.getAuthorName()).isEqualTo("Nikolas Falco");
                assertThat(changeset.getAuthorEmail()).isEqualTo("amuniz@acme.com");
                assertThat(changeset.getComment()).isEqualTo("Provide some change to work diffstats:\n* add a new\n");
                assertThat(changeset.getTimestamp()).isEqualTo(1745661059000L);
            });
            assertThat(result).element(1).satisfies(changeset -> {
                assertThat(changeset.getCommitId()).isEqualTo("2674e72983786424571e0c8fc5ac8eca17e0583f");
                assertThat(changeset.getParentCommit()).isEqualTo("174561d625c9623b60d8aba09b7f08ddc9df45cd");
                assertThat(changeset.getAuthorName()).isEqualTo("Nikolas Falco");
                assertThat(changeset.getAuthorEmail()).isEqualTo("amuniz@acme.com");
                assertThat(changeset.getComment()).isEqualTo("Provide some change to work diffstats:\n* add a new\n* modify a file\n* rename a file\n");
                assertThat(changeset.getTimestamp()).isEqualTo(1745660941000L);
            });
        }
    }

    private List<GitChangeSet> parseOutput(ByteArrayOutputStream out) throws IOException {
        GitChangeLogParser parser = new GitChangeLogParser(null, false);
        return parser.parse(new ByteArrayInputStream(out.toByteArray()));
    }
}
