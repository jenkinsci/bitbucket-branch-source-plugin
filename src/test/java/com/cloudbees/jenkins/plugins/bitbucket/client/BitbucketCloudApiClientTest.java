/*
 * The MIT License
 *
 * Copyright (c) 2018, Nikolas Falco
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
package com.cloudbees.jenkins.plugins.bitbucket.client;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus.Status;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory.IRequestAudit;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudRepository;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.JsonParser;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ProxyConfiguration;
import io.jenkins.cli.shaded.org.apache.commons.lang.RandomStringUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.ArgumentCaptor;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class BitbucketCloudApiClientTest {

    private String loadPayload(String api) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(getClass().getSimpleName() + "/" + api + "Payload.json")) {
            return IOUtils.toString(is, "UTF-8");
        }
    }

    @Test
    @WithJenkins
    void test_proxy_configurated_without_password(JenkinsRule r) throws Exception {
        ProxyConfiguration proxyConfiguration = spy(new ProxyConfiguration("proxy.lan", 8080, "username", null));

        r.jenkins.setProxy(proxyConfiguration);
        BitbucketIntegrationClientFactory.getApiMockClient(BitbucketCloudEndpoint.SERVER_URL);

        verify(proxyConfiguration).createProxy("api.bitbucket.org");
        verify(proxyConfiguration).getUserName();
        verify(proxyConfiguration).getSecretPassword();
    }

    @Test
    void verify_status_notitication_name_max_length() throws Exception {
        BitbucketApi client = BitbucketIntegrationClientFactory.getApiMockClient(BitbucketCloudEndpoint.SERVER_URL);
        BitbucketBuildStatus status = new BitbucketBuildStatus();
        status.setName(RandomStringUtils.randomAlphanumeric(300));
        status.setState(Status.INPROGRESS);
        status.setHash("046d9a3c1532acf4cf08fe93235c00e4d673c1d3");

        client.postBuildStatus(status);

        HttpRequestBase request = extractRequest(client);
        assertThat(request).isNotNull()
            .isInstanceOf(HttpPost.class);
        try (InputStream content = ((HttpPost) request).getEntity().getContent()) {
            String json = IOUtils.toString(content, StandardCharsets.UTF_8);
            assertThatJson(json).node("name").isString().hasSize(255);
        }
    }

    private HttpRequestBase extractRequest(@NonNull BitbucketApi client) {
        IRequestAudit audit = ((IRequestAudit) client).getAudit();
        ArgumentCaptor<HttpRequestBase> captor = ArgumentCaptor.forClass(HttpRequestBase.class);
        verify(audit).request(captor.capture());
        return captor.getValue();
    }

    private void resetAudit(@NonNull BitbucketApi client) {
        reset(((IRequestAudit) client).getAudit());
    }

    @Test
    void get_repository_parse_correctly_date_from_cloud() throws Exception {
        BitbucketCloudRepository repository = JsonParser.toJava(loadPayload("getRepository"), BitbucketCloudRepository.class);
        assertThat(repository.getUpdatedOn()).describedAs("update on date is null").isNotNull();
        Date expectedDate = DateUtils.getDate(2025, 1, 27, 14, 15, 58, 600);
        assertThat(repository.getUpdatedOn()).isEqualTo(expectedDate);
    }

    @Test
    void verifyUpdateWebhookURL() throws Exception {
        BitbucketApi client = BitbucketIntegrationClientFactory.getApiMockClient(BitbucketCloudEndpoint.SERVER_URL);
        Optional<? extends BitbucketWebHook> webHook = client.getWebHooks().stream()
                .filter(h -> h.getDescription().contains("Jenkins"))
                .findFirst();
        assertThat(webHook).isPresent();

        resetAudit(client);
        client.updateCommitWebHook(webHook.get());
        HttpRequestBase request = extractRequest(client);
        assertThat(request).isNotNull()
            .isInstanceOfSatisfying(HttpPut.class, put ->
                assertThat(put.getURI()).hasToString("https://api.bitbucket.org/2.0/repositories/amuniz/test-repos/hooks/%7B202cf34e-7ccf-44b7-ba6b-8827a14d5324%7D"));
    }

}
