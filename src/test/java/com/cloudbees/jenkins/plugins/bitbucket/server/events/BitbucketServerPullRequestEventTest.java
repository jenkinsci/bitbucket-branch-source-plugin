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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestEvent;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.JsonParser;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerWebhookPayload;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.pullrequest.BitbucketServerPullRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static org.assertj.core.api.Assertions.assertThat;

class BitbucketServerPullRequestEventTest {

    private String payload;

    @BeforeEach
    void loadPayload(TestInfo info) throws IOException {
        try (InputStream is = getClass()
            .getResourceAsStream(getClass().getSimpleName() + "/" + info.getTestMethod().orElseThrow().getName() + ".json")) {
            assertThat(is).isNotNull();
            payload = IOUtils.toString(is, StandardCharsets.UTF_8);
        }
    }

    @Test
    void updatePayload() throws Exception {
        BitbucketPullRequestEvent event = BitbucketServerWebhookPayload.pullRequestEventFromPayload(payload);
        assertThat(event.getRepository()).isNotNull();
        assertThat(event.getRepository().getScm()).isEqualTo("git");
        assertThat(event.getRepository().getFullName()).isEqualTo("PROJECT_1/rep_1");
        assertThat(event.getRepository().getOwner().getDisplayName()).isEqualTo("Project 1");
        assertThat(event.getRepository().getOwner().getUsername()).isEqualTo("PROJECT_1");
        assertThat(event.getRepository().getRepositoryName()).isEqualTo("rep_1");
        assertThat(event.getRepository().isPrivate()).isTrue();
        assertThat(event.getRepository().getLinks()).isNotNull();
        assertThat(event.getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getRepository().getLinks().get("self").get(0).getHref())
            .isEqualTo("http://local.example.com:7990/bitbucket/projects/PROJECT_1/repos/rep_1/browse");

        assertThat(event.getPullRequest()).isNotNull();
        assertThat(event.getPullRequest().getTitle()).isEqualTo("Markdown formatting");
        assertThat(event.getPullRequest().getAuthorLogin()).isEqualTo("User");
        assertThat(event.getPullRequest().getLink())
            .isEqualTo("http://local.example.com:7990/bitbucket/projects/PROJECT_1/repos/rep_1/pull-requests/1");

        assertThat(event.getPullRequest().getDestination()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getScm()).isEqualTo("git");
        assertThat(event.getPullRequest().getDestination().getRepository().getFullName()).isEqualTo("PROJECT_1/rep_1");
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getDisplayName()).isEqualTo("Project 1");
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getUsername()).isEqualTo("PROJECT_1");
        assertThat(event.getPullRequest().getDestination().getRepository().getRepositoryName()).isEqualTo("rep_1");
        assertThat(event.getPullRequest().getDestination().getRepository().isPrivate()).isTrue();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self").get(0).getHref())
            .isEqualTo("http://local.example.com:7990/bitbucket/projects/PROJECT_1/repos/rep_1/browse");
        assertThat(event.getPullRequest().getDestination().getBranch()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getBranch().getName()).isEqualTo("main");
        assertThat(event.getPullRequest().getDestination().getBranch().getRawNode()).isEqualTo("d235f0c0aa22f4c75b2fb63b217e39e2d3c29f49");
        assertThat(event.getPullRequest().getDestination().getCommit()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getCommit().getHash())
            .isEqualTo("d235f0c0aa22f4c75b2fb63b217e39e2d3c29f49");

        assertThat(event.getPullRequest().getSource()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getScm()).isEqualTo("git");
        assertThat(event.getPullRequest().getSource().getRepository().getFullName()).isEqualTo("~USER/rep_1");
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getDisplayName()).isEqualTo("User");
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getUsername()).isEqualTo("~USER");
        assertThat(event.getPullRequest().getSource().getRepository().getRepositoryName()).isEqualTo("rep_1");
        assertThat(event.getPullRequest().getSource().getRepository().isPrivate()).isTrue();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self").get(0).getHref())
            .containsAnyOf("http://local.example.com:7990/bitbucket/projects/~USER/repos/rep_1/browse",
                           "http://local.example.com:7990/bitbucket/users/user/repos/rep_1/browse");

        assertThat(event.getPullRequest().getSource().getBranch()).isNotNull();
        assertThat(event.getPullRequest().getSource().getBranch().getName()).isEqualTo("main");
        assertThat(event.getPullRequest().getSource().getBranch().getRawNode())
            .isEqualTo("feb8d676cd70406cecd4128c8fd1bee30282db11");
        assertThat(event.getPullRequest().getSource().getCommit()).isNotNull();
        assertThat(event.getPullRequest().getSource().getCommit().getHash())
            .isEqualTo("feb8d676cd70406cecd4128c8fd1bee30282db11");
    }

    @Test
    void apiResponse() throws Exception {
        BitbucketServerPullRequest pullRequest =
                JsonParser.toJava(payload, BitbucketServerPullRequest.class);
        assertThat(pullRequest).isNotNull();
        assertThat(pullRequest.getTitle()).isEqualTo("Markdown formatting");
        assertThat(pullRequest.getAuthorLogin()).isEqualTo("User");
        assertThat(pullRequest.getLink())
                .isEqualTo("http://local.example.com:7990/bitbucket/projects/PROJECT_1/repos/rep_1/pull-requests/1");

        assertThat(pullRequest.getDestination()).isNotNull();
        assertThat(pullRequest.getDestination().getRepository()).isNotNull();
        assertThat(pullRequest.getDestination().getRepository().getScm()).isEqualTo("git");
        assertThat(pullRequest.getDestination().getRepository().getFullName()).isEqualTo("PROJECT_1/rep_1");
        assertThat(pullRequest.getDestination().getRepository().getOwner().getDisplayName()).isEqualTo("Project 1");
        assertThat(pullRequest.getDestination().getRepository().getOwner().getUsername()).isEqualTo("PROJECT_1");
        assertThat(pullRequest.getDestination().getRepository().getRepositoryName()).isEqualTo("rep_1");
        assertThat(pullRequest.getDestination().getRepository().isPrivate()).isTrue();
        assertThat(pullRequest.getDestination().getRepository().getLinks()).isNotNull();
        assertThat(pullRequest.getDestination().getRepository().getLinks().get("self")).isNotNull();
        assertThat(pullRequest.getDestination().getRepository().getLinks().get("self").get(0).getHref())
                .isEqualTo("http://local.example.com:7990/bitbucket/projects/PROJECT_1/repos/rep_1/browse");
        assertThat(pullRequest.getDestination().getBranch()).isNotNull();
        assertThat(pullRequest.getDestination().getBranch().getName()).isEqualTo("main");
        assertThat(pullRequest.getDestination().getBranch().getRawNode())
                .isEqualTo("d235f0c0aa22f4c75b2fb63b217e39e2d3c29f49");
        assertThat(pullRequest.getDestination().getCommit()).isNotNull();
        assertThat(pullRequest.getDestination().getCommit().getHash()).isEqualTo("d235f0c0aa22f4c75b2fb63b217e39e2d3c29f49");

        assertThat(pullRequest.getSource()).isNotNull();
        assertThat(pullRequest.getSource().getRepository()).isNotNull();
        assertThat(pullRequest.getSource().getRepository().getScm()).isEqualTo("git");
        assertThat(pullRequest.getSource().getRepository().getFullName()).isEqualTo("~USER/rep_1");
        assertThat(pullRequest.getSource().getRepository().getOwner().getDisplayName()).isEqualTo("User");
        assertThat(pullRequest.getSource().getRepository().getOwner().getUsername()).isEqualTo("~USER");
        assertThat(pullRequest.getSource().getRepository().getRepositoryName()).isEqualTo("rep_1");
        assertThat(pullRequest.getSource().getRepository().isPrivate()).isTrue();
        assertThat(pullRequest.getSource().getRepository().getLinks()).isNotNull();
        assertThat(pullRequest.getSource().getRepository().getLinks().get("self")).isNotNull();
        assertThat(pullRequest.getSource().getRepository().getLinks().get("self").get(0).getHref())
            .containsAnyOf("http://local.example.com:7990/bitbucket/projects/~USER/repos/rep_1/browse",
                           "http://local.example.com:7990/bitbucket/users/user/repos/rep_1/browse");

        assertThat(pullRequest.getSource().getBranch()).isNotNull();
        assertThat(pullRequest.getSource().getBranch().getName()).isEqualTo("main");
        assertThat(pullRequest.getSource().getBranch().getRawNode())
            .isEqualTo("feb8d676cd70406cecd4128c8fd1bee30282db11");
        assertThat(pullRequest.getSource().getCommit()).isNotNull();
        assertThat(pullRequest.getSource().getCommit().getHash())
            .isEqualTo("feb8d676cd70406cecd4128c8fd1bee30282db11");

    }
}
