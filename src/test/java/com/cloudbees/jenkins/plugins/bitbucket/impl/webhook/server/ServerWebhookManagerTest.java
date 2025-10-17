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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.server;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticatedClient;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.JsonParser;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerPage;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketServerWebhook;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServerWebhookManagerTest {

    private ServerWebhookManager sut;
    private ServerWebhookConfiguration cfg;
    @Mock
    private BitbucketAuthenticatedClient client;

    @BeforeEach
    void setup() {
        cfg = new ServerWebhookConfiguration(true, "credentialsId");
        cfg.setEndpointJenkinsRootURL("http://localhost::8090/jenkins");

        when(client.getRepositoryOwner()).thenReturn("amuniz");
        when(client.getRepositoryName()).thenReturn("test-repos");

        sut = new ServerWebhookManager();
        sut.clearCaches();
    }

    @Test
    void test_cache_on_read() throws Exception {
        cfg.setEnableCache(true);
        cfg.setWebhooksCacheDuration(1);
        sut.apply(cfg);

        when(client.get(anyString())).thenReturn(JsonParser.toString(buildResponse()));

        sut.read(client);
        sut.read(client);

        verify(client).get("/rest/api/1.0/projects/amuniz/repos/test-repos/webhooks?start=0&limit=200");
    }

    private BitbucketServerPage<BitbucketServerWebhook> buildResponse() {
        BitbucketServerPage<BitbucketServerWebhook> response = new BitbucketServerPage<BitbucketServerWebhook>();
        response.setValues(Collections.emptyList());
        return response;
    }

    @Test
    void test_cache_on_create() throws Exception {
        cfg.setEnableCache(true);
        cfg.setWebhooksCacheDuration(1);
        sut.apply(cfg);

        when(client.get(anyString())).thenReturn(JsonParser.toString(buildResponse()));

        sut.read(client);
        sut.register(client);
        sut.read(client);

        verify(client, times(2)).get("/rest/api/1.0/projects/amuniz/repos/test-repos/webhooks?start=0&limit=200");
        verify(client).post(eq("/rest/api/1.0/projects/amuniz/repos/test-repos/webhooks"), anyString());
    }

    @Test
    void test_cache_on_update() throws Exception {
        cfg.setEnableCache(true);
        cfg.setWebhooksCacheDuration(1);
        sut.apply(cfg);

        BitbucketServerWebhook payload = new BitbucketServerWebhook();
        payload.setUrl(cfg.getEndpointJenkinsRootURL());
        BitbucketServerPage<BitbucketServerWebhook> response = buildResponse();
        response.setValues(List.of(payload));
        when(client.get(anyString())).thenReturn(JsonParser.toString(response));

        sut.read(client);
        sut.register(client);
        sut.read(client);

        verify(client, times(2)).get("/rest/api/1.0/projects/amuniz/repos/test-repos/webhooks?start=0&limit=200");
        verify(client).put(eq("/rest/api/1.0/projects/amuniz/repos/test-repos/webhooks"), anyString());
    }
}
