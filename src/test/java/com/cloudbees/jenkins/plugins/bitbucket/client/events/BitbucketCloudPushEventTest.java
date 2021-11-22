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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPushEvent;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudWebhookPayload;
import com.cloudbees.jenkins.plugins.bitbucket.client.DateUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class BitbucketCloudPushEventTest {
    @Rule
    public final TestName testName = new TestName();

    private String payload;

    @Before
    public void loadPayload() throws IOException {
        try (InputStream is = getClass().getResourceAsStream(getClass().getSimpleName() + "/" + testName.getMethodName() + ".json")) {
            payload = IOUtils.toString(is, "UTF-8");
        }
    }

    @Test
    public void createPayload() throws Exception {
        BitbucketPushEvent event = BitbucketCloudWebhookPayload.pushEventFromPayload(payload);
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
        assertThat(event.getChanges().size(), is(1));
        BitbucketPushEvent.Change change = event.getChanges().get(0);
        assertThat(change.getOld(), nullValue());
        assertThat(change.isCreated(), is(true));
        assertThat(change.isClosed(), is(false));
        assertThat(change.getNew(), notNullValue());
        assertThat(change.getNew().getName(), is("main"));
        assertThat(change.getNew().getType(), is("branch"));
        assertThat(change.getNew().getTarget(), notNullValue());
        assertThat(change.getNew().getTarget().getHash(), is("501bf5b99365d1d870882254b9360c17172bda0e"));
    }

    @Test
    public void updatePayload() throws Exception {
        BitbucketPushEvent event = BitbucketCloudWebhookPayload.pushEventFromPayload(payload);
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
        assertThat(event.getChanges().size(), is(1));
    }

    @Test
    public void emptyPayload() throws Exception {
        BitbucketPushEvent event = BitbucketCloudWebhookPayload.pushEventFromPayload(payload);
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
        assertThat(event.getChanges().size(), is(0));
    }

    @Test
    public void newTagPayload() throws Exception {
        BitbucketPushEvent event = BitbucketCloudWebhookPayload.pushEventFromPayload(payload);
        assertThat(event.getRepository(), notNullValue());
        assertThat(event.getRepository().getScm(), is("git"));
        assertThat(event.getRepository().getOwner().getDisplayName(), is("ACME"));
        assertThat(event.getRepository().getOwner().getUsername(), is("acme"));
        assertThat(event.getRepository().getRepositoryName(), is("tds.cm.maven.plugins-java"));
        assertThat(event.getRepository().isPrivate(), is(true));
        BitbucketPushEvent.Change change = event.getChanges().get(0);
        assertThat(change.isCreated(), is(true));
        assertThat(change.isClosed(), is(false));
        assertThat(change.getNew(), notNullValue());
        assertThat(change.getNew().getName(), is("test"));
        assertThat(change.getNew().getType(), is("tag"));
        Date date = DateUtils.getDate(2018, 4, 27, 9, 4, 24, 0);
        assertThat(change.getNew().getDate().getTime(), is(date.getTime()));
        assertThat(change.getNew().getTarget(), notNullValue());
        assertThat(change.getNew().getTarget().getHash(), is("fee1dcdb330d1318502f303ccd4792531c28dc8e"));
    }

    @Test
    public void multipleChangesPayload() throws Exception {
        BitbucketPushEvent event = BitbucketCloudWebhookPayload.pushEventFromPayload(payload);
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
        assertThat(event.getChanges().size(), is(3));
        BitbucketPushEvent.Change change = event.getChanges().get(0);
        assertThat(change.getOld(), notNullValue());
        assertThat(change.getOld().getName(), is("main"));
        assertThat(change.getOld().getType(), is("branch"));
        assertThat(change.getOld().getTarget(), notNullValue());
        assertThat(change.getOld().getTarget().getHash(), is("fc4d1ce2853b6f1ac0d0dbad643d17ef4a6e0be7"));
        assertThat(change.isCreated(), is(false));
        assertThat(change.isClosed(), is(false));
        assertThat(change.getNew(), notNullValue());
        assertThat(change.getNew().getName(), is("main"));
        assertThat(change.getNew().getType(), is("branch"));
        assertThat(change.getNew().getTarget(), notNullValue());
        assertThat(change.getNew().getTarget().getHash(), is("325d37697849f4b1fe42cb19c20134af08e03a82"));
        change = event.getChanges().get(1);
        assertThat(change.getOld(), nullValue());
        assertThat(change.isCreated(), is(true));
        assertThat(change.isClosed(), is(false));
        assertThat(change.getNew(), notNullValue());
        assertThat(change.getNew().getName(), is("manchu"));
        assertThat(change.getNew().getType(), is("branch"));
        assertThat(change.getNew().getTarget(), notNullValue());
        assertThat(change.getNew().getTarget().getHash(), is("e22fcb49645b4586a845938afac5eb3ac1950586"));
        change = event.getChanges().get(2);
        assertThat(change.getOld(), nullValue());
        assertThat(change.isCreated(), is(true));
        assertThat(change.isClosed(), is(false));
        assertThat(change.getNew(), notNullValue());
        assertThat(change.getNew().getName(), is("v0.1"));
        assertThat(change.getNew().getType(), is("tag"));
        assertThat(change.getNew().getTarget(), notNullValue());
        assertThat(change.getNew().getTarget().getHash(), is("1986c228494671574242f99b62d1a00a4bfb69a5"));
    }
}
