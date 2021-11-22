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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPushEvent;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerWebhookPayload;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class BitbucketServerPushEventTest {
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
        BitbucketPushEvent event = BitbucketServerWebhookPayload.pushEventFromPayload(payload);
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
        assertThat(event.getChanges().size(), is(1));
    }

    @Test
    public void legacyPayload() throws Exception {
        BitbucketPushEvent event = BitbucketServerWebhookPayload.pushEventFromPayload(payload);
        assertThat(event.getRepository(), notNullValue());
        assertThat(event.getRepository().getScm(), is("git"));
        assertThat(event.getRepository().getFullName(), is("PROJECT_1/rep_1"));
        assertThat(event.getRepository().getOwner().getDisplayName(), is("Project 1"));
        assertThat(event.getRepository().getOwner().getUsername(), is("PROJECT_1"));
        assertThat(event.getRepository().getRepositoryName(), is("rep_1"));
        assertThat(event.getRepository().isPrivate(), is(true));
        assertThat(event.getRepository().getLinks(), nullValue());
        assertThat(event.getChanges().size(), is(0));
    }
}
