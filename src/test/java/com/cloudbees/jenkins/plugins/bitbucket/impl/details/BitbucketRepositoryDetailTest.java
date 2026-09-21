/*
 * The MIT License
 *
 * Copyright (c) 2026, Nikolas Falco
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
package com.cloudbees.jenkins.plugins.bitbucket.impl.details;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import hudson.model.Run;
import jenkins.scm.api.SCMSource;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BitbucketRepositoryDetailTest {
    @Test
    void test_RepositoryDetail_showsDetails() {
        BitbucketSCMSource instance = mock(BitbucketSCMSource.class);
        when(instance.getRepoOwner()).thenReturn("amuniz");
        when(instance.getRepository()).thenReturn("test-repos");
        when(instance.getServerUrl()).thenReturn("https://bitbucket.org");
        Run<?, ?> run = mock(Run.class);

        try (MockedStatic<SCMSource.SourceByItem> mocked = mockStatic(SCMSource.SourceByItem.class)) {
            mocked.when(() -> SCMSource.SourceByItem.findSource(any())).thenReturn(instance);

            BitbucketRepositoryDetail detail = new BitbucketRepositoryDetail(run);

            assertThat(detail.getDisplayName()).isEqualTo("amuniz/test-repos");
            assertThat(detail.getLink()).isEqualTo("https://bitbucket.org/amuniz/test-repos");
        }
    }

    @Test
    void test_RepositoryDetail_noConfiguredSource() {
        Run<?, ?> run = mock(Run.class);

        BitbucketRepositoryDetail detail = new BitbucketRepositoryDetail(run);

        assertThat(detail.getDisplayName()).isNull();
        assertThat(detail.getLink()).isNull();
    }
}
