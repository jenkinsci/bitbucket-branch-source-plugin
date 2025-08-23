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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook;

import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookProcessorException;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.cloud.CloudWebhookConfiguration;
import com.cloudbees.plugins.credentials.CredentialsScope;
import hudson.util.Secret;
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
import org.assertj.core.api.InstanceOfAssertFactories;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractWebhookProcessorTest {

    private AbstractWebhookProcessor sut;
    protected SCMHeadEvent<?> scmEvent;

    @BeforeEach
    void setup() {
        sut = new AbstractWebhookProcessor() {

            @Override
            public boolean canHandle(Map<String, String> headers, MultiValuedMap<String, String> parameters) {
                return true;
            }

            @Override
            public void process(String eventType, String payload, Map<String, Object> origin, BitbucketEndpoint endpoint) {
                throw new UnsupportedOperationException();
            }

            @Override
            StringCredentials lookupCredentials(String credentialsId, String serverURL) {
                return new StringCredentialsImpl(CredentialsScope.GLOBAL, credentialsId, null, Secret.fromString("Gkvl$k$wyNpQAF42"));
            }
        };
    }

    @Test
    void test_getEventType_header() throws Exception {
        Map<String, String> headers = new HashMap<>();
        MultiValuedMap<String, String> parameters = new ArrayListValuedHashMap<>();

        headers.put("X-Event-Key", "pullrequest:created");
        assertThat(sut.getEventType(headers, parameters)).isEqualTo("pullrequest:created");
    }

    void test_getServerURL_header() throws Exception {
        Map<String, String> headers = new HashMap<>();
        MultiValuedMap<String, String> parameters = new ArrayListValuedHashMap<>();
        parameters.put("server_url", "https://localhost:8080");

        assertThat(sut.getServerURL(headers, parameters)).isEqualTo("https://localhost:8080");
    }

    @Test
    void test_signature() throws Exception {
        BitbucketEndpoint endpoint = getEndpoint();
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Hub-Signature", "sha256=f205c729821c6954aff2afe72b965c34015b4baf96ea8ddc2cc44999c014a035");

        String payload = loadResource("signed_payload.json");
        assertThatNoException().isThrownBy(() -> sut.verifyPayload(headers, payload, endpoint));
    }

    @Test
    void test_bad_signature() throws Exception {
        BitbucketEndpoint endpoint = getEndpoint();
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Hub-Signature", "sha256=f205c729821c6954aff2afe72b965c34015b4baf96ea8ddc2cc44999c014a036");

        String payload = loadResource("signed_payload.json");
        assertThatThrownBy(() -> sut.verifyPayload(headers, payload, endpoint))
            .isInstanceOf(BitbucketWebhookProcessorException.class)
            .hasMessage("Signature verification failed")
            .asInstanceOf(InstanceOfAssertFactories.type(BitbucketWebhookProcessorException.class))
            .satisfies(ex -> assertThat(ex.getHttpCode()).isEqualTo(403));
    }

    @Test
    void test_signature_is_missing() throws Exception {
        BitbucketEndpoint endpoint = getEndpoint();

        String payload = loadResource("signed_payload.json");
        assertThatThrownBy(() -> sut.verifyPayload(Collections.emptyMap(), payload, endpoint))
            .isInstanceOf(BitbucketWebhookProcessorException.class)
            .hasMessage("Payload has not be signed, configure the webHook secret in Bitbucket as documented at https://github.com/jenkinsci/bitbucket-branch-source-plugin/blob/master/docs/USER_GUIDE.adoc#webhooks-registering")
            .asInstanceOf(InstanceOfAssertFactories.type(BitbucketWebhookProcessorException.class))
            .satisfies(ex -> assertThat(ex.getHttpCode()).isEqualTo(403));
    }

    private String loadResource(String resource) throws IOException {
        try (InputStream stream = this.getClass().getResourceAsStream("cloud/" + resource)) {
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        }
    }

    private BitbucketEndpoint getEndpoint() {
        String credentialsId = "hmac";
        BitbucketEndpoint endpoint = mock(BitbucketEndpoint.class);
        when(endpoint.getWebhook()).thenReturn(new CloudWebhookConfiguration(false, null, true, credentialsId));
        return endpoint;
    }

}
