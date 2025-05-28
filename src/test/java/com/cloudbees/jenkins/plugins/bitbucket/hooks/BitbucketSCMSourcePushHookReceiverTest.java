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

import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketServerEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.test.util.BitbucketTestUtil;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WithJenkins
class BitbucketSCMSourcePushHookReceiverTest {

    private static JenkinsRule j;

    @BeforeAll
    static void init(JenkinsRule rule) {
        j = rule;
    }

    private BitbucketSCMSourcePushHookReceiver sut;
    private StaplerRequest2 req;
    private HookProcessor hookProcessor;
    private String credentialsId;

    @BeforeEach
    void setup() throws Exception {
        req = mock(StaplerRequest2.class);
        when(req.getRemoteHost()).thenReturn("https://bitbucket.org");
        when(req.getParameter("server_url")).thenReturn("https://bitbucket.org");
        when(req.getRemoteAddr()).thenReturn("185.166.143.48");
        when(req.getScheme()).thenReturn("https");
        when(req.getServerName()).thenReturn("jenkins.example.com");
        when(req.getLocalPort()).thenReturn(80);
        when(req.getRequestURI()).thenReturn("/bitbucket-scmsource-hook/notify");
        when(req.getHeader("User-Agent")).thenReturn("Bitbucket-Webhooks/2.0");
        when(req.getHeader("X-Attempt-Number")).thenReturn("1");
        when(req.getHeader("Content-Type")).thenReturn("application/json");
        when(req.getHeader("X-Hook-UUID")).thenReturn(UUID.randomUUID().toString());
        when(req.getHeader("X-Request-UUID")).thenReturn(UUID.randomUUID().toString());

        hookProcessor = mock(HookProcessor.class);
        sut = new BitbucketSCMSourcePushHookReceiver() {
            @Override
            HookProcessor getHookProcessor(HookEventType type) {
                return hookProcessor;
            }
        };

        credentialsId = BitbucketTestUtil.registerHookCredentials("Gkvl$k$wyNpQAF42", j).getId();
    }

    @Test
    void test_signature_is_missing_from_cloud_payload() throws Exception {
        BitbucketCloudEndpoint endpoint = new BitbucketCloudEndpoint(false, null, BitbucketCloudEndpoint.SERVER_URL);
        endpoint.setEnableHookSignature(true);
        endpoint.setHookSignatureCredentialsId(credentialsId);
        BitbucketEndpointConfiguration.get().updateEndpoint(endpoint);

        try {
            when(req.getHeader("X-Event-Key")).thenReturn("repo:push");
            when(req.getHeader("X-Bitbucket-Type")).thenReturn("cloud");
            when(req.getInputStream()).thenReturn(loadResource("cloud/signed_payload.json"));

            /*HttpResponse response = */sut.doNotify(req);
            // really hard to verify if response contains a status 400
            verify(hookProcessor, never()).process(any(), anyString(), any(), anyString(), anyString());
        } finally {
            BitbucketEndpointConfiguration.get().removeEndpoint(endpoint.getServerUrl());
        }
    }

    @Test
    void test_signature_from_cloud() throws Exception {
        BitbucketCloudEndpoint endpoint = new BitbucketCloudEndpoint(false, null, BitbucketCloudEndpoint.SERVER_URL);
        endpoint.setEnableHookSignature(true);
        endpoint.setHookSignatureCredentialsId(credentialsId);
        BitbucketEndpointConfiguration.get().updateEndpoint(endpoint);

        try {
            when(req.getHeader("X-Event-Key")).thenReturn("repo:push");
            when(req.getHeader("X-Bitbucket-Type")).thenReturn("cloud");
            when(req.getHeader("X-Hub-Signature")).thenReturn("sha256=f205c729821c6954aff2afe72b965c34015b4baf96ea8ddc2cc44999c014a035");
            when(req.getInputStream()).thenReturn(loadResource("cloud/signed_payload.json"));

            sut.doNotify(req);
            verify(hookProcessor).process(
                    eq(HookEventType.PUSH),
                    anyString(),
                    eq(BitbucketType.CLOUD),
                    eq("https://bitbucket.org/185.166.143.48 ⇒ https://jenkins.example.com:80/bitbucket-scmsource-hook/notify"),
                    eq("https://bitbucket.org"));
        } finally {
            BitbucketEndpointConfiguration.get().removeEndpoint(endpoint.getServerUrl());
        }
    }

    @Test
    void test_bad_signature_from_cloud() throws Exception {
        BitbucketCloudEndpoint endpoint = new BitbucketCloudEndpoint(false, null, BitbucketCloudEndpoint.SERVER_URL);
        endpoint.setEnableHookSignature(true);
        endpoint.setHookSignatureCredentialsId(credentialsId);
        BitbucketEndpointConfiguration.get().updateEndpoint(endpoint);

        try {
            when(req.getHeader("X-Event-Key")).thenReturn("repo:push");
            when(req.getHeader("X-Bitbucket-Type")).thenReturn("cloud");
            when(req.getHeader("X-Hub-Signature")).thenReturn("sha256=f205c729821c6954aff2afe72b965c34015b4baf96ea8ddc2cc44999c014a036");
            when(req.getInputStream()).thenReturn(loadResource("cloud/signed_payload.json"));

            /*HttpResponse response = */sut.doNotify(req);
            // really hard to verify if response contains a status 400
            verify(hookProcessor, never()).process(any(), anyString(), any(), anyString(), anyString());
        } finally {
            BitbucketEndpointConfiguration.get().removeEndpoint(endpoint.getServerUrl());
        }
    }

