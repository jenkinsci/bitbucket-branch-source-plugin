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
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.DateUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static org.assertj.core.api.Assertions.assertThat;

class BitbucketCloudPushEventTest {

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
    void createPayload() throws Exception {
        BitbucketPushEvent event = BitbucketCloudWebhookPayload.pushEventFromPayload(payload);
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
        assertThat(event.getChanges()).hasSize(1);
        BitbucketPushEvent.Change change = event.getChanges().get(0);
        assertThat(change.getOld()).isNull();
        assertThat(change.isCreated()).isTrue();
        assertThat(change.isClosed()).isFalse();
        assertThat(change.getNew()).isNotNull();
        assertThat(change.getNew().getName()).isEqualTo("main");
        assertThat(change.getNew().getType()).isEqualTo("branch");
        assertThat(change.getNew().getTarget()).isNotNull();
        assertThat(change.getNew().getTarget().getHash()).isEqualTo("501bf5b99365d1d870882254b9360c17172bda0e");
    }

    @Test
    void updatePayload() throws Exception {
        BitbucketPushEvent event = BitbucketCloudWebhookPayload.pushEventFromPayload(payload);
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
        assertThat(event.getChanges()).hasSize(1);
    }

    @Test
    void emptyPayload() throws Exception {
        BitbucketPushEvent event = BitbucketCloudWebhookPayload.pushEventFromPayload(payload);
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
        assertThat(event.getChanges()).isEmpty();
    }

    @Test
    void newTagPayload() throws Exception {
        BitbucketPushEvent event = BitbucketCloudWebhookPayload.pushEventFromPayload(payload);
        assertThat(event.getRepository()).isNotNull();
        assertThat(event.getRepository().getScm()).isEqualTo("git");
        assertThat(event.getRepository().getOwner().getDisplayName()).isEqualTo("ACME");
        assertThat(event.getRepository().getOwner().getUsername()).isEqualTo("acme");
        assertThat(event.getRepository().getRepositoryName()).isEqualTo("tds.cm.maven.plugins-java");
        assertThat(event.getRepository().isPrivate()).isTrue();
        BitbucketPushEvent.Change change = event.getChanges().get(0);
        assertThat(change.isCreated()).isTrue();
        assertThat(change.isClosed()).isFalse();
        assertThat(change.getNew()).isNotNull();
        assertThat(change.getNew().getName()).isEqualTo("test");
        assertThat(change.getNew().getType()).isEqualTo("tag");
        Date date = DateUtils.getDate(2018, 4, 27, 9, 4, 24, 0);
        assertThat(change.getNew().getDate()).isEqualTo(date);
        assertThat(change.getNew().getTarget()).isNotNull();
        assertThat(change.getNew().getTarget().getHash()).isEqualTo("fee1dcdb330d1318502f303ccd4792531c28dc8e");
    }

    @Test
    void multipleChangesPayload() throws Exception {
        BitbucketPushEvent event = BitbucketCloudWebhookPayload.pushEventFromPayload(payload);
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
        assertThat(event.getChanges()).hasSize(3);
        BitbucketPushEvent.Change change = event.getChanges().get(0);
        assertThat(change.getOld()).isNotNull();
        assertThat(change.getOld().getName()).isEqualTo("main");
        assertThat(change.getOld().getType()).isEqualTo("branch");
        assertThat(change.getOld().getTarget()).isNotNull();
        assertThat(change.getOld().getTarget().getHash()).isEqualTo("fc4d1ce2853b6f1ac0d0dbad643d17ef4a6e0be7");
        assertThat(change.isCreated()).isFalse();
        assertThat(change.isClosed()).isFalse();
        assertThat(change.getNew()).isNotNull();
        assertThat(change.getNew().getName()).isEqualTo("main");
        assertThat(change.getNew().getType()).isEqualTo("branch");
        assertThat(change.getNew().getTarget()).isNotNull();
        assertThat(change.getNew().getTarget().getHash()).isEqualTo("325d37697849f4b1fe42cb19c20134af08e03a82");
        change = event.getChanges().get(1);
        assertThat(change.getOld()).isNull();
        assertThat(change.isCreated()).isTrue();
        assertThat(change.isClosed()).isFalse();
        assertThat(change.getNew()).isNotNull();
        assertThat(change.getNew().getName()).isEqualTo("manchu");
        assertThat(change.getNew().getType()).isEqualTo("branch");
        assertThat(change.getNew().getTarget()).isNotNull();
        assertThat(change.getNew().getTarget().getHash()).isEqualTo("e22fcb49645b4586a845938afac5eb3ac1950586");
        change = event.getChanges().get(2);
        assertThat(change.getOld()).isNull();
        assertThat(change.isCreated()).isTrue();
        assertThat(change.isClosed()).isFalse();
        assertThat(change.getNew()).isNotNull();
        assertThat(change.getNew().getName()).isEqualTo("v0.1");
        assertThat(change.getNew().getType()).isEqualTo("tag");
        assertThat(change.getNew().getTarget()).isNotNull();
        assertThat(change.getNew().getTarget().getHash()).isEqualTo("1986c228494671574242f99b62d1a00a4bfb69a5");
    }
}
