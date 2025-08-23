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

import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.HookEventType;
import com.cloudbees.jenkins.plugins.bitbucket.test.util.HookProcessorTestUtil;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jenkins.scm.api.SCMHeadEvent;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WithJenkins
class ServerPingWebhookProcessorTest {

    private static final String SERVER_URL = "http://localhost:7990";
    private ServerPingWebhookProcessor sut;
    private SCMHeadEvent<?> scmEvent;
    private BitbucketEndpoint endpoint;

    @BeforeEach
    void setup() {
        sut = new ServerPingWebhookProcessor() {
            @Override
            public void notifyEvent(SCMHeadEvent<?> event, int delaySeconds) {
                ServerPingWebhookProcessorTest.this.scmEvent = event;
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

        headers.put("X-Event-Key", "diagnostics:ping");
        assertThat(sut.canHandle(headers, parameters)).isTrue();
    }

    @Test
    void test_ping() throws Exception {
        sut.process(HookEventType.SERVER_PING.getKey(), loadResource("ping.json"), Collections.emptyMap(), endpoint);

        assertThat(scmEvent).isNull();
    }

    private String loadResource(String resource) throws IOException {
        try (InputStream stream = this.getClass().getResourceAsStream(resource)) {
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        }
    }
}
