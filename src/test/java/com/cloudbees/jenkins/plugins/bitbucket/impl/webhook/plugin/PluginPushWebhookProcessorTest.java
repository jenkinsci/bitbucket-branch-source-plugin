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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.plugin;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMNavigator;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.BranchSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.HookEventType;
import com.cloudbees.jenkins.plugins.bitbucket.test.util.HookProcessorTestUtil;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jenkins.plugins.git.AbstractGitSCMSource.SCMRevisionImpl;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMEvent.Type;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SuppressWarnings("deprecation")
class PluginPushWebhookProcessorTest {

    private static final String SERVER_URL = "http://localhost:7990";
    private PluginPushWebhookProcessor sut;
    private PluginPushEvent scmEvent;

    @BeforeEach
    void setup() {
        sut = new PluginPushWebhookProcessor() {
            @Override
            public void notifyEvent(SCMHeadEvent<?> event, int delaySeconds) {
                PluginPushWebhookProcessorTest.this.scmEvent = (PluginPushEvent) event;
            }
        };
    }

    @Test
    void test_getServerURL_return_always_cloud_URL() throws Exception {
        Map<String, String> headers = new CaseInsensitiveMap<>();
        MultiValuedMap<String, String> parameters = new ArrayListValuedHashMap<>();
        parameters.put("server_url", SERVER_URL);

        assertThat(sut.getServerURL(headers, parameters)).isEqualTo(SERVER_URL);
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

        Map<String, String> headers = HookProcessorTestUtil.getPluginHeaders();
        assertThat(sut.canHandle(headers, parameters)).isFalse();

        headers.put("X-Event-Key", "pr:opened");
        assertThat(sut.canHandle(headers, parameters)).isFalse();

        headers.put("X-Event-Key", "pr:merged");
        assertThat(sut.canHandle(headers, parameters)).isFalse();

        headers.put("X-Event-Key", "repo:push");
        assertThat(sut.canHandle(headers, parameters)).isTrue();

        headers.put("X-Event-Key", "pullrequest:updated");
        assertThat(sut.canHandle(headers, parameters)).isFalse();
    }

    @Test
    void test_push_server_UPDATE_2() throws Exception {
        sut.process(HookEventType.PUSH.getKey(), loadResource("commit_update2.json"), Collections.emptyMap(), mock(BitbucketEndpoint.class));

        assertThat(scmEvent).isNotNull();
        assertThat(scmEvent.getSourceName()).isEqualTo("rep_1");
        assertThat(scmEvent.getType()).isEqualTo(SCMEvent.Type.UPDATED);

        BitbucketSCMSource scmSource = new BitbucketSCMSource("PROJECT_1", "rep_1");
        scmSource.setServerUrl(SERVER_URL);
        Map<SCMHead, SCMRevision> heads = scmEvent.heads(scmSource);
        assertThat(heads)
            .containsKey(new BranchSCMHead("master"))
            .containsValue(new SCMRevisionImpl(new BranchSCMHead("master"), "500cf91e7b4b7d9f995cdb6e81cb5538216ac02e"));
    }

    @Test
    void test_push_server_UPDATE() throws Exception {
        sut.process(HookEventType.PUSH.getKey(), loadResource("commit_update.json"), Collections.emptyMap(), mock(BitbucketEndpoint.class));

        assertThat(scmEvent).isNotNull();
        assertThat(scmEvent.getSourceName()).isEqualTo("rep_1");
        assertThat(scmEvent.getType()).isEqualTo(SCMEvent.Type.UPDATED);

        BitbucketSCMSource scmSource = new BitbucketSCMSource("PROJEct_1", "rep_1");
        scmSource.setServerUrl(SERVER_URL);
        Map<SCMHead, SCMRevision> heads = scmEvent.heads(scmSource);
        assertThat(heads)
            .containsKey(new BranchSCMHead("test-webhook"))
            .containsValue(new SCMRevisionImpl(new BranchSCMHead("test-webhook"), "c0158b3e6c8cecf3bddc39d20957a98660cd23fd"));
    }

