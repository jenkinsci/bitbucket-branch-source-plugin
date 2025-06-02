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

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketMockApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.AbstractBitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketServerEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.JsonParser;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.NativeBitbucketServerWebhook;
import com.cloudbees.jenkins.plugins.bitbucket.test.util.BitbucketTestUtil;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.List;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceOwner;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.core5.http.HttpRequest;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WithJenkins
class WebhookAutoRegisterListenerTest {

    static JenkinsRule rule = new JenkinsRule();

    @BeforeAll
    static void init(JenkinsRule r) {
        rule = r;
    }

    private WebhookAutoRegisterListener sut;

    @BeforeEach
    void setup() {
        sut = new WebhookAutoRegisterListener();
    }

    @Timeout(60)
    @Test
    void test_register() throws Exception {
        String serverURL = "http://localhost:7990/bitbucket";
        BitbucketApi client = BitbucketIntegrationClientFactory.getClient(null, serverURL , "amuniz", "test-repos");
        BitbucketMockApiFactory.add(serverURL, client);

        StringCredentials credentials = BitbucketTestUtil.registerHookCredentials("password", rule);

        AbstractBitbucketEndpoint endpoint = new BitbucketServerEndpoint("datacenter", serverURL, true, "dummyId", true, credentials.getId());
        BitbucketEndpointConfiguration.get().updateEndpoint(endpoint);

        BitbucketSCMSource scmSource = new BitbucketSCMSource("amuniz", "test-repos") {
            @Override
            public String getEndpointJenkinsRootURL() {
                return "https://jenkins.example.com/";
            }
        };
        scmSource.setServerUrl(serverURL);
        scmSource.setOwner(getSCMSourceOwnerMock(scmSource));

        sut.onCreated(scmSource.getOwner());
        HttpRequest request = BitbucketTestUtil.waitForRequest(client, req -> {
            return "PUT".equals(req.getMethod()) && req.getPath().contains("webhooks");
        }).get();
        assertThat(request).isNotNull()
            .isInstanceOf(HttpPut.class)
            .asInstanceOf(InstanceOfAssertFactories.type(HttpPut.class))
            .satisfies(put -> {
                NativeBitbucketServerWebhook message = JsonParser.toJava(put.getEntity().getContent(), NativeBitbucketServerWebhook.class);
                assertThat(message.getSecret()).isEqualTo("password");
            });
    }

    @SuppressWarnings("serial")
    private SCMSourceOwner getSCMSourceOwnerMock(SCMSource scmSource) {
        SCMSourceOwner scmSourceOwner = mock(SCMSourceOwner.class);
        when(scmSourceOwner.getSCMSources()).thenReturn(List.of(scmSource));
        when(scmSourceOwner.getSCMSourceCriteria(any(SCMSource.class))).thenReturn(new SCMSourceCriteria() {

            @Override
            public boolean isHead(Probe probe, TaskListener listener) throws IOException {
                return probe.stat("markerfile.txt").exists();
            }

            @Override
            public int hashCode() {
                return getClass().hashCode();
            }

            @Override
            public boolean equals(Object obj) {
                return getClass().isInstance(obj);
            }
        });
        return scmSourceOwner;
    }
}
