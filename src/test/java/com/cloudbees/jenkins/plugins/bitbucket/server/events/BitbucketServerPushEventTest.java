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
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static org.assertj.core.api.Assertions.assertThat;

class BitbucketServerPushEventTest {

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
        BitbucketPushEvent event = BitbucketServerWebhookPayload.pushEventFromPayload(payload);
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
        assertThat(event.getChanges()).hasSize(1);
    }
    @Test
    void legacyPayload() throws Exception {
        BitbucketPushEvent event = BitbucketServerWebhookPayload.pushEventFromPayload(payload);
        assertThat(event.getRepository()).isNotNull();
        assertThat(event.getRepository().getScm()).isEqualTo("git");
        assertThat(event.getRepository().getFullName()).isEqualTo("PROJECT_1/rep_1");
        assertThat(event.getRepository().getOwner().getDisplayName()).isEqualTo("Project 1");
        assertThat(event.getRepository().getOwner().getUsername()).isEqualTo("PROJECT_1");
        assertThat(event.getRepository().getRepositoryName()).isEqualTo("rep_1");
        assertThat(event.getRepository().isPrivate()).isTrue();
        assertThat(event.getRepository().getLinks()).isNull();
        assertThat(event.getChanges()).isEmpty();
    }
}
