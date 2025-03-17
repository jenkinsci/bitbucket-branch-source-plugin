/*
 * The MIT License
 *
 * Copyright (c) 2025, ugrave.
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
package com.cloudbees.jenkins.plugins.bitbucket.impl.notifier;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus.TestResults;
import hudson.model.Run;
import hudson.tasks.test.AbstractTestResultAction;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class TestResultsAdapterTest {

    @Test
    void shouldReturnNullIfNoTestResultAvailable() {
        Run<?, ?> build = Mockito.mock(Run.class);

        TestResults result = TestResultsAdapter.getTestResults(build);

        assertThat(result).isNull();
        Mockito.verify(build).getActions(AbstractTestResultAction.class);
    }

    @Test
    void shouldSummariesTestResult() {
        Run<?, ?> build = Mockito.mock(Run.class);
        Mockito.when(build.getActions(Mockito.any())).thenReturn(
            List.of(
                new MockTestResultAction(4, 2, 1),
                new MockTestResultAction(2, 1, 1)
            )
        );

        TestResults result = TestResultsAdapter.getTestResults(build);

        assertThat(result)
            .extracting(TestResults::getSuccessful, TestResults::getFailed, TestResults::getSkipped)
            .containsExactly(1, 3, 2);
    }

    private static class MockTestResultAction extends AbstractTestResultAction<MockTestResultAction> {

        private final int totalCount;
        private final int failCount;
        private final int skipCount;

        private MockTestResultAction(int totalCount, int failCount, int skipCount) {
            this.totalCount = totalCount;
            this.failCount = failCount;
            this.skipCount = skipCount;
        }

        @Override
        public int getFailCount() {
            return failCount;
        }

        @Override
        public int getTotalCount() {
            return totalCount;
        }

        @Override
        public int getSkipCount() {
            return skipCount;
        }

        @Override
        public Object getResult() {
            return null;
        }
    }
}