    @Test
    void test_push_server_CREATED() throws Exception {
        sut.process(HookEventType.PUSH.getKey(), loadResource("branch_created.json"), Collections.emptyMap(), mock(BitbucketEndpoint.class));

        assertThat(scmEvent).isNotNull();
        assertThat(scmEvent.getSourceName()).isEqualTo("rep_1");
        assertThat(scmEvent.getType()).isEqualTo(SCMEvent.Type.CREATED);

        BitbucketSCMSource scmSource = new BitbucketSCMSource("pROJECT_1", "rep_1");
        scmSource.setServerUrl(SERVER_URL);
        Map<SCMHead, SCMRevision> heads = scmEvent.heads(scmSource);
        assertThat(heads)
            .containsKey(new BranchSCMHead("test-webhook"))
            .containsValue(new SCMRevisionImpl(new BranchSCMHead("test-webhook"), "417b2f673581ee6000e260a5fa65e62b56c7a3cd"));
    }

    @Test
    void test_push_server_REMOVED() throws Exception {
        sut.process(HookEventType.PUSH.getKey(), loadResource("branch_deleted.json"), Collections.emptyMap(), mock(BitbucketEndpoint.class));

        assertThat(scmEvent).isNotNull();
        assertThat(scmEvent.getSourceName()).isEqualTo("rep_1");
        assertThat(scmEvent.getType()).isEqualTo(SCMEvent.Type.REMOVED);

        BitbucketSCMSource scmSource = new BitbucketSCMSource("pROJECT_1", "rep_1");
        scmSource.setServerUrl(SERVER_URL);
        Map<SCMHead, SCMRevision> heads = scmEvent.heads(scmSource);
        assertThat(heads).containsKey(new BranchSCMHead("test-webhook"));
        assertThat(heads.values()).containsNull();
    }

    @Test
    void test_PushEvent_match_SCMNavigator() throws Exception {
        sut.process(HookEventType.PUSH.getKey(), loadResource("branch_created.json"), Collections.emptyMap(), mock(BitbucketEndpoint.class));

        assertThat(scmEvent.getType()).isEqualTo(Type.CREATED);
        // discard any scm navigator than bitbucket
        assertThat(scmEvent.isMatch(mock(SCMNavigator.class))).isFalse();

        BitbucketSCMNavigator scmNavigator = new BitbucketSCMNavigator("PROJECT_1");
        assertThat(scmEvent.isMatch(scmNavigator)).isFalse();
        // match only if projectKey and serverURL matches
        scmNavigator.setServerUrl(SERVER_URL);
        assertThat(scmEvent.isMatch(scmNavigator)).isTrue();
        // if set must match the project of repository from which the hook is generated
        scmNavigator.setProjectKey("PROJECT_1");
        assertThat(scmEvent.isMatch(scmNavigator)).isTrue();
        // project key is case sensitive
        scmNavigator.setProjectKey("project_1");
        assertThat(scmEvent.isMatch(scmNavigator)).isFalse();

        // workspace/owner is case insensitive
        scmNavigator = new BitbucketSCMNavigator("project_1");
        scmNavigator.setServerUrl(SERVER_URL);
        assertThat(scmEvent.isMatch(scmNavigator)).isTrue();
    }

    @WithJenkins
    @Test
    void test_PushEvent_match_SCMSource(JenkinsRule r) throws Exception {
        sut.process(HookEventType.PUSH.getKey(), loadResource("branch_created.json"), Collections.emptyMap(), mock(BitbucketEndpoint.class));

        // discard any scm navigator than bitbucket
        assertThat(scmEvent.isMatch(mock(SCMSource.class))).isFalse();

        BitbucketSCMSource scmSource = new BitbucketSCMSource("PROJECT_1", "rep_1");
        scmSource.setServerUrl(SERVER_URL);
        assertThat(scmEvent.isMatch(scmSource)).isTrue();

        // workspace/owner is case insensitive
        scmSource = new BitbucketSCMSource("project_1", "rep_1");
        scmSource.setServerUrl(SERVER_URL);
        assertThat(scmEvent.isMatch(scmSource)).isTrue();
    }

    @Test
    @Issue("JENKINS-55927")
    void test_push_server_empty_changes() throws Exception {
        sut.process(HookEventType.SERVER_REFS_CHANGED.getKey(), loadResource("emptyPayload.json"), Collections.emptyMap(), mock(BitbucketEndpoint.class));
        assertThat(scmEvent).isNull();
    }

    private String loadResource(String resource) throws IOException {
        try (InputStream stream = this.getClass().getResourceAsStream(resource)) {
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        }
    }
}
