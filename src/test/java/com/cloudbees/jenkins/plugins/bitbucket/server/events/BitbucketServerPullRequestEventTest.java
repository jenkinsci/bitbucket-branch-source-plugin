/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket.server.events;

import com.cloudbees.jenkins.plugins.bitbucket.JsonParser;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestEvent;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerWebhookPayload;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.pullrequest.BitbucketServerPullRequest;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class BitbucketServerPullRequestEventTest {
    @Rule
    public final TestName testName = new TestName();

    private String payload;

    @Before
    public void loadPayload() throws IOException {
        try (InputStream is = getClass()
                .getResourceAsStream(getClass().getSimpleName() + "/" + testName.getMethodName() + ".json")) {
            payload = IOUtils.toString(is, "UTF-8");
        }
    }

    @Test
    public void updatePayload() throws Exception {
        BitbucketPullRequestEvent event =
                BitbucketServerWebhookPayload.pullRequestEventFromPayload(payload);
        assertThat(event.getRepository(), notNullValue());
        assertThat(event.getRepository().getScm(), is("git"));
        assertThat(event.getRepository().getFullName(), is("PROJECT_1/rep_1"));
        assertThat(event.getRepository().getOwner().getDisplayName(), is("Project 1"));
        assertThat(event.getRepository().getOwner().getUsername(), is("PROJECT_1"));
        assertThat(event.getRepository().getRepositoryName(), is("rep_1"));
        assertThat(event.getRepository().isPrivate(), is(true));
        assertThat(event.getRepository().getLinks(), notNullValue());
        assertThat(event.getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getRepository().getLinks().get("self").get(0).getHref(),
                is("http://local.example.com:7990/bitbucket/projects/PROJECT_1/repos/rep_1/browse"));

        assertThat(event.getPullRequest(), notNullValue());
        assertThat(event.getPullRequest().getTitle(), is("Markdown formatting"));
        assertThat(event.getPullRequest().getAuthorLogin(), is("User"));
        assertThat(event.getPullRequest().getLink(),
                is("http://local.example.com:7990/bitbucket/projects/PROJECT_1/repos/rep_1/pull-requests/1"));

        assertThat(event.getPullRequest().getDestination(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getScm(), is("git"));
        assertThat(event.getPullRequest().getDestination().getRepository().getFullName(), is("PROJECT_1/rep_1"));
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getDisplayName(), is("Project 1"));
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getUsername(), is("PROJECT_1"));
        assertThat(event.getPullRequest().getDestination().getRepository().getRepositoryName(), is("rep_1"));
        assertThat(event.getPullRequest().getDestination().getRepository().isPrivate(), is(true));
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self").get(0).getHref(),
                is("http://local.example.com:7990/bitbucket/projects/PROJECT_1/repos/rep_1/browse"));
        assertThat(event.getPullRequest().getDestination().getBranch(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getBranch().getName(), is("main"));
        assertThat(event.getPullRequest().getDestination().getBranch().getRawNode(), is("d235f0c0aa22f4c75b2fb63b217e39e2d3c29f49"));
        assertThat(event.getPullRequest().getDestination().getCommit(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getCommit().getHash(), is(
                "d235f0c0aa22f4c75b2fb63b217e39e2d3c29f49"));

        assertThat(event.getPullRequest().getSource(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getScm(), is("git"));
        assertThat(event.getPullRequest().getSource().getRepository().getFullName(), is("~USER/rep_1"));
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getDisplayName(), is("User"));
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getUsername(), is("~USER"));
        assertThat(event.getPullRequest().getSource().getRepository().getRepositoryName(), is("rep_1"));
        assertThat(event.getPullRequest().getSource().getRepository().isPrivate(), is(true));
        assertThat(event.getPullRequest().getSource().getRepository().getLinks(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self").get(0).getHref(),
                anyOf(is("http://local.example.com:7990/bitbucket/projects/~USER/repos/rep_1/browse"),
                        is("http://local.example.com:7990/bitbucket/users/user/repos/rep_1/browse")));

        assertThat(event.getPullRequest().getSource().getBranch(), notNullValue());
        assertThat(event.getPullRequest().getSource().getBranch().getName(), is("main"));
        assertThat(event.getPullRequest().getSource().getBranch().getRawNode(),
                is("feb8d676cd70406cecd4128c8fd1bee30282db11"));
        assertThat(event.getPullRequest().getSource().getCommit(), notNullValue());
        assertThat(event.getPullRequest().getSource().getCommit().getHash(), is(
                "feb8d676cd70406cecd4128c8fd1bee30282db11"));
    }

    @Test
    public void apiResponse() throws Exception {
        BitbucketServerPullRequest pullRequest =
                JsonParser.toJava(payload, BitbucketServerPullRequest.class);
        assertThat(pullRequest, notNullValue());
        assertThat(pullRequest.getTitle(), is("Markdown formatting"));
        assertThat(pullRequest.getAuthorLogin(), is("User"));
        assertThat(pullRequest.getLink(),
                is("http://local.example.com:7990/bitbucket/projects/PROJECT_1/repos/rep_1/pull-requests/1"));

        assertThat(pullRequest.getDestination(), notNullValue());
        assertThat(pullRequest.getDestination().getRepository(), notNullValue());
        assertThat(pullRequest.getDestination().getRepository().getScm(), is("git"));
        assertThat(pullRequest.getDestination().getRepository().getFullName(), is("PROJECT_1/rep_1"));
        assertThat(pullRequest.getDestination().getRepository().getOwner().getDisplayName(),
                is("Project 1"));
        assertThat(pullRequest.getDestination().getRepository().getOwner().getUsername(), is("PROJECT_1"));
        assertThat(pullRequest.getDestination().getRepository().getRepositoryName(), is("rep_1"));
        assertThat(pullRequest.getDestination().getRepository().isPrivate(), is(true));
        assertThat(pullRequest.getDestination().getRepository().getLinks(), notNullValue());
        assertThat(pullRequest.getDestination().getRepository().getLinks().get("self"), notNullValue());
        assertThat(pullRequest.getDestination().getRepository().getLinks().get("self").get(0).getHref(),
                is("http://local.example.com:7990/bitbucket/projects/PROJECT_1/repos/rep_1/browse"));
        assertThat(pullRequest.getDestination().getBranch(), notNullValue());
        assertThat(pullRequest.getDestination().getBranch().getName(), is("main"));
        assertThat(pullRequest.getDestination().getBranch().getRawNode(),
                is("d235f0c0aa22f4c75b2fb63b217e39e2d3c29f49"));
        assertThat(pullRequest.getDestination().getCommit(), notNullValue());
        assertThat(pullRequest.getDestination().getCommit().getHash(), is(
                "d235f0c0aa22f4c75b2fb63b217e39e2d3c29f49"));

        assertThat(pullRequest.getSource(), notNullValue());
        assertThat(pullRequest.getSource().getRepository(), notNullValue());
        assertThat(pullRequest.getSource().getRepository().getScm(), is("git"));
        assertThat(pullRequest.getSource().getRepository().getFullName(), is("~USER/rep_1"));
        assertThat(pullRequest.getSource().getRepository().getOwner().getDisplayName(), is("User"));
        assertThat(pullRequest.getSource().getRepository().getOwner().getUsername(), is("~USER"));
        assertThat(pullRequest.getSource().getRepository().getRepositoryName(), is("rep_1"));
        assertThat(pullRequest.getSource().getRepository().isPrivate(), is(true));
        assertThat(pullRequest.getSource().getRepository().getLinks(), notNullValue());
        assertThat(pullRequest.getSource().getRepository().getLinks().get("self"), notNullValue());
        assertThat(pullRequest.getSource().getRepository().getLinks().get("self").get(0).getHref(),
                anyOf(is("http://local.example.com:7990/bitbucket/projects/~USER/repos/rep_1/browse"),
                        is("http://local.example.com:7990/bitbucket/users/user/repos/rep_1/browse"))
        );

        assertThat(pullRequest.getSource().getBranch(), notNullValue());
        assertThat(pullRequest.getSource().getBranch().getName(), is("main"));
        assertThat(pullRequest.getSource().getBranch().getRawNode(),
                is("feb8d676cd70406cecd4128c8fd1bee30282db11"));
        assertThat(pullRequest.getSource().getCommit(), notNullValue());
        assertThat(pullRequest.getSource().getCommit().getHash(), is(
                "feb8d676cd70406cecd4128c8fd1bee30282db11"));

    }
}
