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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.cloud;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketTagSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.BranchSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.HookEventType;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.test.util.HookProcessorTestUtil;
import hudson.scm.SCM;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jenkins.plugins.git.AbstractGitSCMSource.SCMRevisionImpl;
import jenkins.scm.api.SCMEvent.Type;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMRevision;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CloudPushWebhookProcessorTest {

    private CloudPushWebhookProcessor sut;
    private CloudPushEvent scmEvent;

    @BeforeEach
    void setup() {
        sut = new CloudPushWebhookProcessor() {
            @Override
            public void notifyEvent(SCMHeadEvent<?> event, int delaySeconds) {
                CloudPushWebhookProcessorTest.this.scmEvent = (CloudPushEvent) event;
            }
        };
    }

    @Test
    void test_getServerURL_return_always_cloud_URL() throws Exception {
        Map<String, String> headers = new HashMap<>();
        MultiValuedMap<String, String> parameters = new ArrayListValuedHashMap<>();
        parameters.put("server_url", "https://localhost:8080");

        assertThat(sut.getServerURL(headers, parameters)).isEqualTo(BitbucketCloudEndpoint.SERVER_URL);
    }

    @Test
    void test_reindexOnEmptyChanges_is_disable_by_default() throws Exception {
        assertThat(sut.reindexOnEmptyChanges()).isFalse();
    }

    @Test
    void test_canHandle_only_pass_specific_cloud_hook() throws Exception {
        MultiValuedMap<String, String> parameters = new ArrayListValuedHashMap<>();

        assertThat(sut.canHandle(new HashMap<>(), parameters)).isFalse();

        Map<String, String> headers = HookProcessorTestUtil.getCloudHeaders();
        assertThat(sut.canHandle(headers, parameters)).isFalse();

        headers.put("X-Event-Key", "pullrequest:created");
        assertThat(sut.canHandle(headers, parameters)).isFalse();

        headers.put("X-Event-Key", "pullrequest:rejected");
        assertThat(sut.canHandle(headers, parameters)).isFalse();

        headers.put("X-Event-Key", "pullrequest:fulfilled");
        assertThat(sut.canHandle(headers, parameters)).isFalse();

        headers.put("X-Event-Key", "pullrequest:updated");
        assertThat(sut.canHandle(headers, parameters)).isFalse();

        headers.put("X-Event-Key", "repo:push");
        assertThat(sut.canHandle(headers, parameters)).isTrue();
    }

    @Test
    void test_tag_created() throws Exception {
        sut.process(HookEventType.PUSH.getKey(), loadResource("tag_created.json"), Collections.emptyMap(), mock(BitbucketEndpoint.class));

        assertThat(scmEvent).isNotNull();
        assertThat(scmEvent.getSourceName()).isEqualTo("test-repos");
        assertThat(scmEvent.getType()).isEqualTo(Type.CREATED);
        assertThat(scmEvent.isMatch(mock(SCM.class))).isFalse();

        BitbucketSCMSource scmSource = new BitbucketSCMSource("AMUNIZ", "test-repos");
        Map<SCMHead, SCMRevision> heads = scmEvent.heads(scmSource);
        assertThat(heads.keySet())
            .first()
            .usingRecursiveComparison()
            .isEqualTo(new BitbucketTagSCMHead("simple-tag", 1738608795000L)); // verify is using last commit date
    }

    @Test
    void test_annotated_tag_created() throws Exception {
        sut.process(HookEventType.PUSH.getKey(), loadResource("annotated_tag_created.json"), Collections.emptyMap(), mock(BitbucketEndpoint.class));

        assertThat(scmEvent).isNotNull();
        assertThat(scmEvent.getSourceName()).isEqualTo("test-repos");
        assertThat(scmEvent.getType()).isEqualTo(Type.CREATED);
        assertThat(scmEvent.isMatch(mock(SCM.class))).isFalse();

        BitbucketSCMSource scmSource = new BitbucketSCMSource("AMUNIz", "test-repos");
        Map<SCMHead, SCMRevision> heads = scmEvent.heads(scmSource);
        assertThat(heads.keySet())
            .first()
            .usingRecursiveComparison()
            .isEqualTo(new BitbucketTagSCMHead("test-tag", 1738608816000L));
    }

    @Test
    void test_commmit_created() throws Exception {
        sut.process(HookEventType.PUSH.getKey(), loadResource("commit_created.json"), Collections.emptyMap(), mock(BitbucketEndpoint.class));

        assertThat(scmEvent).isNotNull();
        assertThat(scmEvent.getSourceName()).isEqualTo("test-repos");
        assertThat(scmEvent.getType()).isEqualTo(Type.UPDATED);
        assertThat(scmEvent.isMatch(mock(SCM.class))).isFalse();

        BitbucketSCMSource scmSource = new BitbucketSCMSource("aMUNIZ", "test-repos");
        Map<SCMHead, SCMRevision> heads = scmEvent.heads(scmSource);
        assertThat(heads.keySet())
            .first()
            .usingRecursiveComparison()
            .isEqualTo(new BranchSCMHead("feature/issue-819"));
        assertThat(heads.values())
            .first()
            .usingRecursiveComparison()
            .isEqualTo(new SCMRevisionImpl(new BranchSCMHead("feature/issue-819"), "5ecffa3874e96920f24a2b3c0d0038e47d5cd1a4"));
    }

    private String loadResource(String resource) throws IOException {
        try (InputStream stream = this.getClass().getResourceAsStream(resource)) {
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        }
    }

}
