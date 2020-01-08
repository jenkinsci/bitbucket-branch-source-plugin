/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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

import com.cloudbees.jenkins.plugins.bitbucket.server.client.pullrequest.BitbucketServerPullRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import static com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource.applyPRsNamingStrategy;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class PullRequestNamingTest {
    private BitbucketServerPullRequest pr;
    private String sourceBranchName = "feature/branch-name";

    @Before
    public void loadPayload() throws IOException {
        // TODO: probably shouldn't recycle this json, but I needed a source for a nice BitbucketServerPullRequest object.
        try (InputStream is = getClass().getResourceAsStream("./server/events/BitbucketServerPullRequestEventTest/apiResponse.json")) {
            String payload = IOUtils.toString(is, "UTF-8");
            pr = JsonParser.toJava(payload, BitbucketServerPullRequest.class);
        }
    }

    @Test
    public void checkPullRequestNamingStrategy_PrId() throws Exception {
        String branchName;
        branchName = applyPRsNamingStrategy(
                pr,
                sourceBranchName,
                1,
                ChangeRequestCheckoutStrategy.MERGE,
                PullRequestNamingStrategy.PR_ID,
                Pattern.compile("")
        );
        assertThat(branchName, is("PR-1"));

        branchName = applyPRsNamingStrategy(
                pr,
                sourceBranchName,
                2,
                ChangeRequestCheckoutStrategy.MERGE,
                PullRequestNamingStrategy.PR_ID,
                Pattern.compile("")
        );
        assertThat(branchName, is("PR-1-merge"));

        branchName = applyPRsNamingStrategy(
                pr,
                sourceBranchName,
                2,
                ChangeRequestCheckoutStrategy.HEAD,
                PullRequestNamingStrategy.PR_ID,
                Pattern.compile("")
        );
        assertThat(branchName, is("PR-1-head"));
    }

    @Test
    public void checkPullRequestNamingStrategy_PrIdTitle() throws Exception {
        String branchName;
        branchName = applyPRsNamingStrategy(
                pr,
                sourceBranchName,
                1,
                ChangeRequestCheckoutStrategy.MERGE,
                PullRequestNamingStrategy.PR_ID_TITLE,
                Pattern.compile("")
        );
        assertThat(branchName, is("PR-1: Markdown formatting"));

        branchName = applyPRsNamingStrategy(
                pr,
                sourceBranchName,
                2,
                ChangeRequestCheckoutStrategy.MERGE,
                PullRequestNamingStrategy.PR_ID_TITLE,
                Pattern.compile("")
        );
        assertThat(branchName, is("PR-1-merge: Markdown formatting"));

        branchName = applyPRsNamingStrategy(
                pr,
                sourceBranchName,
                2,
                ChangeRequestCheckoutStrategy.HEAD,
                PullRequestNamingStrategy.PR_ID_TITLE,
                Pattern.compile("")
        );
        assertThat(branchName, is("PR-1-head: Markdown formatting"));
    }

    @Test
    public void checkPullRequestNamingStrategy_PrIdBranchName() throws Exception {
        String branchName;
        branchName = applyPRsNamingStrategy(
                pr,
                sourceBranchName,
                1,
                ChangeRequestCheckoutStrategy.MERGE,
                PullRequestNamingStrategy.PR_ID_BRANCH_NAME,
                Pattern.compile("")
        );
        assertThat(branchName, is("PR-1_feature/branch-name"));

        branchName = applyPRsNamingStrategy(
                pr,
                sourceBranchName,
                2,
                ChangeRequestCheckoutStrategy.MERGE,
                PullRequestNamingStrategy.PR_ID_BRANCH_NAME,
                Pattern.compile("")
        );
        assertThat(branchName, is("PR-1-merge_feature/branch-name"));

        branchName = applyPRsNamingStrategy(
                pr,
                sourceBranchName,
                2,
                ChangeRequestCheckoutStrategy.HEAD,
                PullRequestNamingStrategy.PR_ID_BRANCH_NAME,
                Pattern.compile("")
        );
        assertThat(branchName, is("PR-1-head_feature/branch-name"));
    }

    @Test
    public void checkPullRequestNamingStrategy_PrTitle() throws Exception {
        String branchName;
        branchName = applyPRsNamingStrategy(
                pr,
                sourceBranchName,
                1,
                ChangeRequestCheckoutStrategy.MERGE,
                PullRequestNamingStrategy.PR_TITLE,
                Pattern.compile("")
        );
        assertThat(branchName, is("Markdown formatting"));

        branchName = applyPRsNamingStrategy(
                pr,
                sourceBranchName,
                2,
                ChangeRequestCheckoutStrategy.MERGE,
                PullRequestNamingStrategy.PR_TITLE,
                Pattern.compile("")
        );
        assertThat(branchName, is("merge: Markdown formatting"));

        branchName = applyPRsNamingStrategy(
                pr,
                sourceBranchName,
                2,
                ChangeRequestCheckoutStrategy.HEAD,
                PullRequestNamingStrategy.PR_TITLE,
                Pattern.compile("")
        );
        assertThat(branchName, is("head: Markdown formatting"));
    }

    @Test
    public void checkPullRequestNamingStrategy_PrBranchName() throws Exception {
        String branchName;
        branchName = applyPRsNamingStrategy(
                pr,
                sourceBranchName,
                1,
                ChangeRequestCheckoutStrategy.MERGE,
                PullRequestNamingStrategy.PR_BRANCH_NAME,
                Pattern.compile("")
        );
        assertThat(branchName, is("feature/branch-name"));

        branchName = applyPRsNamingStrategy(
                pr,
                sourceBranchName,
                2,
                ChangeRequestCheckoutStrategy.MERGE,
                PullRequestNamingStrategy.PR_BRANCH_NAME,
                Pattern.compile("")
        );
        assertThat(branchName, is("merge_feature/branch-name"));

        branchName = applyPRsNamingStrategy(
                pr,
                sourceBranchName,
                2,
                ChangeRequestCheckoutStrategy.HEAD,
                PullRequestNamingStrategy.PR_BRANCH_NAME,
                Pattern.compile("")
        );
        assertThat(branchName, is("head_feature/branch-name"));
    }

    @Test
    public void checkPullRequestNamingStrategy_ExcludeRegex() throws Exception {
        String branchName;
        branchName = applyPRsNamingStrategy(
                pr,
                sourceBranchName,
                1,
                ChangeRequestCheckoutStrategy.MERGE,
                PullRequestNamingStrategy.PR_ID,
                Pattern.compile("^release/.*|^hotfix/.*")
        );
        assertThat(branchName, is("PR-1"));

        branchName = applyPRsNamingStrategy(
                pr,
                "release/1.0.0",
                1,
                ChangeRequestCheckoutStrategy.MERGE,
                PullRequestNamingStrategy.PR_ID,
                Pattern.compile("^release/.*|^hotfix/.*")
        );
        assertThat(branchName, is("release/1.0.0"));

        branchName = applyPRsNamingStrategy(
                pr,
                "release/1.0.0",
                2,
                ChangeRequestCheckoutStrategy.MERGE,
                PullRequestNamingStrategy.PR_ID,
                Pattern.compile("^release/.*|^hotfix/.*")
        );
        assertThat(branchName, is("release/1.0.0-merge"));

        branchName = applyPRsNamingStrategy(
                pr,
                "release/1.0.0",
                2,
                ChangeRequestCheckoutStrategy.HEAD,
                PullRequestNamingStrategy.PR_ID,
                Pattern.compile("^release/.*|^hotfix/.*")
        );
        assertThat(branchName, is("release/1.0.0-head"));
    }
}
