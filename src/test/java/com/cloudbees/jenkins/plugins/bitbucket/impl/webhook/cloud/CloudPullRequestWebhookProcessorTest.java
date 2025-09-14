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

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMNavigator;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.HookEventType;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.test.util.HookProcessorTestUtil;
import com.cloudbees.jenkins.plugins.bitbucket.trait.OriginPullRequestDiscoveryTrait;
import hudson.scm.SCM;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jenkins.scm.api.SCMEvent.Type;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMSource;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CloudPullRequestWebhookProcessorTest {

    private CloudPullRequestWebhookProcessor sut;
    private CloudPREvent scmEvent;

    @BeforeEach
    void setup() {
        sut = new CloudPullRequestWebhookProcessor() {
            @Override
            public void notifyEvent(SCMHeadEvent<?> event, int delaySeconds) {
                CloudPullRequestWebhookProcessorTest.this.scmEvent = (CloudPREvent) event;
            }
        };
    }

    @Test
    void test_getServerURL_return_always_cloud_URL() throws Exception {
        Map<String, String> headers = new CaseInsensitiveMap<>();
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
        assertThat(sut.canHandle(headers, parameters)).isTrue();

        headers.put("X-Event-Key", "pullrequest:rejected");
        assertThat(sut.canHandle(headers, parameters)).isTrue();

        headers.put("X-Event-Key", "pullrequest:fulfilled");
        assertThat(sut.canHandle(headers, parameters)).isTrue();

        headers.put("X-Event-Key", "pullrequest:updated");
        assertThat(sut.canHandle(headers, parameters)).isTrue();

        headers.put("X-Event-Key", "repo:push");
        assertThat(sut.canHandle(headers, parameters)).isFalse();
    }

    @Test
    void test_pullrequest_created() throws Exception {
        sut.process(HookEventType.PULL_REQUEST_CREATED.getKey(), loadResource("pullrequest_created.json"), Collections.emptyMap(), mock(BitbucketEndpoint.class));

        assertThat(scmEvent).isNotNull();
        assertThat(scmEvent.getSourceName()).isEqualTo("test-repos");
        assertThat(scmEvent.getType()).isEqualTo(Type.CREATED);
        assertThat(scmEvent.isMatch(mock(SCM.class))).isFalse();
    }

    @Test
    void test_pullrequest_rejected() throws Exception {
        sut.process(HookEventType.PULL_REQUEST_DECLINED.getKey(), loadResource("pullrequest_rejected.json"), Collections.emptyMap(), mock(BitbucketEndpoint.class));

        assertThat(scmEvent).isNotNull();
        assertThat(scmEvent.getSourceName()).isEqualTo("test-repos");
        assertThat(scmEvent.getType()).isEqualTo(Type.REMOVED);
        assertThat(scmEvent.isMatch(mock(SCM.class))).isFalse();
    }

    @Test
    void test_pullrequest_created_when_event_match_SCMNavigator() throws Exception {
        sut.process(HookEventType.PULL_REQUEST_CREATED.getKey(), loadResource("pullrequest_created.json"), Collections.emptyMap(), mock(BitbucketEndpoint.class));

        // discard any scm navigator than bitbucket
        assertThat(scmEvent.isMatch(mock(SCMNavigator.class))).isFalse();

        BitbucketSCMNavigator scmNavigator = new BitbucketSCMNavigator("amuniz");
        // cloud could not filter by ProjectKey
        assertThat(scmEvent.isMatch(scmNavigator)).isTrue();
        // if set must match the project of repository from which the hook is generated
        scmNavigator.setProjectKey("PRJKEY");
        assertThat(scmEvent.isMatch(scmNavigator)).isTrue();
        // project key is case sensitive
        scmNavigator.setProjectKey("prjkey");
        assertThat(scmEvent.isMatch(scmNavigator)).isFalse();

        // workspace/owner is case insensitive
        scmNavigator = new BitbucketSCMNavigator("AMUNIZ");
        assertThat(scmEvent.isMatch(scmNavigator)).isTrue();
    }

    @WithJenkins
    @Test
    void test_pullrequest_created_when_event_match_SCMSource(JenkinsRule r) throws Exception {
        sut.process(HookEventType.PULL_REQUEST_CREATED.getKey(), loadResource("pullrequest_created.json"), Collections.emptyMap(), mock(BitbucketEndpoint.class));

        // discard any scm navigator than bitbucket
        assertThat(scmEvent.isMatch(mock(SCMSource.class))).isFalse();

        BitbucketSCMSource scmSource = new BitbucketSCMSource("amuniz", "test-repos");
        // skip scm source that has not been configured to discover PRs
        assertThat(scmEvent.isMatch(scmSource)).isFalse();

        scmSource.setTraits(List.of(new OriginPullRequestDiscoveryTrait(2)));
        assertThat(scmEvent.isMatch(scmSource)).isTrue();

        // workspace/owner is case insensitive
        scmSource = new BitbucketSCMSource("AMUNIZ", "TEST-REPOS");
        scmSource.setTraits(List.of(new OriginPullRequestDiscoveryTrait(1)));
        assertThat(scmEvent.isMatch(scmSource)).isTrue();

        assertThat(scmEvent.getPullRequests(scmSource))
            .isNotEmpty()
            .hasSize(1);
    }

    @WithJenkins
    @Test
    void test_pullrequest_rejected_returns_empty_pullrequests_when_event_match_SCMSource(JenkinsRule r) throws Exception {
        sut.process(HookEventType.PULL_REQUEST_DECLINED.getKey(), loadResource("pullrequest_rejected.json"), Collections.emptyMap(), mock(BitbucketEndpoint.class));

        BitbucketSCMSource scmSource = new BitbucketSCMSource("aMUNIZ", "test-repos");
        scmSource.setTraits(List.of(new OriginPullRequestDiscoveryTrait(2)));
        assertThat(scmEvent.isMatch(scmSource)).isTrue();
        assertThat(scmEvent.getPullRequests(scmSource)).isEmpty();
    }

    private String loadResource(String resource) throws IOException {
        try (InputStream stream = this.getClass().getResourceAsStream(resource)) {
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        }
    }

}
