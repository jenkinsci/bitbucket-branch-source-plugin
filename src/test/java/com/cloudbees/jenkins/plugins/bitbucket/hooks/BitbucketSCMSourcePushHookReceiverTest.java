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

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.stapler.StaplerRequest2;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BitbucketSCMSourcePushHookReceiverTest {

    private BitbucketSCMSourcePushHookReceiver sut;
    private StaplerRequest2 req;
    private HookProcessor hookProcessor;

    @BeforeEach
    void setup() {
        req = mock(StaplerRequest2.class);
        when(req.getRemoteHost()).thenReturn("https://bitbucket.org");
        when(req.getParameter("server_url")).thenReturn("https://bitbucket.org");
        when(req.getRemoteAddr()).thenReturn("[185.166.143.48");
        when(req.getScheme()).thenReturn("https");
        when(req.getServerName()).thenReturn("jenkins.example.com");
        when(req.getLocalPort()).thenReturn(80);
        when(req.getRequestURI()).thenReturn("/bitbucket-scmsource-hook/notify");

        sut = spy(new BitbucketSCMSourcePushHookReceiver());
        hookProcessor = mock(HookProcessor.class);
        doReturn(hookProcessor).when(sut).getHookProcessor(any(HookEventType.class));
    }

    @Test
    void test_pullrequest_created() throws Exception {
        when(req.getHeader("X-Event-Key")).thenReturn("pullrequest:created");
        when(req.getHeader("X-Hook-UUID")).thenReturn("fc2f2c82-c7de-485b-917b-550c576751d3");
        when(req.getHeader("User-Agent")).thenReturn("Bitbucket-Webhooks/2.0");
        when(req.getHeader("X-Attempt-Number")).thenReturn("1");
        when(req.getHeader("X-Request-UUID")).thenReturn("5deabc5d-2369-4e11-a86a-396de804feca");
        when(req.getHeader("Content-Type")).thenReturn("application/json");
        when(req.getHeader("X-Bitbucket-Type")).thenReturn("cloud");
        when(req.getInputStream()).thenReturn(loadResource("pullrequest_created.json"));

        sut.doNotify(req);

        verify(sut).getHookProcessor(HookEventType.PULL_REQUEST_CREATED);
        verify(hookProcessor).process(
                eq(HookEventType.PULL_REQUEST_CREATED),
                anyString(),
                eq(BitbucketType.CLOUD),
                eq("https://bitbucket.org/[185.166.143.48 ⇒ https://jenkins.example.com:80/bitbucket-scmsource-hook/notify"),
                eq("https://bitbucket.org"));
    }

    @Test
    void test_pullrequest_declined() throws Exception {
        when(req.getHeader("X-Event-Key")).thenReturn("pullrequest:rejected");
        when(req.getHeader("X-Hook-UUID")).thenReturn("fc2f2c82-c7de-485b-917b-450c576751d7");
        when(req.getHeader("User-Agent")).thenReturn("Bitbucket-Webhooks/2.0");
        when(req.getHeader("X-Attempt-Number")).thenReturn("1");
        when(req.getHeader("X-Request-UUID")).thenReturn("2b600570-77fa-4476-a091-a22b501a5542");
        when(req.getHeader("Content-Type")).thenReturn("application/json");
        when(req.getHeader("X-Bitbucket-Type")).thenReturn("cloud");
        when(req.getInputStream()).thenReturn(loadResource("pullrequest_rejected.json"));

        sut.doNotify(req);

        verify(sut).getHookProcessor(HookEventType.PULL_REQUEST_DECLINED);
        verify(hookProcessor).process(
                eq(HookEventType.PULL_REQUEST_DECLINED),
                anyString(),
                eq(BitbucketType.CLOUD),
                eq("https://bitbucket.org/[185.166.143.48 ⇒ https://jenkins.example.com:80/bitbucket-scmsource-hook/notify"),
                eq("https://bitbucket.org"));
    }

    private ServletInputStream loadResource(String resource) {
        final InputStream delegate = this.getClass().getResourceAsStream("cloud/" + resource);
        return new ServletInputStream() {

            @Override
            public int read() throws IOException {
                return delegate.read();
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public boolean isReady() {
                return !isFinished();
            }

            @Override
            public boolean isFinished() {
                try {
                    return delegate.available() == 0;
                } catch (IOException e) {
                    return false;
                }
            }
        };
    }
}
