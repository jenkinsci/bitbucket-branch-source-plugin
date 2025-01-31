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
package com.cloudbees.jenkins.plugins.bitbucket.hooks;

import hudson.scm.SCM;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import jenkins.scm.api.SCMEvent;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PushHookProcessorTest {

    private PushHookProcessor sut;

    @BeforeEach
    void setup() {
        sut = spy(new PushHookProcessor());
    }

    @Test
    void test_push_server() throws Exception {
        sut.process(HookEventType.SERVER_REFS_CHANGED, loadResource("server/pushPayload.json"), BitbucketType.SERVER, "origin");

        ArgumentCaptor<PushEvent> eventCaptor = ArgumentCaptor.forClass(PushEvent.class);
        verify(sut).notifyEvent(eventCaptor.capture(), anyInt());
        PushEvent event = eventCaptor.getValue();
        assertThat(event).isNotNull();
        assertThat(event.getSourceName()).isEqualTo("test-repos");
        assertThat(event.getType()).isEqualTo(SCMEvent.Type.UPDATED);
        assertThat(event.isMatch(mock(SCM.class))).isFalse();
    }

    @Test
    @Issue("JENKINS-55927")
    void test_push_server_empty_changes() throws Exception {
        sut.process(HookEventType.SERVER_REFS_CHANGED, loadResource("server/emptyPayload.json"), BitbucketType.SERVER, "origin");

        ArgumentCaptor<PushEvent> eventCaptor = ArgumentCaptor.forClass(PushEvent.class);
        verify(sut, times(0)).notifyEvent(eventCaptor.capture(), anyInt());
    }

    private String loadResource(String resource) throws IOException {
        try (InputStream stream = this.getClass().getResourceAsStream(resource)) {
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        }
    }
}
