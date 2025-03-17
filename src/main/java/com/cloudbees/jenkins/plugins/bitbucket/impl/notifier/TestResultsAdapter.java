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
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Run;
import hudson.tasks.test.AbstractTestResultAction;
import java.util.List;

class TestResultsAdapter {

    private static final boolean JUNIT_PLUGIN_INSTALLED = isJUnitPluginInstalled();

    static @Nullable TestResults getTestResults(@NonNull Run<?, ?> build) {
        return JUNIT_PLUGIN_INSTALLED ? getTestResultsFromBuildAction(build) : null;
    }

    private static @Nullable TestResults getTestResultsFromBuildAction(@NonNull Run<?, ?> build) {
        List<AbstractTestResultAction> testResultActions = build.getActions(AbstractTestResultAction.class);
        if (testResultActions.isEmpty()) {
            return null;
        }
        return testResultActions.stream()
            .collect(TestResultSummary::new, TestResultSummary::accept, TestResultSummary::combine)
            .getTestResults();
    }

    private static boolean isJUnitPluginInstalled() {
        try {
            Class.forName("hudson.tasks.test.AbstractTestResultAction");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static class TestResultSummary {

        private int totalCount;
        private int failCount;
        private int skipCount;

        void accept(@NonNull AbstractTestResultAction<?> value) {
            totalCount += value.getTotalCount();
            failCount += value.getFailCount();
            skipCount += value.getSkipCount();
        }

        void combine(@NonNull TestResultSummary other) {
            totalCount += other.totalCount;
            failCount += other.failCount;
            skipCount += other.skipCount;
        }

        @NonNull
        TestResults getTestResults() {
            return new TestResults( totalCount - failCount - skipCount, failCount, skipCount);
        }
    }

}
