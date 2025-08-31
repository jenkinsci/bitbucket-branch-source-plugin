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
package com.cloudbees.jenkins.plugins.bitbucket.client.events;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestEvent;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudWebhookPayload;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BitbucketCloudPullRequestEventTest {

    private String payload;

    @BeforeEach
    void loadPayload(TestInfo info) throws IOException {
        try (InputStream is = getClass()
                .getResourceAsStream(getClass().getSimpleName() + "/" + info.getTestMethod().orElseThrow().getName() + ".json")) {
            assertNotNull(is);
            payload = IOUtils.toString(is, StandardCharsets.UTF_8);
        }
    }

    @Test
    void createPayloadOrigin() throws Exception {
        BitbucketPullRequestEvent event = BitbucketCloudWebhookPayload.pullRequestEventFromPayload(payload);
        assertThat(event.getRepository()).isNotNull();
        assertThat(event.getRepository().getScm()).isEqualTo("git");
        assertThat(event.getRepository().getFullName()).isEqualTo("cloudbeers/temp");
        assertThat(event.getRepository().getOwner().getDisplayName()).isEqualTo("cloudbeers");
        assertThat(event.getRepository().getOwner().getUsername()).isEqualTo("cloudbeers");
        assertThat(event.getRepository().getRepositoryName()).isEqualTo("temp");
        assertThat(event.getRepository().isPrivate()).isEqualTo(true);
        assertThat(event.getRepository().getLinks()).isNotNull();
        assertThat(event.getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getRepository().getLinks().get("self").get(0).getHref())
            .isEqualTo("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp");

        assertThat(event.getPullRequest()).isNotNull();
        assertThat(event.getPullRequest().getTitle()).isEqualTo("README.md edited online with Bitbucket");
        assertThat(event.getPullRequest().getAuthorIdentifier()).isEqualTo("123456:dead-beef");
        assertThat(event.getPullRequest().getAuthorLogin()).isEqualTo("Stephen Connolly");
        assertThat(event.getPullRequest().getLink())
            .isEqualTo("https://bitbucket.org/cloudbeers/temp/pull-requests/1");

        assertThat(event.getPullRequest().getDestination()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getScm()).isEqualTo("git");
        assertThat(event.getPullRequest().getDestination().getRepository().getFullName()).isEqualTo("cloudbeers/temp");
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getDisplayName()).isEqualTo("cloudbeers");
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getUsername()).isEqualTo("cloudbeers");
        assertThat(event.getPullRequest().getDestination().getRepository().getRepositoryName()).isEqualTo("temp");
        assertThat(event.getPullRequest().getDestination().getRepository().isPrivate()).isTrue();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self").get(0).getHref())
            .isEqualTo("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp");
        assertThat(event.getPullRequest().getDestination().getBranch()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getBranch().getName()).isEqualTo("main");
        assertThat(event.getPullRequest().getDestination().getBranch().getRawNode())
            .containsAnyOf("f612156eff2c", "f612156eff2c958f52f8e6e20c71f396aeaeaff4");
        assertThat(event.getPullRequest().getDestination().getCommit()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getCommit().getHash())
            .containsAnyOf("f612156eff2c", "f612156eff2c958f52f8e6e20c71f396aeaeaff4");

        assertThat(event.getPullRequest().getSource()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getScm()).isEqualTo("git");
        assertThat(event.getPullRequest().getSource().getRepository().getFullName()).isEqualTo("cloudbeers/temp");
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getDisplayName()).isEqualTo("cloudbeers");
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getUsername()).isEqualTo("cloudbeers");
        assertThat(event.getPullRequest().getSource().getRepository().getRepositoryName()).isEqualTo("temp");
        assertThat(event.getPullRequest().getSource().getRepository().isPrivate()).isTrue();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self").get(0).getHref())
            .isEqualTo("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp");

        assertThat(event.getPullRequest().getSource().getBranch()).isNotNull();
        assertThat(event.getPullRequest().getSource().getBranch().getName()).isEqualTo("foo");
        assertThat(event.getPullRequest().getSource().getBranch().getRawNode())
            .containsAnyOf("a72355f35fde", "a72355f35fde2ad4f5724a279b970ef7b6729131");
        assertThat(event.getPullRequest().getSource().getCommit()).isNotNull();
        assertThat(event.getPullRequest().getSource().getCommit().getHash())
            .containsAnyOf("a72355f35fde", "a72355f35fde2ad4f5724a279b970ef7b6729131");
    }

    @Test
    void createPayloadFork() throws Exception {
        BitbucketPullRequestEvent event = BitbucketCloudWebhookPayload.pullRequestEventFromPayload(payload);

        assertThat(event.getRepository()).isNotNull();
        assertThat(event.getRepository().getScm()).isEqualTo("git");
        assertThat(event.getRepository().getFullName()).isEqualTo("cloudbeers/temp");
        assertThat(event.getRepository().getOwner().getDisplayName()).isEqualTo("cloudbeers");
        assertThat(event.getRepository().getOwner().getUsername()).isEqualTo("cloudbeers");
        assertThat(event.getRepository().getRepositoryName()).isEqualTo("temp");
        assertThat(event.getRepository().isPrivate()).isTrue();
        assertThat(event.getRepository().getLinks()).isNotNull();
        assertThat(event.getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getRepository().getLinks().get("self").get(0).getHref())
            .isEqualTo("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp");

        assertThat(event.getPullRequest()).isNotNull();
        assertThat(event.getPullRequest().getTitle()).isEqualTo("Forking for PR");
        assertThat(event.getPullRequest().getAuthorIdentifier()).isEqualTo("123456:dead-beef");
        assertThat(event.getPullRequest().getAuthorLogin()).isEqualTo("Stephen Connolly");
        assertThat(event.getPullRequest().getLink())
            .isEqualTo("https://bitbucket.org/cloudbeers/temp/pull-requests/3");

        assertThat(event.getPullRequest().getDestination()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getScm()).isEqualTo("git");
        assertThat(event.getPullRequest().getDestination().getRepository().getFullName()).isEqualTo("cloudbeers/temp");
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getDisplayName()).isEqualTo("cloudbeers");
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getUsername()).isEqualTo("cloudbeers");
        assertThat(event.getPullRequest().getDestination().getRepository().getRepositoryName()).isEqualTo("temp");
        assertThat(event.getPullRequest().getDestination().getRepository().isPrivate()).isTrue();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self").get(0).getHref())
                .isEqualTo("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp");
        assertThat(event.getPullRequest().getDestination().getBranch()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getBranch().getName()).isEqualTo("main");
        assertThat(event.getPullRequest().getDestination().getBranch().getRawNode())
                .containsAnyOf("1986c2284946", "1986c228494671574242f99b62d1a00a4bfb69a5");
        assertThat(event.getPullRequest().getDestination().getCommit()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getCommit().getHash())
                .containsAnyOf("1986c2284946", "1986c228494671574242f99b62d1a00a4bfb69a5");

        assertThat(event.getPullRequest().getSource()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getScm()).isEqualTo("git");
        assertThat(event.getPullRequest().getSource().getRepository().getFullName()).isEqualTo("stephenc/temp-fork");
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getDisplayName()).isEqualTo("Stephen Connolly");
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getUsername()).isEqualTo("stephenc");
        assertThat(event.getPullRequest().getSource().getRepository().getRepositoryName()).isEqualTo("temp-fork");
        assertThat(event.getPullRequest().getSource().getRepository().isPrivate()).isTrue();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self").get(0).getHref())
                .isEqualTo("https://api.bitbucket.org/2.0/repositories/stephenc/temp-fork");

        assertThat(event.getPullRequest().getSource().getBranch()).isNotNull();
        assertThat(event.getPullRequest().getSource().getBranch().getName()).isEqualTo("main");
        assertThat(event.getPullRequest().getSource().getBranch().getRawNode())
                .containsAnyOf("1c48041a96db", "1c48041a96db4c98620609260c21ff5fbc9640c2");
        assertThat(event.getPullRequest().getSource().getCommit()).isNotNull();
        assertThat(event.getPullRequest().getSource().getCommit().getHash())
                .containsAnyOf("1c48041a96db", "1c48041a96db4c98620609260c21ff5fbc9640c2");
    }

    @Test
    void updatePayload_newCommit() throws Exception {
        BitbucketPullRequestEvent event = BitbucketCloudWebhookPayload.pullRequestEventFromPayload(payload);
        assertThat(event.getRepository()).isNotNull();
        assertThat(event.getRepository().getScm()).isEqualTo("git");
        assertThat(event.getRepository().getFullName()).isEqualTo("cloudbeers/temp");
        assertThat(event.getRepository().getOwner().getDisplayName()).isEqualTo("cloudbeers");
        assertThat(event.getRepository().getOwner().getUsername()).isEqualTo("cloudbeers");
        assertThat(event.getRepository().getRepositoryName()).isEqualTo("temp");
        assertThat(event.getRepository().isPrivate()).isTrue();
        assertThat(event.getRepository().getLinks()).isNotNull();
        assertThat(event.getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getRepository().getLinks().get("self").get(0).getHref())
                .isEqualTo("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp");

        assertThat(event.getPullRequest()).isNotNull();
        assertThat(event.getPullRequest().getTitle()).isEqualTo("Forking for PR");
        assertThat(event.getPullRequest().getAuthorIdentifier()).isEqualTo("123456:dead-beef");
        assertThat(event.getPullRequest().getAuthorLogin()).isEqualTo("Stephen Connolly");
        assertThat(event.getPullRequest().getLink())
                .isEqualTo("https://bitbucket.org/cloudbeers/temp/pull-requests/3");

        assertThat(event.getPullRequest().getDestination()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getScm()).isEqualTo("git");
        assertThat(event.getPullRequest().getDestination().getRepository().getFullName()).isEqualTo("cloudbeers/temp");
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getDisplayName()).isEqualTo("cloudbeers");
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getUsername()).isEqualTo("cloudbeers");
        assertThat(event.getPullRequest().getDestination().getRepository().getRepositoryName()).isEqualTo("temp");
        assertThat(event.getPullRequest().getDestination().getRepository().isPrivate()).isTrue();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self").get(0).getHref())
                .isEqualTo("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp");
        assertThat(event.getPullRequest().getDestination().getBranch()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getBranch().getName()).isEqualTo("main");
        assertThat(event.getPullRequest().getDestination().getBranch().getRawNode())
                .containsAnyOf("1986c2284946", "1986c228494671574242f99b62d1a00a4bfb69a5");
        assertThat(event.getPullRequest().getDestination().getCommit()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getCommit().getHash())
                .containsAnyOf("1986c2284946", "1986c228494671574242f99b62d1a00a4bfb69a5");

        assertThat(event.getPullRequest().getSource()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getScm()).isEqualTo("git");
        assertThat(event.getPullRequest().getSource().getRepository().getFullName()).isEqualTo("stephenc/temp-fork");
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getDisplayName()).isEqualTo("Stephen Connolly");
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getUsername()).isEqualTo("stephenc");
        assertThat(event.getPullRequest().getSource().getRepository().getRepositoryName()).isEqualTo("temp-fork");
        assertThat(event.getPullRequest().getSource().getRepository().isPrivate()).isTrue();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self").get(0).getHref())
                .isEqualTo("https://api.bitbucket.org/2.0/repositories/stephenc/temp-fork");

        assertThat(event.getPullRequest().getSource().getBranch()).isNotNull();
        assertThat(event.getPullRequest().getSource().getBranch().getName()).isEqualTo("main");
        assertThat(event.getPullRequest().getSource().getBranch().getRawNode())
                .containsAnyOf("63e3d18dca4c", "63e3d18dca4c61e6b9e31eb6036802c7730fa2b3");
        assertThat(event.getPullRequest().getSource().getCommit()).isNotNull();
        assertThat(event.getPullRequest().getSource().getCommit().getHash())
                .containsAnyOf("63e3d18dca4c", "63e3d18dca4c61e6b9e31eb6036802c7730fa2b3");
    }

    @Test
    void updatePayload_newDestination() throws Exception {
        BitbucketPullRequestEvent event = BitbucketCloudWebhookPayload.pullRequestEventFromPayload(payload);
        assertThat(event.getRepository()).isNotNull();
        assertThat(event.getRepository().getScm()).isEqualTo("git");
        assertThat(event.getRepository().getFullName()).isEqualTo("cloudbeers/temp");
        assertThat(event.getRepository().getOwner().getDisplayName()).isEqualTo("cloudbeers");
        assertThat(event.getRepository().getOwner().getUsername()).isEqualTo("cloudbeers");
        assertThat(event.getRepository().getRepositoryName()).isEqualTo("temp");
        assertThat(event.getRepository().isPrivate()).isTrue();
        assertThat(event.getRepository().getLinks()).isNotNull();
        assertThat(event.getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getRepository().getLinks().get("self").get(0).getHref())
                .isEqualTo("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp");

        assertThat(event.getPullRequest()).isNotNull();
        assertThat(event.getPullRequest().getTitle()).isEqualTo("Forking for PR");
        assertThat(event.getPullRequest().getAuthorIdentifier()).isEqualTo("123456:dead-beef");
        assertThat(event.getPullRequest().getAuthorLogin()).isEqualTo("Stephen Connolly");
        assertThat(event.getPullRequest().getLink())
                .isEqualTo("https://bitbucket.org/cloudbeers/temp/pull-requests/3");

        assertThat(event.getPullRequest().getDestination()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getScm()).isEqualTo("git");
        assertThat(event.getPullRequest().getDestination().getRepository().getFullName()).isEqualTo("cloudbeers/temp");
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getDisplayName()).isEqualTo("cloudbeers");
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getUsername()).isEqualTo("cloudbeers");
        assertThat(event.getPullRequest().getDestination().getRepository().getRepositoryName()).isEqualTo("temp");
        assertThat(event.getPullRequest().getDestination().getRepository().isPrivate()).isTrue();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self").get(0).getHref())
                .isEqualTo("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp");
        assertThat(event.getPullRequest().getDestination().getBranch()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getBranch().getName()).isEqualTo("stable");
        assertThat(event.getPullRequest().getDestination().getBranch().getRawNode())
                .containsAnyOf("1986c2284946", "1986c228494671574242f99b62d1a00a4bfb69a5");
        assertThat(event.getPullRequest().getDestination().getCommit()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getCommit().getHash())
                .containsAnyOf("1986c2284946", "1986c228494671574242f99b62d1a00a4bfb69a5");

        assertThat(event.getPullRequest().getSource()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getScm()).isEqualTo("git");
        assertThat(event.getPullRequest().getSource().getRepository().getFullName()).isEqualTo("stephenc/temp-fork");
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getDisplayName()).isEqualTo("Stephen Connolly");
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getUsername()).isEqualTo("stephenc");
        assertThat(event.getPullRequest().getSource().getRepository().getRepositoryName()).isEqualTo("temp-fork");
        assertThat(event.getPullRequest().getSource().getRepository().isPrivate()).isTrue();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self").get(0).getHref())
                .isEqualTo("https://api.bitbucket.org/2.0/repositories/stephenc/temp-fork");

        assertThat(event.getPullRequest().getSource().getBranch()).isNotNull();
        assertThat(event.getPullRequest().getSource().getBranch().getName()).isEqualTo("main");
        assertThat(event.getPullRequest().getSource().getBranch().getRawNode())
                .containsAnyOf("63e3d18dca4c", "63e3d18dca4c61e6b9e31eb6036802c7730fa2b3");
        assertThat(event.getPullRequest().getSource().getCommit()).isNotNull();
        assertThat(event.getPullRequest().getSource().getCommit().getHash())
                .containsAnyOf("63e3d18dca4c", "63e3d18dca4c61e6b9e31eb6036802c7730fa2b3");
    }

    @Test
    void updatePayload_newDestinationCommit() throws Exception {
        BitbucketPullRequestEvent event = BitbucketCloudWebhookPayload.pullRequestEventFromPayload(payload);
        assertThat(event.getRepository()).isNotNull();
        assertThat(event.getRepository().getScm()).isEqualTo("git");
        assertThat(event.getRepository().getFullName()).isEqualTo("cloudbeers/temp");
        assertThat(event.getRepository().getOwner().getDisplayName()).isEqualTo("cloudbeers");
        assertThat(event.getRepository().getOwner().getUsername()).isEqualTo("cloudbeers");
        assertThat(event.getRepository().getRepositoryName()).isEqualTo("temp");
        assertThat(event.getRepository().isPrivate()).isTrue();
        assertThat(event.getRepository().getLinks()).isNotNull();
        assertThat(event.getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getRepository().getLinks().get("self").get(0).getHref())
                .isEqualTo("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp");

        assertThat(event.getPullRequest()).isNotNull();
        assertThat(event.getPullRequest().getTitle()).isEqualTo("Forking for PR");
        assertThat(event.getPullRequest().getAuthorIdentifier()).isEqualTo("123456:dead-beef");
        assertThat(event.getPullRequest().getAuthorLogin()).isEqualTo("Stephen Connolly");
        assertThat(event.getPullRequest().getLink())
                .isEqualTo("https://bitbucket.org/cloudbeers/temp/pull-requests/3");

        assertThat(event.getPullRequest().getDestination()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getScm()).isEqualTo("git");
        assertThat(event.getPullRequest().getDestination().getRepository().getFullName()).isEqualTo("cloudbeers/temp");
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getDisplayName()).isEqualTo("cloudbeers");
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getUsername()).isEqualTo("cloudbeers");
        assertThat(event.getPullRequest().getDestination().getRepository().getRepositoryName()).isEqualTo("temp");
        assertThat(event.getPullRequest().getDestination().getRepository().isPrivate()).isTrue();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self").get(0).getHref())
                .isEqualTo("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp");
        assertThat(event.getPullRequest().getDestination().getBranch()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getBranch().getName()).isEqualTo("main");
        assertThat(event.getPullRequest().getDestination().getBranch().getRawNode())
                .containsAnyOf("5449b752db4f", "5449b752db4fa7ca0e2329d7f70122e2a82856cc");
        assertThat(event.getPullRequest().getDestination().getCommit()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getCommit().getHash())
                .containsAnyOf("5449b752db4f", "5449b752db4fa7ca0e2329d7f70122e2a82856cc");

        assertThat(event.getPullRequest().getSource()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getScm()).isEqualTo("git");
        assertThat(event.getPullRequest().getSource().getRepository().getFullName()).isEqualTo("stephenc/temp-fork");
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getDisplayName()).isEqualTo("Stephen Connolly");
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getUsername()).isEqualTo("stephenc");
        assertThat(event.getPullRequest().getSource().getRepository().getRepositoryName()).isEqualTo("temp-fork");
        assertThat(event.getPullRequest().getSource().getRepository().isPrivate()).isTrue();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self").get(0).getHref())
                .isEqualTo("https://api.bitbucket.org/2.0/repositories/stephenc/temp-fork");

        assertThat(event.getPullRequest().getSource().getBranch()).isNotNull();
        assertThat(event.getPullRequest().getSource().getBranch().getName()).isEqualTo("main");
        assertThat(event.getPullRequest().getSource().getBranch().getRawNode())
                .containsAnyOf("63e3d18dca4c", "63e3d18dca4c61e6b9e31eb6036802c7730fa2b3");
        assertThat(event.getPullRequest().getSource().getCommit()).isNotNull();
        assertThat(event.getPullRequest().getSource().getCommit().getHash())
                .containsAnyOf("63e3d18dca4c", "63e3d18dca4c61e6b9e31eb6036802c7730fa2b3");
    }

    @Test
    void rejectedPayload() throws Exception {
        BitbucketPullRequestEvent event = BitbucketCloudWebhookPayload.pullRequestEventFromPayload(payload);
        assertThat(event.getRepository()).isNotNull();
        assertThat(event.getRepository().getScm()).isEqualTo("git");
        assertThat(event.getRepository().getFullName()).isEqualTo("cloudbeers/temp");
        assertThat(event.getRepository().getOwner().getDisplayName()).isEqualTo("cloudbeers");
        assertThat(event.getRepository().getOwner().getUsername()).isEqualTo("cloudbeers");
        assertThat(event.getRepository().getRepositoryName()).isEqualTo("temp");
        assertThat(event.getRepository().isPrivate()).isTrue();
        assertThat(event.getRepository().getLinks()).isNotNull();
        assertThat(event.getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getRepository().getLinks().get("self").get(0).getHref())
            .isEqualTo("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp");

        assertThat(event.getPullRequest()).isNotNull();
        assertThat(event.getPullRequest().getTitle()).isEqualTo("Forking for PR");
        assertThat(event.getPullRequest().getAuthorIdentifier()).isEqualTo("123456:dead-beef");
        assertThat(event.getPullRequest().getAuthorLogin()).isEqualTo("Stephen Connolly");
        assertThat(event.getPullRequest().getLink())
            .isEqualTo("https://bitbucket.org/cloudbeers/temp/pull-requests/3");

        assertThat(event.getPullRequest().getDestination()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getScm()).isEqualTo("git");
        assertThat(event.getPullRequest().getDestination().getRepository().getFullName()).isEqualTo("cloudbeers/temp");
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getDisplayName()).isEqualTo("cloudbeers");
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getUsername()).isEqualTo("cloudbeers");
        assertThat(event.getPullRequest().getDestination().getRepository().getRepositoryName()).isEqualTo("temp");
        assertThat(event.getPullRequest().getDestination().getRepository().isPrivate()).isTrue();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self").get(0).getHref())
            .isEqualTo("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp");
        assertThat(event.getPullRequest().getDestination().getBranch()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getBranch().getName()).isEqualTo("main");
        assertThat(event.getPullRequest().getDestination().getBranch().getRawNode())
            .containsAnyOf("5449b752db4f", "5449b752db4fa7ca0e2329d7f70122e2a82856cc");
        assertThat(event.getPullRequest().getDestination().getCommit()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getCommit().getHash())
            .containsAnyOf("5449b752db4f", "5449b752db4fa7ca0e2329d7f70122e2a82856cc");

        assertThat(event.getPullRequest().getSource()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getScm()).isEqualTo("git");
        assertThat(event.getPullRequest().getSource().getRepository().getFullName()).isEqualTo("stephenc/temp-fork");
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getDisplayName())
            .isEqualTo("Stephen Connolly");
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getUsername()).isEqualTo("stephenc");
        assertThat(event.getPullRequest().getSource().getRepository().getRepositoryName()).isEqualTo("temp-fork");
        assertThat(event.getPullRequest().getSource().getRepository().isPrivate()).isTrue();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self").get(0).getHref())
            .isEqualTo("https://api.bitbucket.org/2.0/repositories/stephenc/temp-fork");

        assertThat(event.getPullRequest().getSource().getBranch()).isNotNull();
        assertThat(event.getPullRequest().getSource().getBranch().getName()).isEqualTo("main");
        assertThat(event.getPullRequest().getSource().getBranch().getRawNode())
            .containsAnyOf("63e3d18dca4c", "63e3d18dca4c61e6b9e31eb6036802c7730fa2b3");
        assertThat(event.getPullRequest().getSource().getCommit()).isNotNull();
        assertThat(event.getPullRequest().getSource().getCommit().getHash())
            .containsAnyOf("63e3d18dca4c", "63e3d18dca4c61e6b9e31eb6036802c7730fa2b3");
    }

    @Test
    void fulfilledPayload() throws Exception {
        BitbucketPullRequestEvent event = BitbucketCloudWebhookPayload.pullRequestEventFromPayload(payload);
        assertThat(event.getRepository()).isNotNull();
        assertThat(event.getRepository().getScm()).isEqualTo("git");
        assertThat(event.getRepository().getFullName()).isEqualTo("cloudbeers/temp");
        assertThat(event.getRepository().getOwner().getDisplayName()).isEqualTo("cloudbeers");
        assertThat(event.getRepository().getOwner().getUsername()).isEqualTo("cloudbeers");
        assertThat(event.getRepository().getRepositoryName()).isEqualTo("temp");
        assertThat(event.getRepository().isPrivate()).isTrue();
        assertThat(event.getRepository().getLinks()).isNotNull();
        assertThat(event.getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getRepository().getLinks().get("self").get(0).getHref())
                .isEqualTo("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp");

        assertThat(event.getPullRequest()).isNotNull();
        assertThat(event.getPullRequest().getTitle()).isEqualTo("README.md edited online with Bitbucket");
        assertThat(event.getPullRequest().getAuthorIdentifier()).isEqualTo("123456:dead-beef");
        assertThat(event.getPullRequest().getAuthorLogin()).isEqualTo("Stephen Connolly");
        assertThat(event.getPullRequest().getLink())
                .isEqualTo("https://bitbucket.org/cloudbeers/temp/pull-requests/2");

        assertThat(event.getPullRequest().getDestination()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getScm()).isEqualTo("git");
        assertThat(event.getPullRequest().getDestination().getRepository().getFullName()).isEqualTo("cloudbeers/temp");
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getDisplayName()).isEqualTo("cloudbeers");
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getUsername()).isEqualTo("cloudbeers");
        assertThat(event.getPullRequest().getDestination().getRepository().getRepositoryName()).isEqualTo("temp");
        assertThat(event.getPullRequest().getDestination().getRepository().isPrivate()).isTrue();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self").get(0).getHref())
                .isEqualTo("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp");
        assertThat(event.getPullRequest().getDestination().getBranch()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getBranch().getName()).isEqualTo("main");
        assertThat(event.getPullRequest().getDestination().getBranch().getRawNode())
                .containsAnyOf("f612156eff2c", "f612156eff2c958f52f8e6e20c71f396aeaeaff4");
        assertThat(event.getPullRequest().getDestination().getCommit()).isNotNull();
        assertThat(event.getPullRequest().getDestination().getCommit().getHash())
                .containsAnyOf("f612156eff2c", "f612156eff2c958f52f8e6e20c71f396aeaeaff4");

        assertThat(event.getPullRequest().getSource()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getScm()).isEqualTo("git");
        assertThat(event.getPullRequest().getSource().getRepository().getFullName()).isEqualTo("cloudbeers/temp");
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getDisplayName()).isEqualTo("cloudbeers");
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getUsername()).isEqualTo("cloudbeers");
        assertThat(event.getPullRequest().getSource().getRepository().getRepositoryName()).isEqualTo("temp");
        assertThat(event.getPullRequest().getSource().getRepository().isPrivate()).isTrue();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks()).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self")).isNotNull();
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self").get(0).getHref())
            .isEqualTo("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp");

        assertThat(event.getPullRequest().getSource().getBranch()).isNotNull();
        assertThat(event.getPullRequest().getSource().getBranch().getName()).isEqualTo("foo");
        assertThat(event.getPullRequest().getSource().getBranch().getRawNode())
                .containsAnyOf("a72355f35fde", "a72355f35fde2ad4f5724a279b970ef7b6729131");
        assertThat(event.getPullRequest().getSource().getCommit()).isNotNull();
        assertThat(event.getPullRequest().getSource().getCommit().getHash())
                .containsAnyOf("a72355f35fde", "a72355f35fde2ad4f5724a279b970ef7b6729131");
    }

}
