/*
 * The MIT License
 *
 * Copyright (c) 2026, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketMockApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketTeam;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.UserRoleInRepository;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BitbucketSCMSourceRepositoryAutocompleteTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "a", " a "})
    void short_input_returns_without_accessing_jenkins_or_bitbucket(String value) throws Exception {
        assertThat(new BitbucketSCMSource.DescriptorImpl()
                .doAutoCompleteRepository(null, null, null, "owner", value).getValues()).isEmpty();
    }

    @Test
    @WithJenkins
    void autocomplete_passes_trimmed_filter_and_preserves_role(JenkinsRule rule) throws Exception {
        BitbucketApi api = mock(BitbucketApi.class);
        BitbucketRepository repository = mock(BitbucketRepository.class);
        when(repository.getRepositoryName()).thenReturn("test-repository");
        doReturn(List.of(repository)).when(api).getRepositories(UserRoleInRepository.MEMBER, "te", 1);
        doReturn(List.of(repository)).when(api).getRepositories(null, "te", 1);
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, api);
        try {
            BitbucketSCMSource.DescriptorImpl descriptor = rule.jenkins.getDescriptorByType(BitbucketSCMSource.DescriptorImpl.class);
            assertThat(descriptor.doAutoCompleteRepository(null, BitbucketCloudEndpoint.SERVER_URL, null, "owner", " te ")
                    .getValues()).containsExactly("test-repository");
            verify(api).getRepositories(UserRoleInRepository.MEMBER, "te", 1);
            when(api.getTeam()).thenReturn(mock(BitbucketTeam.class));
            assertThat(descriptor.doAutoCompleteRepository(null, BitbucketCloudEndpoint.SERVER_URL, null, "owner", "te")
                    .getValues()).containsExactly("test-repository");
            verify(api).getRepositories(null, "te", 1);
            verify(api, never()).getRepositories();
            verify(api, never()).getRepositories(UserRoleInRepository.MEMBER);
        } finally {
            BitbucketMockApiFactory.remove(BitbucketCloudEndpoint.SERVER_URL);
        }
    }

    @Test
    void legacy_api_fallback_filters_and_pages_results() throws Exception {
        BitbucketApi api = mock(BitbucketApi.class, CALLS_REAL_METHODS);
        List<BitbucketRepository> repositories = IntStream.range(0, 205).mapToObj(index -> {
            BitbucketRepository repository = mock(BitbucketRepository.class);
            when(repository.getRepositoryName()).thenReturn("test-" + index);
            return repository;
        }).toList();
        doReturn(repositories).when(api).getRepositories(UserRoleInRepository.MEMBER);

        assertThat(api.getRepositories(UserRoleInRepository.MEMBER, null, null)).hasSize(205);
        assertThat(api.getRepositories(UserRoleInRepository.MEMBER, " TEST ", null)).hasSize(205);
        assertThat(api.getRepositories(UserRoleInRepository.MEMBER, null, 1))
                .extracting(BitbucketRepository::getRepositoryName)
                .containsExactlyElementsOf(repositories.subList(0, 100).stream().map(BitbucketRepository::getRepositoryName).toList());
        assertThat(api.getRepositories(UserRoleInRepository.MEMBER, "test", 2))
                .extracting(BitbucketRepository::getRepositoryName)
                .containsExactlyElementsOf(repositories.subList(100, 200).stream().map(BitbucketRepository::getRepositoryName).toList());
        assertThat(api.getRepositories(UserRoleInRepository.MEMBER, "test", 3))
                .extracting(BitbucketRepository::getRepositoryName)
                .containsExactlyElementsOf(repositories.subList(200, 205).stream().map(BitbucketRepository::getRepositoryName).toList());
        assertThat(api.getRepositories(UserRoleInRepository.MEMBER, "test", 4)).isEmpty();
        assertThat(api.getRepositories(UserRoleInRepository.MEMBER, "missing", null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {-2, -1, 0})
    void legacy_api_fallback_rejects_invalid_page_without_calling_api(int page) throws Exception {
        BitbucketApi api = mock(BitbucketApi.class, CALLS_REAL_METHODS);
        assertThatThrownBy(() -> api.getRepositories(UserRoleInRepository.MEMBER, null, page))
                .isInstanceOf(IllegalArgumentException.class);
        verify(api, never()).getRepositories(UserRoleInRepository.MEMBER);
    }

    @Test
    @WithJenkins
    void no_matches_returns_empty_suggestions(JenkinsRule rule) throws Exception {
        BitbucketApi api = mock(BitbucketApi.class);
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, api);
        try {
            assertThat(rule.jenkins.getDescriptorByType(BitbucketSCMSource.DescriptorImpl.class)
                    .doAutoCompleteRepository(null, BitbucketCloudEndpoint.SERVER_URL, null, "owner", "xx")
                    .getValues()).isEmpty();
        } finally {
            BitbucketMockApiFactory.remove(BitbucketCloudEndpoint.SERVER_URL);
        }
    }
}
