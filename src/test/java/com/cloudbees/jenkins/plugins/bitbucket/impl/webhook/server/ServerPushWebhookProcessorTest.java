/*
 * The MIT License
 *
 * Copyright (c) 2025, Allan Burdajewicz
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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.server;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketTagSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.BranchSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketMockApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.HookEventType;
import com.cloudbees.jenkins.plugins.bitbucket.test.util.HookProcessorTestUtil;
import hudson.scm.SCM;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMRevision;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WithJenkins
class ServerPushWebhookProcessorTest {

    private static final String SERVER_URL = "http://localhost:7990";
    private static final String MIRROR_ID = "ABCD-1234-EFGH-5678";
    private ServerPushWebhookProcessor sut;
    private SCMHeadEvent<?> scmEvent;
    private BitbucketEndpoint endpoint;

    private static JenkinsRule rule;

    @BeforeAll
    static void init(JenkinsRule r) {
        rule = r;
    }

    @BeforeEach
    void setup() {
        sut = new ServerPushWebhookProcessor() {
            @Override
            public void notifyEvent(SCMHeadEvent<?> event, int delaySeconds) {
                ServerPushWebhookProcessorTest.this.scmEvent = event;
            }
        };
        endpoint = mock(BitbucketEndpoint.class);
        when(endpoint.getServerURL()).thenReturn(SERVER_URL);
    }

    @Test
    void test_reindexOnEmptyChanges_is_disable_by_default() throws Exception {
        assertThat(sut.reindexOnEmptyChanges()).isFalse();
    }

    @Test
    void test_canHandle_only_pass_specific_native_hook() throws Exception {
        MultiValuedMap<String, String> parameters = new ArrayListValuedHashMap<>();
        parameters.put("server_url", SERVER_URL);

        assertThat(sut.canHandle(new HashMap<>(), parameters)).isFalse();

        Map<String, String> headers = HookProcessorTestUtil.getNativeHeaders();
        assertThat(sut.canHandle(headers, parameters)).isFalse();

        headers.put("X-Event-Key", "pr:opened");
        assertThat(sut.canHandle(headers, parameters)).isFalse();

        headers.put("X-Event-Key", "pr:merged");
        assertThat(sut.canHandle(headers, parameters)).isFalse();

        headers.put("X-Event-Key", "repo:refs_changed");
        assertThat(sut.canHandle(headers, parameters)).isTrue();

        headers.put("X-Event-Key", "mirror:repo_synchronized");
        assertThat(sut.canHandle(headers, parameters)).isTrue();
    }

    @Test
    @Issue("JENKINS-55927")
    void test_mirror_sync_changes() throws Exception {
        sut.process(HookEventType.SERVER_MIRROR_REPO_SYNCHRONIZED.getKey(), loadResource("mirrorSynchronized.json"), Collections.emptyMap(), endpoint);

        ServerPushEvent event = (ServerPushEvent) scmEvent;
        assertThat(event).isNotNull();
        assertThat(event.getSourceName()).isEqualTo("test-repos");
        assertThat(event.getType()).isEqualTo(SCMEvent.Type.UPDATED);
        assertThat(event.isMatch(mock(SCM.class))).isFalse();

        BitbucketSCMSource scmSource = new BitbucketSCMSource("aMUNIZ", "test-repos");
        scmSource.setMirrorId(MIRROR_ID);
        Map<SCMHead, SCMRevision> heads = event.heads(scmSource);
        assertThat(heads.keySet())
            .first()
            .usingRecursiveComparison()
            .isEqualTo(new BranchSCMHead("main"));
    }

    @Test
    @Issue("JENKINS-75604")
    void test_annotated_tag_create_event() throws Exception {
        sut.process(HookEventType.SERVER_REFS_CHANGED.getKey(), loadResource("annotated_tag_created.json"), Collections.emptyMap(), endpoint);
        assertThat(scmEvent)
            .isInstanceOf(ServerPushEvent.class)
            .isNotNull();

        BitbucketSCMSource scmSource = new BitbucketSCMSource("amuniz", "test-repos");
        scmSource.setServerUrl(SERVER_URL);

        BitbucketMockApiFactory.add(SERVER_URL, BitbucketIntegrationClientFactory.getApiMockClient(SERVER_URL));

        Map<SCMHead, SCMRevision> result = scmEvent.heads(scmSource);
        assertThat(result.keySet())
            .hasSize(1)
            .first()
            .usingRecursiveComparison()
            .isEqualTo(new BitbucketTagSCMHead("annotated-tag", 1537538991000L));
    }

    @Test
    @Issue("JENKINS-75604")
    void test_tag_created_event() throws Exception {
        sut.process(HookEventType.SERVER_REFS_CHANGED.getKey(), loadResource("tag_created.json"), Collections.emptyMap(), endpoint);
        assertThat(scmEvent)
            .isInstanceOf(ServerPushEvent.class)
            .isNotNull();

        BitbucketSCMSource scmSource = new BitbucketSCMSource("amuniz", "test-repos");
        scmSource.setServerUrl(SERVER_URL);

        BitbucketMockApiFactory.add(SERVER_URL, BitbucketIntegrationClientFactory.getApiMockClient(SERVER_URL));

        Map<SCMHead, SCMRevision> result = scmEvent.heads(scmSource);
        assertThat(result.keySet())
            .hasSize(1)
            .first()
            .usingRecursiveComparison()
            .isEqualTo(new BitbucketTagSCMHead("simple-tag", 1537538991000L));
    }

    @Test
    @Issue("JENKINS-75604")
    void test_tag_deleted_event() throws Exception {
        sut.process(HookEventType.SERVER_REFS_CHANGED.getKey(), loadResource("tag_deleted.json"), Collections.emptyMap(), endpoint);
        assertThat(scmEvent)
            .isInstanceOf(ServerPushEvent.class)
            .isNotNull();

        BitbucketSCMSource scmSource = new BitbucketSCMSource("amuniz", "test-repos");
        scmSource.setServerUrl(SERVER_URL);

        BitbucketMockApiFactory.add(SERVER_URL, BitbucketIntegrationClientFactory.getApiMockClient(SERVER_URL));

        Map<SCMHead, SCMRevision> result = scmEvent.heads(scmSource);
        assertThat(result.keySet())
            .hasSize(1)
            .first()
            .usingRecursiveComparison()
            .isEqualTo(new BitbucketTagSCMHead("simple-tag", 1537538991000L));
    }

    @Test
    @Issue("JENKINS-55927")
    void test_mirror_sync_reflimitexceeed() throws Exception {
        sut.process(HookEventType.SERVER_MIRROR_REPO_SYNCHRONIZED.getKey(), loadResource("mirrorSynchronized_refLimitExceeded.json"), Collections.emptyMap(), endpoint);
        ServerPushEvent event = (ServerPushEvent) scmEvent;
        assertThat(event).isNull();
    }

    @Test
    void test_push() throws Exception {
        sut.process(HookEventType.SERVER_REFS_CHANGED.getKey(), loadResource("pushPayload.json"), Collections.emptyMap(), endpoint);

        ServerPushEvent event = (ServerPushEvent) scmEvent;
        assertThat(event).isNotNull();
        assertThat(event.getSourceName()).isEqualTo("test-repos");
        assertThat(event.getType()).isEqualTo(SCMEvent.Type.UPDATED);
        assertThat(event.isMatch(mock(SCM.class))).isFalse();

        BitbucketSCMSource scmSource = new BitbucketSCMSource("aMUNIZ", "test-repos");
        Map<SCMHead, SCMRevision> heads = event.heads(scmSource);
        assertThat(heads.keySet())
            .first()
            .usingRecursiveComparison()
            .isEqualTo(new BranchSCMHead("main"));
        assertThat(heads.values())
            .first()
            .usingRecursiveComparison()
            .isEqualTo(new AbstractGitSCMSource.SCMRevisionImpl(new BranchSCMHead("main"), "9fdd7b96d3f5c276d0b9e0bf38c879eb112d889a"));
    }

    @Test
    @Issue("JENKINS-55927")
    void test_push_empty_changes() throws Exception {
        sut.process(HookEventType.SERVER_REFS_CHANGED.getKey(), loadResource("emptyPayload.json"), Collections.emptyMap(), endpoint);
        ServerPushEvent event = (ServerPushEvent) scmEvent;
        assertThat(event).isNull();
    }

    private String loadResource(String resource) throws IOException {
        try (InputStream stream = this.getClass().getResourceAsStream(resource)) {
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        }
    }
}
