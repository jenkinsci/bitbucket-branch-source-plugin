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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticatedClient;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudPage;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudWebhook;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.JsonParser;
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
class CloudWebhookManagerTest {

    private CloudWebhookManager sut;
    private CloudWebhookConfiguration cfg;
    @Mock
    private BitbucketAuthenticatedClient client;

    @BeforeEach
    void setup() {
        cfg = new CloudWebhookConfiguration(true, "credentialsId");
        cfg.setEndpointJenkinsRootURL("http://localhost::8090/jenkins");

        when(client.getRepositoryOwner()).thenReturn("amuniz");
        when(client.getRepositoryName()).thenReturn("test-repos");

        sut = new CloudWebhookManager();
        sut.clearCaches();
    }

    @Test
    void test_cache_on_read() throws Exception {
        cfg.setEnableCache(true);
        cfg.setWebhooksCacheDuration(1);
        sut.apply(cfg);

        when(client.get(anyString())).thenReturn(JsonParser.toString(new BitbucketCloudPage<BitbucketCloudWebhook>(0, 0, 0, null, Collections.emptyList())));

        sut.read(client);
        sut.read(client);

        verify(client).get("/2.0/repositories/amuniz/test-repos/hooks?pagelen=100");
    }

    @Test
    void test_cache_on_create() throws Exception {
        cfg.setEnableCache(true);
        cfg.setWebhooksCacheDuration(1);
        sut.apply(cfg);

        when(client.get(anyString())).thenReturn(JsonParser.toString(new BitbucketCloudPage<BitbucketCloudWebhook>(0, 0, 0, null, Collections.emptyList())));

        sut.read(client);
        sut.register(client);
        sut.read(client);

        verify(client, times(2)).get("/2.0/repositories/amuniz/test-repos/hooks?pagelen=100");
        verify(client).post(eq("/2.0/repositories/amuniz/test-repos/hooks"), anyString());
    }

    @Test
    void test_cache_on_update() throws Exception {
        cfg.setEnableCache(true);
        cfg.setWebhooksCacheDuration(1);
        sut.apply(cfg);

        BitbucketCloudWebhook payload = new BitbucketCloudWebhook();
        payload.setUrl(cfg.getEndpointJenkinsRootURL());
        when(client.get(anyString())).thenReturn(JsonParser.toString(new BitbucketCloudPage<BitbucketCloudWebhook>(0, 0, 0, null, List.of(payload))));

        sut.read(client);
        sut.register(client);
        sut.read(client);

        verify(client, times(2)).get("/2.0/repositories/amuniz/test-repos/hooks?pagelen=100");
        verify(client).put(eq("/2.0/repositories/amuniz/test-repos/hooks"), anyString());
    }
}