    @Test
    void test_signature_from_native_server() throws Exception {
        BitbucketServerEndpoint endpoint = new BitbucketServerEndpoint("datacenter", "http://localhost:7990/bitbucket", false, null, "https://jenkins.example.com");
        endpoint.setEnableHookSignature(true);
        endpoint.setHookSignatureCredentialsId(credentialsId);
        BitbucketEndpointConfiguration.get().updateEndpoint(endpoint);

        try {
            when(req.getRemoteHost()).thenReturn("http://localhost:7990");
            when(req.getParameter("server_url")).thenReturn(endpoint.getServerUrl());
            when(req.getHeader("X-Event-Key")).thenReturn("repo:refs_changed");
            when(req.getHeader("X-Request-Id")).thenReturn("2b15f131-4d3a-436e-bc63-caa9ae92580d");
            when(req.getHeader("X-Hub-Signature")).thenReturn("sha256=4ffba9e7b58ea3d7e1a230446e8c92baea0aeec89b73f598932387254f0de13e");
            when(req.getInputStream()).thenReturn(loadResource("native/signed_payload.json"));

            sut.doNotify(req);
            // really hard to verify if response contains a status 400
            verify(hookProcessor).process(
                    eq(HookEventType.SERVER_REFS_CHANGED),
                    anyString(),
                    eq(BitbucketType.SERVER),
                    eq("http://localhost:7990/185.166.143.48 ⇒ https://jenkins.example.com:80/bitbucket-scmsource-hook/notify"),
                    eq(endpoint.getServerUrl()));

            // verify bad signature
            reset(hookProcessor);
            when(req.getHeader("X-Hub-Signature")).thenReturn("sha256=4ffba9e7b58ea3d7e1a230446e8c92baea0aeec89b73f598932387254f0de13f");
            when(req.getInputStream()).thenReturn(loadResource("native/signed_payload.json"));
            /*HttpResponse response = */ sut.doNotify(req);
            // really hard to verify if response contains a status 400
            verify(hookProcessor, never()).process(any(), anyString(), any(), anyString(), anyString());
        } finally {
            BitbucketEndpointConfiguration.get().removeEndpoint(endpoint.getServerUrl());
        }
    }

    @Test
    void test_bad_signature_from_native_server() throws Exception {
        BitbucketServerEndpoint endpoint = new BitbucketServerEndpoint("datacenter", "http://localhost:7990/bitbucket", false, null, "https://jenkins.example.com");
        endpoint.setEnableHookSignature(true);
        endpoint.setHookSignatureCredentialsId(credentialsId);
        BitbucketEndpointConfiguration.get().updateEndpoint(endpoint);

        try {
            when(req.getRemoteHost()).thenReturn("http://localhost:7990");
            when(req.getParameter("server_url")).thenReturn(endpoint.getServerUrl());
            when(req.getHeader("X-Event-Key")).thenReturn("repo:refs_changed");
            when(req.getHeader("X-Request-Id")).thenReturn("2b15f131-4d3a-436e-bc63-caa9ae92580d");
            when(req.getHeader("X-Hub-Signature")).thenReturn("sha256=4ffba9e7b58ea3d7e1a230446e8c92baea0aeec89b73f598932387254f0de13f");
            when(req.getInputStream()).thenReturn(loadResource("native/signed_payload.json"));
            /*HttpResponse response = */ sut.doNotify(req);
            // really hard to verify if response contains a status 400
            verify(hookProcessor, never()).process(any(), anyString(), any(), anyString(), anyString());
        } finally {
            BitbucketEndpointConfiguration.get().removeEndpoint(endpoint.getServerUrl());
        }
    }

    @Test
    void test_pullrequest_created() throws Exception {
        when(req.getHeader("X-Event-Key")).thenReturn("pullrequest:created");
        when(req.getHeader("X-Bitbucket-Type")).thenReturn("cloud");
        when(req.getInputStream()).thenReturn(loadResource("cloud/pullrequest_created.json"));

        sut.doNotify(req);

        verify(hookProcessor).process(
                eq(HookEventType.PULL_REQUEST_CREATED),
                anyString(),
                eq(BitbucketType.CLOUD),
                eq("https://bitbucket.org/185.166.143.48 ⇒ https://jenkins.example.com:80/bitbucket-scmsource-hook/notify"),
                eq("https://bitbucket.org"));
    }

    @Test
    void test_pullrequest_declined() throws Exception {
        when(req.getHeader("X-Event-Key")).thenReturn("pullrequest:rejected");
        when(req.getHeader("X-Bitbucket-Type")).thenReturn("cloud");
        when(req.getInputStream()).thenReturn(loadResource("cloud/pullrequest_rejected.json"));

        sut.doNotify(req);

        verify(hookProcessor).process(
                eq(HookEventType.PULL_REQUEST_DECLINED),
                anyString(),
                eq(BitbucketType.CLOUD),
                eq("https://bitbucket.org/185.166.143.48 ⇒ https://jenkins.example.com:80/bitbucket-scmsource-hook/notify"),
                eq("https://bitbucket.org"));
    }

    private ServletInputStream loadResource(String resource) {
        final InputStream delegate = this.getClass().getResourceAsStream(resource);
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
