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
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class BitbucketCloudPullRequestEventTest {
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
    public void createPayloadOrigin() throws Exception {
        BitbucketPullRequestEvent event = BitbucketCloudWebhookPayload.pullRequestEventFromPayload(payload);
        assertThat(event.getRepository(), notNullValue());
        assertThat(event.getRepository().getScm(), is("git"));
        assertThat(event.getRepository().getFullName(), is("cloudbeers/temp"));
        assertThat(event.getRepository().getOwner().getDisplayName(), is("cloudbeers"));
        assertThat(event.getRepository().getOwner().getUsername(), is("cloudbeers"));
        assertThat(event.getRepository().getRepositoryName(), is("temp"));
        assertThat(event.getRepository().isPrivate(), is(true));
        assertThat(event.getRepository().getLinks(), notNullValue());
        assertThat(event.getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getRepository().getLinks().get("self").get(0).getHref(),
                is("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp"));

        assertThat(event.getPullRequest(), notNullValue());
        assertThat(event.getPullRequest().getTitle(), is("README.md edited online with Bitbucket"));
        assertThat(event.getPullRequest().getAuthorIdentifier(), is("123456:dead-beef"));
        assertThat(event.getPullRequest().getAuthorLogin(), is("Stephen Connolly"));
        assertThat(event.getPullRequest().getLink(),
                is("https://bitbucket.org/cloudbeers/temp/pull-requests/1"));

        assertThat(event.getPullRequest().getDestination(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getScm(), is("git"));
        assertThat(event.getPullRequest().getDestination().getRepository().getFullName(), is("cloudbeers/temp"));
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getDisplayName(),
                is("cloudbeers"));
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getUsername(), is("cloudbeers"));
        assertThat(event.getPullRequest().getDestination().getRepository().getRepositoryName(), is("temp"));
        assertThat(event.getPullRequest().getDestination().getRepository().isPrivate(), is(true));
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self").get(0).getHref(),
                is("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp"));
        assertThat(event.getPullRequest().getDestination().getBranch(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getBranch().getName(), is("main"));
        assertThat(event.getPullRequest().getDestination().getBranch().getRawNode(),
                anyOf(is("f612156eff2c"), is("f612156eff2c958f52f8e6e20c71f396aeaeaff4")));
        assertThat(event.getPullRequest().getDestination().getCommit(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getCommit().getHash(),
                anyOf(is("f612156eff2c"), is("f612156eff2c958f52f8e6e20c71f396aeaeaff4")));

        assertThat(event.getPullRequest().getSource(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getScm(), is("git"));
        assertThat(event.getPullRequest().getSource().getRepository().getFullName(), is("cloudbeers/temp"));
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getDisplayName(), is("cloudbeers"));
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getUsername(), is("cloudbeers"));
        assertThat(event.getPullRequest().getSource().getRepository().getRepositoryName(), is("temp"));
        assertThat(event.getPullRequest().getSource().getRepository().isPrivate(), is(true));
        assertThat(event.getPullRequest().getSource().getRepository().getLinks(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self").get(0).getHref(),
                is("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp"));

        assertThat(event.getPullRequest().getSource().getBranch(), notNullValue());
        assertThat(event.getPullRequest().getSource().getBranch().getName(), is("foo"));
        assertThat(event.getPullRequest().getSource().getBranch().getRawNode(),
                anyOf(is("a72355f35fde"), is("a72355f35fde2ad4f5724a279b970ef7b6729131")));
        assertThat(event.getPullRequest().getSource().getCommit(), notNullValue());
        assertThat(event.getPullRequest().getSource().getCommit().getHash(),
                anyOf(is("a72355f35fde"), is("a72355f35fde2ad4f5724a279b970ef7b6729131")));
    }

    @Test
    public void createPayloadFork() throws Exception {
        BitbucketPullRequestEvent event = BitbucketCloudWebhookPayload.pullRequestEventFromPayload(payload);

        assertThat(event.getRepository(), notNullValue());
        assertThat(event.getRepository().getScm(), is("git"));
        assertThat(event.getRepository().getFullName(), is("cloudbeers/temp"));
        assertThat(event.getRepository().getOwner().getDisplayName(), is("cloudbeers"));
        assertThat(event.getRepository().getOwner().getUsername(), is("cloudbeers"));
        assertThat(event.getRepository().getRepositoryName(), is("temp"));
        assertThat(event.getRepository().isPrivate(), is(true));
        assertThat(event.getRepository().getLinks(), notNullValue());
        assertThat(event.getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getRepository().getLinks().get("self").get(0).getHref(),
                is("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp"));

        assertThat(event.getPullRequest(), notNullValue());
        assertThat(event.getPullRequest().getTitle(), is("Forking for PR"));
        assertThat(event.getPullRequest().getAuthorIdentifier(), is("123456:dead-beef"));
        assertThat(event.getPullRequest().getAuthorLogin(), is("Stephen Connolly"));
        assertThat(event.getPullRequest().getLink(),
                is("https://bitbucket.org/cloudbeers/temp/pull-requests/3"));

        assertThat(event.getPullRequest().getDestination(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getScm(), is("git"));
        assertThat(event.getPullRequest().getDestination().getRepository().getFullName(), is("cloudbeers/temp"));
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getDisplayName(),
                is("cloudbeers"));
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getUsername(), is("cloudbeers"));
        assertThat(event.getPullRequest().getDestination().getRepository().getRepositoryName(), is("temp"));
        assertThat(event.getPullRequest().getDestination().getRepository().isPrivate(), is(true));
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self").get(0).getHref(),
                is("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp"));
        assertThat(event.getPullRequest().getDestination().getBranch(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getBranch().getName(), is("main"));
        assertThat(event.getPullRequest().getDestination().getBranch().getRawNode(),
                anyOf(is("1986c2284946"), is("1986c228494671574242f99b62d1a00a4bfb69a5")));
        assertThat(event.getPullRequest().getDestination().getCommit(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getCommit().getHash(),
                anyOf(is("1986c2284946"), is("1986c228494671574242f99b62d1a00a4bfb69a5")));

        assertThat(event.getPullRequest().getSource(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getScm(), is("git"));
        assertThat(event.getPullRequest().getSource().getRepository().getFullName(), is("stephenc/temp-fork"));
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getDisplayName(), is("Stephen Connolly"));
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getUsername(), is("stephenc"));
        assertThat(event.getPullRequest().getSource().getRepository().getRepositoryName(), is("temp-fork"));
        assertThat(event.getPullRequest().getSource().getRepository().isPrivate(), is(true));
        assertThat(event.getPullRequest().getSource().getRepository().getLinks(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self").get(0).getHref(),
                is("https://api.bitbucket.org/2.0/repositories/stephenc/temp-fork"));

        assertThat(event.getPullRequest().getSource().getBranch(), notNullValue());
        assertThat(event.getPullRequest().getSource().getBranch().getName(), is("main"));
        assertThat(event.getPullRequest().getSource().getBranch().getRawNode(),
                anyOf(is("1c48041a96db"), is("1c48041a96db4c98620609260c21ff5fbc9640c2")));
        assertThat(event.getPullRequest().getSource().getCommit(), notNullValue());
        assertThat(event.getPullRequest().getSource().getCommit().getHash(),
                anyOf(is("1c48041a96db"), is("1c48041a96db4c98620609260c21ff5fbc9640c2")));
    }

    @Test
    public void updatePayload_newCommit() throws Exception {
        BitbucketPullRequestEvent event = BitbucketCloudWebhookPayload.pullRequestEventFromPayload(payload);
        assertThat(event.getRepository(), notNullValue());
        assertThat(event.getRepository().getScm(), is("git"));
        assertThat(event.getRepository().getFullName(), is("cloudbeers/temp"));
        assertThat(event.getRepository().getOwner().getDisplayName(), is("cloudbeers"));
        assertThat(event.getRepository().getOwner().getUsername(), is("cloudbeers"));
        assertThat(event.getRepository().getRepositoryName(), is("temp"));
        assertThat(event.getRepository().isPrivate(), is(true));
        assertThat(event.getRepository().getLinks(), notNullValue());
        assertThat(event.getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getRepository().getLinks().get("self").get(0).getHref(),
                is("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp"));

        assertThat(event.getPullRequest(), notNullValue());
        assertThat(event.getPullRequest().getTitle(), is("Forking for PR"));
        assertThat(event.getPullRequest().getAuthorIdentifier(), is("123456:dead-beef"));
        assertThat(event.getPullRequest().getAuthorLogin(), is("Stephen Connolly"));
        assertThat(event.getPullRequest().getLink(),
                is("https://bitbucket.org/cloudbeers/temp/pull-requests/3"));

        assertThat(event.getPullRequest().getDestination(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getScm(), is("git"));
        assertThat(event.getPullRequest().getDestination().getRepository().getFullName(), is("cloudbeers/temp"));
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getDisplayName(),
                is("cloudbeers"));
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getUsername(), is("cloudbeers"));
        assertThat(event.getPullRequest().getDestination().getRepository().getRepositoryName(), is("temp"));
        assertThat(event.getPullRequest().getDestination().getRepository().isPrivate(), is(true));
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self").get(0).getHref(),
                is("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp"));
        assertThat(event.getPullRequest().getDestination().getBranch(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getBranch().getName(), is("main"));
        assertThat(event.getPullRequest().getDestination().getBranch().getRawNode(),
                anyOf(is("1986c2284946"), is("1986c228494671574242f99b62d1a00a4bfb69a5")));
        assertThat(event.getPullRequest().getDestination().getCommit(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getCommit().getHash(),
                anyOf(is("1986c2284946"), is("1986c228494671574242f99b62d1a00a4bfb69a5")));

        assertThat(event.getPullRequest().getSource(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getScm(), is("git"));
        assertThat(event.getPullRequest().getSource().getRepository().getFullName(), is("stephenc/temp-fork"));
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getDisplayName(), is("Stephen Connolly"));
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getUsername(), is("stephenc"));
        assertThat(event.getPullRequest().getSource().getRepository().getRepositoryName(), is("temp-fork"));
        assertThat(event.getPullRequest().getSource().getRepository().isPrivate(), is(true));
        assertThat(event.getPullRequest().getSource().getRepository().getLinks(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self").get(0).getHref(),
                is("https://api.bitbucket.org/2.0/repositories/stephenc/temp-fork"));

        assertThat(event.getPullRequest().getSource().getBranch(), notNullValue());
        assertThat(event.getPullRequest().getSource().getBranch().getName(), is("main"));
        assertThat(event.getPullRequest().getSource().getBranch().getRawNode(),
                anyOf(is("63e3d18dca4c"), is("63e3d18dca4c61e6b9e31eb6036802c7730fa2b3")));
        assertThat(event.getPullRequest().getSource().getCommit(), notNullValue());
        assertThat(event.getPullRequest().getSource().getCommit().getHash(),
                anyOf(is("63e3d18dca4c"), is("63e3d18dca4c61e6b9e31eb6036802c7730fa2b3")));
    }

    @Test
    public void updatePayload_newDestination() throws Exception {
        BitbucketPullRequestEvent event = BitbucketCloudWebhookPayload.pullRequestEventFromPayload(payload);
        assertThat(event.getRepository(), notNullValue());
        assertThat(event.getRepository().getScm(), is("git"));
        assertThat(event.getRepository().getFullName(), is("cloudbeers/temp"));
        assertThat(event.getRepository().getOwner().getDisplayName(), is("cloudbeers"));
        assertThat(event.getRepository().getOwner().getUsername(), is("cloudbeers"));
        assertThat(event.getRepository().getRepositoryName(), is("temp"));
        assertThat(event.getRepository().isPrivate(), is(true));
        assertThat(event.getRepository().getLinks(), notNullValue());
        assertThat(event.getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getRepository().getLinks().get("self").get(0).getHref(),
                is("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp"));

        assertThat(event.getPullRequest(), notNullValue());
        assertThat(event.getPullRequest().getTitle(), is("Forking for PR"));
        assertThat(event.getPullRequest().getAuthorIdentifier(), is("123456:dead-beef"));
        assertThat(event.getPullRequest().getAuthorLogin(), is("Stephen Connolly"));
        assertThat(event.getPullRequest().getLink(),
                is("https://bitbucket.org/cloudbeers/temp/pull-requests/3"));

        assertThat(event.getPullRequest().getDestination(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getScm(), is("git"));
        assertThat(event.getPullRequest().getDestination().getRepository().getFullName(), is("cloudbeers/temp"));
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getDisplayName(),
                is("cloudbeers"));
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getUsername(), is("cloudbeers"));
        assertThat(event.getPullRequest().getDestination().getRepository().getRepositoryName(), is("temp"));
        assertThat(event.getPullRequest().getDestination().getRepository().isPrivate(), is(true));
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self").get(0).getHref(),
                is("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp"));
        assertThat(event.getPullRequest().getDestination().getBranch(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getBranch().getName(), is("stable"));
        assertThat(event.getPullRequest().getDestination().getBranch().getRawNode(),
                anyOf(is("1986c2284946"), is("1986c228494671574242f99b62d1a00a4bfb69a5")));
        assertThat(event.getPullRequest().getDestination().getCommit(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getCommit().getHash(),
                anyOf(is("1986c2284946"), is("1986c228494671574242f99b62d1a00a4bfb69a5")));

        assertThat(event.getPullRequest().getSource(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getScm(), is("git"));
        assertThat(event.getPullRequest().getSource().getRepository().getFullName(), is("stephenc/temp-fork"));
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getDisplayName(), is("Stephen Connolly"));
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getUsername(), is("stephenc"));
        assertThat(event.getPullRequest().getSource().getRepository().getRepositoryName(), is("temp-fork"));
        assertThat(event.getPullRequest().getSource().getRepository().isPrivate(), is(true));
        assertThat(event.getPullRequest().getSource().getRepository().getLinks(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self").get(0).getHref(),
                is("https://api.bitbucket.org/2.0/repositories/stephenc/temp-fork"));

        assertThat(event.getPullRequest().getSource().getBranch(), notNullValue());
        assertThat(event.getPullRequest().getSource().getBranch().getName(), is("main"));
        assertThat(event.getPullRequest().getSource().getBranch().getRawNode(),
                anyOf(is("63e3d18dca4c"), is("63e3d18dca4c61e6b9e31eb6036802c7730fa2b3")));
        assertThat(event.getPullRequest().getSource().getCommit(), notNullValue());
        assertThat(event.getPullRequest().getSource().getCommit().getHash(),
                anyOf(is("63e3d18dca4c"), is("63e3d18dca4c61e6b9e31eb6036802c7730fa2b3")));
    }

    @Test
    public void updatePayload_newDestinationCommit() throws Exception {
        BitbucketPullRequestEvent event = BitbucketCloudWebhookPayload.pullRequestEventFromPayload(payload);
        assertThat(event.getRepository(), notNullValue());
        assertThat(event.getRepository().getScm(), is("git"));
        assertThat(event.getRepository().getFullName(), is("cloudbeers/temp"));
        assertThat(event.getRepository().getOwner().getDisplayName(), is("cloudbeers"));
        assertThat(event.getRepository().getOwner().getUsername(), is("cloudbeers"));
        assertThat(event.getRepository().getRepositoryName(), is("temp"));
        assertThat(event.getRepository().isPrivate(), is(true));
        assertThat(event.getRepository().getLinks(), notNullValue());
        assertThat(event.getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getRepository().getLinks().get("self").get(0).getHref(),
                is("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp"));

        assertThat(event.getPullRequest(), notNullValue());
        assertThat(event.getPullRequest().getTitle(), is("Forking for PR"));
        assertThat(event.getPullRequest().getAuthorIdentifier(), is("123456:dead-beef"));
        assertThat(event.getPullRequest().getAuthorLogin(), is("Stephen Connolly"));
        assertThat(event.getPullRequest().getLink(),
                is("https://bitbucket.org/cloudbeers/temp/pull-requests/3"));

        assertThat(event.getPullRequest().getDestination(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getScm(), is("git"));
        assertThat(event.getPullRequest().getDestination().getRepository().getFullName(), is("cloudbeers/temp"));
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getDisplayName(),
                is("cloudbeers"));
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getUsername(), is("cloudbeers"));
        assertThat(event.getPullRequest().getDestination().getRepository().getRepositoryName(), is("temp"));
        assertThat(event.getPullRequest().getDestination().getRepository().isPrivate(), is(true));
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self").get(0).getHref(),
                is("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp"));
        assertThat(event.getPullRequest().getDestination().getBranch(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getBranch().getName(), is("main"));
        assertThat(event.getPullRequest().getDestination().getBranch().getRawNode(),
                anyOf(is("5449b752db4f"), is("5449b752db4fa7ca0e2329d7f70122e2a82856cc")));
        assertThat(event.getPullRequest().getDestination().getCommit(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getCommit().getHash(),
                anyOf(is("5449b752db4f"), is("5449b752db4fa7ca0e2329d7f70122e2a82856cc")));

        assertThat(event.getPullRequest().getSource(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getScm(), is("git"));
        assertThat(event.getPullRequest().getSource().getRepository().getFullName(), is("stephenc/temp-fork"));
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getDisplayName(), is("Stephen Connolly"));
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getUsername(), is("stephenc"));
        assertThat(event.getPullRequest().getSource().getRepository().getRepositoryName(), is("temp-fork"));
        assertThat(event.getPullRequest().getSource().getRepository().isPrivate(), is(true));
        assertThat(event.getPullRequest().getSource().getRepository().getLinks(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self").get(0).getHref(),
                is("https://api.bitbucket.org/2.0/repositories/stephenc/temp-fork"));

        assertThat(event.getPullRequest().getSource().getBranch(), notNullValue());
        assertThat(event.getPullRequest().getSource().getBranch().getName(), is("main"));
        assertThat(event.getPullRequest().getSource().getBranch().getRawNode(),
                anyOf(is("63e3d18dca4c"), is("63e3d18dca4c61e6b9e31eb6036802c7730fa2b3")));
        assertThat(event.getPullRequest().getSource().getCommit(), notNullValue());
        assertThat(event.getPullRequest().getSource().getCommit().getHash(),
                anyOf(is("63e3d18dca4c"), is("63e3d18dca4c61e6b9e31eb6036802c7730fa2b3")));
    }

    @Test
    public void rejectedPayload() throws Exception {
        BitbucketPullRequestEvent event = BitbucketCloudWebhookPayload.pullRequestEventFromPayload(payload);
        assertThat(event.getRepository(), notNullValue());
        assertThat(event.getRepository().getScm(), is("git"));
        assertThat(event.getRepository().getFullName(), is("cloudbeers/temp"));
        assertThat(event.getRepository().getOwner().getDisplayName(), is("cloudbeers"));
        assertThat(event.getRepository().getOwner().getUsername(), is("cloudbeers"));
        assertThat(event.getRepository().getRepositoryName(), is("temp"));
        assertThat(event.getRepository().isPrivate(), is(true));
        assertThat(event.getRepository().getLinks(), notNullValue());
        assertThat(event.getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getRepository().getLinks().get("self").get(0).getHref(),
                is("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp"));

        assertThat(event.getPullRequest(), notNullValue());
        assertThat(event.getPullRequest().getTitle(), is("Forking for PR"));
        assertThat(event.getPullRequest().getAuthorIdentifier(), is("123456:dead-beef"));
        assertThat(event.getPullRequest().getAuthorLogin(), is("Stephen Connolly"));
        assertThat(event.getPullRequest().getLink(),
                is("https://bitbucket.org/cloudbeers/temp/pull-requests/3"));

        assertThat(event.getPullRequest().getDestination(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getScm(), is("git"));
        assertThat(event.getPullRequest().getDestination().getRepository().getFullName(), is("cloudbeers/temp"));
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getDisplayName(),
                is("cloudbeers"));
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getUsername(), is("cloudbeers"));
        assertThat(event.getPullRequest().getDestination().getRepository().getRepositoryName(), is("temp"));
        assertThat(event.getPullRequest().getDestination().getRepository().isPrivate(), is(true));
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self").get(0).getHref(),
                is("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp"));
        assertThat(event.getPullRequest().getDestination().getBranch(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getBranch().getName(), is("main"));
        assertThat(event.getPullRequest().getDestination().getBranch().getRawNode(),
                anyOf(is("5449b752db4f"), is("5449b752db4fa7ca0e2329d7f70122e2a82856cc")));
        assertThat(event.getPullRequest().getDestination().getCommit(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getCommit().getHash(),
                anyOf(is("5449b752db4f"), is("5449b752db4fa7ca0e2329d7f70122e2a82856cc")));

        assertThat(event.getPullRequest().getSource(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getScm(), is("git"));
        assertThat(event.getPullRequest().getSource().getRepository().getFullName(), is("stephenc/temp-fork"));
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getDisplayName(),
                is("Stephen Connolly"));
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getUsername(), is("stephenc"));
        assertThat(event.getPullRequest().getSource().getRepository().getRepositoryName(), is("temp-fork"));
        assertThat(event.getPullRequest().getSource().getRepository().isPrivate(), is(true));
        assertThat(event.getPullRequest().getSource().getRepository().getLinks(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self").get(0).getHref(),
                is("https://api.bitbucket.org/2.0/repositories/stephenc/temp-fork"));

        assertThat(event.getPullRequest().getSource().getBranch(), notNullValue());
        assertThat(event.getPullRequest().getSource().getBranch().getName(), is("main"));
        assertThat(event.getPullRequest().getSource().getBranch().getRawNode(),
                anyOf(is("63e3d18dca4c"), is("63e3d18dca4c61e6b9e31eb6036802c7730fa2b3")));
        assertThat(event.getPullRequest().getSource().getCommit(), notNullValue());
        assertThat(event.getPullRequest().getSource().getCommit().getHash(),
                anyOf(is("63e3d18dca4c"), is("63e3d18dca4c61e6b9e31eb6036802c7730fa2b3")));
    }

    @Test
    public void fulfilledPayload() throws Exception {
        BitbucketPullRequestEvent event = BitbucketCloudWebhookPayload.pullRequestEventFromPayload(payload);
        assertThat(event.getRepository(), notNullValue());
        assertThat(event.getRepository().getScm(), is("git"));
        assertThat(event.getRepository().getFullName(), is("cloudbeers/temp"));
        assertThat(event.getRepository().getOwner().getDisplayName(), is("cloudbeers"));
        assertThat(event.getRepository().getOwner().getUsername(), is("cloudbeers"));
        assertThat(event.getRepository().getRepositoryName(), is("temp"));
        assertThat(event.getRepository().isPrivate(), is(true));
        assertThat(event.getRepository().getLinks(), notNullValue());
        assertThat(event.getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getRepository().getLinks().get("self").get(0).getHref(),
                is("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp"));

        assertThat(event.getPullRequest(), notNullValue());
        assertThat(event.getPullRequest().getTitle(), is("README.md edited online with Bitbucket"));
        assertThat(event.getPullRequest().getAuthorIdentifier(), is("123456:dead-beef"));
        assertThat(event.getPullRequest().getAuthorLogin(), is("Stephen Connolly"));
        assertThat(event.getPullRequest().getLink(),
                is("https://bitbucket.org/cloudbeers/temp/pull-requests/2"));

        assertThat(event.getPullRequest().getDestination(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getScm(), is("git"));
        assertThat(event.getPullRequest().getDestination().getRepository().getFullName(), is("cloudbeers/temp"));
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getDisplayName(),
                is("cloudbeers"));
        assertThat(event.getPullRequest().getDestination().getRepository().getOwner().getUsername(), is("cloudbeers"));
        assertThat(event.getPullRequest().getDestination().getRepository().getRepositoryName(), is("temp"));
        assertThat(event.getPullRequest().getDestination().getRepository().isPrivate(), is(true));
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getPullRequest().getDestination().getRepository().getLinks().get("self").get(0).getHref(),
                is("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp"));
        assertThat(event.getPullRequest().getDestination().getBranch(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getBranch().getName(), is("main"));
        assertThat(event.getPullRequest().getDestination().getBranch().getRawNode(),
                anyOf(is("f612156eff2c"), is("f612156eff2c958f52f8e6e20c71f396aeaeaff4")));
        assertThat(event.getPullRequest().getDestination().getCommit(), notNullValue());
        assertThat(event.getPullRequest().getDestination().getCommit().getHash(),
                anyOf(is("f612156eff2c"), is("f612156eff2c958f52f8e6e20c71f396aeaeaff4")));

        assertThat(event.getPullRequest().getSource(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getScm(), is("git"));
        assertThat(event.getPullRequest().getSource().getRepository().getFullName(), is("cloudbeers/temp"));
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getDisplayName(), is("cloudbeers"));
        assertThat(event.getPullRequest().getSource().getRepository().getOwner().getUsername(), is("cloudbeers"));
        assertThat(event.getPullRequest().getSource().getRepository().getRepositoryName(), is("temp"));
        assertThat(event.getPullRequest().getSource().getRepository().isPrivate(), is(true));
        assertThat(event.getPullRequest().getSource().getRepository().getLinks(), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self"), notNullValue());
        assertThat(event.getPullRequest().getSource().getRepository().getLinks().get("self").get(0).getHref(),
                is("https://api.bitbucket.org/2.0/repositories/cloudbeers/temp"));

        assertThat(event.getPullRequest().getSource().getBranch(), notNullValue());
        assertThat(event.getPullRequest().getSource().getBranch().getName(), is("foo"));
        assertThat(event.getPullRequest().getSource().getBranch().getRawNode(),
                anyOf(is("a72355f35fde"), is("a72355f35fde2ad4f5724a279b970ef7b6729131")));
        assertThat(event.getPullRequest().getSource().getCommit(), notNullValue());
        assertThat(event.getPullRequest().getSource().getCommit().getHash(),
                anyOf(is("a72355f35fde"), is("a72355f35fde2ad4f5724a279b970ef7b6729131")));
    }

}
