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

import com.cloudbees.jenkins.plugins.bitbucket.api.hook.BitbucketHookProcessor;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.test.util.HookProcessorTestUtil;
import com.cloudbees.jenkins.plugins.bitbucket.test.util.MockRequest;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.collections4.MultiValuedMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WithJenkins
class BitbucketSCMSourcePushHookReceiverTest {

    private static JenkinsRule j;

    @BeforeAll
    static void init(JenkinsRule rule) {
        j = rule;
    }

    private StaplerRequest2 req;
    private Map<String, String> headers;
    private BitbucketSCMSourcePushHookReceiver sut;

    @BeforeEach
    void setup() throws Exception {
        req = mock(StaplerRequest2.class);
        headers = new HashMap<>();
        when(req.getHeader(anyString())).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                return headers.get(invocation.getArgument(0));
            }
        });
        when(req.getHeaderNames()).thenAnswer(new Answer<Enumeration<String>>() {
            @Override
            public Enumeration<String> answer(InvocationOnMock invocation) throws Throwable {
                return Collections.enumeration(headers.keySet());
            }
        });
        when(req.getInputStream()).thenReturn(new MockRequest("{}"));
    }

    private void mockRequest() {
        when(req.getRemoteHost()).thenReturn("https://bitbucket.org");
        when(req.getRemoteAddr()).thenReturn("185.166.143.48");
        when(req.getScheme()).thenReturn("https");
        when(req.getServerName()).thenReturn("jenkins.example.com");
        when(req.getLocalPort()).thenReturn(80);
        when(req.getRequestURI()).thenReturn("/bitbucket-scmsource-hook/notify");
    }

    @Test
    void test_roundtrip() throws Exception {
        BitbucketCloudEndpoint endpoint = new BitbucketCloudEndpoint(false, 0, 0, false, null, true, "hmac");
        endpoint.setBitbucketJenkinsRootUrl("https://jenkins.example.com");
        BitbucketEndpointConfiguration.get().updateEndpoint(endpoint);

        try {
            BitbucketHookProcessor hookProcessor = mock(BitbucketHookProcessor.class);
            sut = new BitbucketSCMSourcePushHookReceiver() {
                @Override
                Stream<BitbucketHookProcessor> getHookProcessors() {
                    return Stream.of(hookProcessor);
                }
            };
            mockRequest();
            headers.putAll(HookProcessorTestUtil.getCloudHeaders());
            when(hookProcessor.canHandle(any(), any())).thenReturn(true);
            when(hookProcessor.getServerURL(any(), any())).thenReturn(endpoint.getServerURL());
            when(hookProcessor.getEventType(any(), any())).thenReturn("event:X");

            sut.doNotify(req);
            ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
            ArgumentCaptor<MultiValuedMap<String, String>> parametersCaptor = ArgumentCaptor.forClass(MultiValuedMap.class);

            verify(hookProcessor).canHandle(headersCaptor.capture(),  parametersCaptor.capture());
            assertThat(headersCaptor.getValue()).containsAllEntriesOf(headers);
            assertThat(parametersCaptor.getValue().entries()).isEmpty();

            verify(hookProcessor).getServerURL(headersCaptor.capture(), parametersCaptor.capture());
            assertThat(headersCaptor.getValue()).containsAllEntriesOf(headers);
            assertThat(parametersCaptor.getValue().entries()).isEmpty();

            verify(hookProcessor).getEventType(headersCaptor.capture(),  parametersCaptor.capture());
            assertThat(headersCaptor.getValue()).containsAllEntriesOf(headers);
            assertThat(parametersCaptor.getValue().entries()).isEmpty();

            verify(hookProcessor).verifySignature(headersCaptor.capture(), eq("{}"), eq(endpoint));
            verify(hookProcessor).process(
                    eq("event:X"),
                    eq("{}"),
                    eq("https://bitbucket.org/185.166.143.48 ⇒ https://jenkins.example.com:80/bitbucket-scmsource-hook/notify"),
                    eq(endpoint));
        } finally {
            BitbucketEndpointConfiguration.get().removeEndpoint(endpoint.getServerURL());
        }
    }

    @Test
    void stop_process_when_multiple_processors_canHandle_incoming_webhook() throws Exception {
        BitbucketCloudEndpoint endpoint = new BitbucketCloudEndpoint(false, 0, 0, false, null, true, "hmac");
        endpoint.setBitbucketJenkinsRootUrl("https://jenkins.example.com");
        BitbucketEndpointConfiguration.get().updateEndpoint(endpoint);

        try {
            BitbucketHookProcessor hookProcessor = mock(BitbucketHookProcessor.class);
            when(hookProcessor.canHandle(any(), any())).thenReturn(true);
            sut = new BitbucketSCMSourcePushHookReceiver() {
                @Override
                Stream<BitbucketHookProcessor> getHookProcessors() {
                    BitbucketHookProcessor otherHookProcessor = mock(BitbucketHookProcessor.class);
                    when(otherHookProcessor.canHandle(any(), any())).thenReturn(true);
                    return Stream.of(hookProcessor, otherHookProcessor);
                }
            };
            mockRequest();
            headers.putAll(HookProcessorTestUtil.getCloudHeaders());

            HttpResponse response = sut.doNotify(req);
            assertThat(response).asString().contains("More processors found that handle the incoming Bitbucket hook.");
            verify(hookProcessor, never()).getServerURL(any(), any());
        } finally {
            BitbucketEndpointConfiguration.get().removeEndpoint(endpoint.getServerURL());
        }
    }

}
