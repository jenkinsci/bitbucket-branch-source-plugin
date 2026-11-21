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

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus.Status;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketException;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketMockApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketTeam;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.UserRoleInRepository;
import com.cloudbees.jenkins.plugins.bitbucket.impl.credentials.BitbucketAccessTokenAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.impl.credentials.BitbucketClientCertificateAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.impl.credentials.BitbucketOAuthAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.impl.credentials.BitbucketUsernamePasswordAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.DateUtils;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.JsonParser;
import com.cloudbees.jenkins.plugins.bitbucket.test.util.BitbucketTestUtil;
import hudson.ProxyConfiguration;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.net.URIBuilder;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BitbucketCloudApiClientTest {

    @Test
    @WithJenkins
    void repository_dropdown_uses_repository_names(JenkinsRule rule) throws Exception {
        BitbucketApi client = mock(BitbucketApi.class);
        when(client.getTeam()).thenReturn(mock(BitbucketTeam.class));
        when(client.getRepositoryNames(null)).thenReturn(List.of("alpha", "bravo"));
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, client);
        try {
            BitbucketSCMSource.DescriptorImpl descriptor =
                    rule.jenkins.getDescriptorByType(BitbucketSCMSource.DescriptorImpl.class);
            ListBoxModel items = descriptor.doFillRepositoryItems(null, BitbucketCloudEndpoint.SERVER_URL, null, "team");
            assertThat(items).extracting(option -> option.value).containsExactly("alpha", "bravo");
            verify(client, never()).getRepositories(any());
        } finally {
            BitbucketMockApiFactory.remove(BitbucketCloudEndpoint.SERVER_URL);
        }
    }

    @Test
    void repository_names_request_only_required_fields_and_follow_pagination() throws Exception {
        List<String> requests = new ArrayList<>();
        BitbucketAccessTokenAuthenticator authenticator = mock(BitbucketAccessTokenAuthenticator.class);
        try (BitbucketCloudApiClient client = new BitbucketCloudApiClient(
                false, 0, 0, "team", "PROJECT", null, authenticator) {
            @Override
            protected String getRequest(String url) {
                requests.add(url);
                return switch (requests.size()) {
                    case 1 -> """
                            {"values":[{"full_name":"team/zulu"}],
                             "next":"https://api.bitbucket.org/2.0/repositories/team?cursor=a%2Bb"}
                            """;
                    case 2 -> """
                            {"values":[{"full_name":"team/alpha"}],
                             "next":"https://api.bitbucket.org/2.0/repositories/team?cursor=second&fields=next%2Cvalues.full_name"}
                            """;
                    case 3 -> """
                            {"values":[{"full_name":"team/bravo"}]}
                            """;
                    default -> throw new AssertionError("Unexpected Bitbucket request");
                };
            }
        }) {
            assertThat(client.getRepositoryNames(UserRoleInRepository.MEMBER)).containsExactly("alpha", "bravo", "zulu");
        }

        assertThat(requests).hasSize(3);
        assertThat(new URIBuilder(requests.get(0)).getQueryParams()).extracting(p -> p.getName() + "=" + p.getValue())
                .containsExactlyInAnyOrder("role=member", "pagelen=100", "q=project.key=\"PROJECT\"",
                        "fields=next,values.full_name");
        assertThat(new URIBuilder(requests.get(1)).getQueryParams()).extracting(p -> p.getName() + "=" + p.getValue())
                .containsExactlyInAnyOrder("cursor=a+b", "fields=next,values.full_name");
        assertThat(URI.create(requests.get(2)).getRawQuery()).isEqualTo("cursor=second&fields=next%2Cvalues.full_name");
    }

    @Test
    void repository_names_cache_is_separate_from_full_repositories() throws Exception {
        List<String> requests = new ArrayList<>();
        try (BitbucketCloudApiClient client = new BitbucketCloudApiClient(
                true, 360, 180, "repository-name-cache-test", null, null, null) {
            @Override
            protected String getRequest(String url) {
                requests.add(url);
                if (url.contains("fields=")) {
                    return """
                            {"values":[{"full_name":"repository-name-cache-test/alpha"}]}
                            """;
                }
                return """
                        {"values":[{"full_name":"repository-name-cache-test/alpha","is_private":true}]}
                        """;
            }
        }) {
            assertThat(client.getRepositoryNames(null)).containsExactly("alpha");
            assertThat(client.getRepositories()).singleElement().satisfies(repository -> assertThat(repository.isPrivate()).isTrue());
            assertThat(client.getRepositoryNames(null)).containsExactly("alpha");
            assertThat(client.getRepositories()).hasSize(1);
            assertThat(requests).hasSize(2);
        }
    }

    private String loadPayload(String api) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(getClass().getSimpleName() + "/" + api + "Payload.json")) {
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        }
    }

    @Test
    @WithJenkins
    void test_proxy_configured_without_password(JenkinsRule r) throws Exception {
        ProxyConfiguration proxyConfiguration = spy(new ProxyConfiguration("proxy.lan", 8080, "username", null));

        r.jenkins.setProxy(proxyConfiguration);
        BitbucketIntegrationClientFactory.getApiMockClient(BitbucketCloudEndpoint.SERVER_URL);

        verify(proxyConfiguration).createProxy("api.bitbucket.org");
        verify(proxyConfiguration).getUserName();
        verify(proxyConfiguration).getSecretPassword();
    }

    @Test
    void verify_status_notification_name_max_length() throws Exception {
        BitbucketApi client = BitbucketIntegrationClientFactory.getApiMockClient(BitbucketCloudEndpoint.SERVER_URL);
        BitbucketBuildStatus status = new BitbucketBuildStatus();
        status.setName(RandomStringUtils.secure().nextAlphanumeric(300));
        status.setState(Status.INPROGRESS);
        status.setHash("046d9a3c1532acf4cf08fe93235c00e4d673c1d2");
        status.setKey("PRJ/REPO");

        client.postBuildStatus(status);

        HttpRequest request = BitbucketTestUtil.extractRequest(client);
        assertThat(request).isNotNull()
            .isInstanceOf(HttpPost.class);
        try (InputStream content = ((HttpPost) request).getEntity().getContent()) {
            String json = IOUtils.toString(content, StandardCharsets.UTF_8);
            assertThatJson(json).node("name").isString().hasSize(255);
        }
    }

    @Test
    void get_repository_parse_correctly_date_from_cloud() throws Exception {
        BitbucketCloudRepository repository = JsonParser.toJava(loadPayload("getRepository"), BitbucketCloudRepository.class);
        assertThat(repository.getUpdatedOn()).describedAs("update on date is null").isNotNull();
        Date expectedDate = DateUtils.getDate(2025, 1, 27, 14, 15, 58, 600);
        assertThat(repository.getUpdatedOn()).isEqualTo(expectedDate);
    }

    @Test
    void verify_avatar_does_not_authenticate_request() throws Exception {
        BitbucketApi client = BitbucketIntegrationClientFactory.getApiMockClient(BitbucketCloudEndpoint.SERVER_URL);
        BitbucketAuthenticator authenticator = BitbucketIntegrationClientFactory.extractAuthenticator(client);
        client.getAvatar("https://bytebucket.org/ravatar/%7B3deb8c29-778a-450c-8f69-3e50a18079df%7D?ts=default");
        verify(authenticator, never()).configureRequest(any(HttpRequest.class));
    }

    @Test
    void verifyUpdateWebhookURL() throws Exception {
        BitbucketApi client = BitbucketIntegrationClientFactory.getApiMockClient(BitbucketCloudEndpoint.SERVER_URL);
        Optional<? extends BitbucketWebHook> webHook = client.getWebHooks().stream()
                .filter(h -> h.getDescription().contains("Jenkins"))
                .findFirst();
        assertThat(webHook).isPresent();

        client.updateCommitWebHook(webHook.get());
        HttpRequest request = BitbucketTestUtil.extractRequest(client);
        assertThat(request).isNotNull()
            .isInstanceOfSatisfying(HttpPut.class, put ->
                assertThat(put.getRequestUri()).isEqualTo("https://api.bitbucket.org/2.0/repositories/amuniz/test-repos/hooks/%7B202cf34e-7ccf-44b7-ba6b-8827a14d5324%7D"));
    }

    @Test
    void test_supported_auth() throws Exception {
        try (BitbucketApi client = new BitbucketCloudApiClient(false, 0, 0, null, null, null, mock(BitbucketOAuthAuthenticator.class))) {}
        try (BitbucketApi client = new BitbucketCloudApiClient(false, 0, 0, null, null, null, mock(BitbucketAccessTokenAuthenticator.class))) {}

        assertThatThrownBy(() -> new BitbucketCloudApiClient(false, 0, 0, null, null, null, mock(BitbucketClientCertificateAuthenticator.class)))
            .isInstanceOf(BitbucketException.class);
        assertThatThrownBy(() -> new BitbucketCloudApiClient(false, 0, 0, null, null, null, mock(BitbucketUsernamePasswordAuthenticator.class)))
            .isInstanceOf(BitbucketException.class);
    }
}
