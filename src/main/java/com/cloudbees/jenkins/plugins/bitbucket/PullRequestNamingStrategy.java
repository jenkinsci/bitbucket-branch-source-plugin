/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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

/**
 * Pull request naming strategies:
 * <ul>
 *   <li>PR_ID: Use pull request ID (default).</li>
 *   <li>PR_ID_TITLE: Use pull request ID and title. (experimental)</li>
 *   <li>PR_ID_BRANCH_NAME: Use pull request ID and branch name.<li>
 *   <li>PR_TITLE: Use only pull request title. (experimental)</li>
 *   <li>PR_BRANCH_NAME: Use only pull request branch name.</li>
 * </ul>
 *
 * @since 2.10.0
 */
public enum PullRequestNamingStrategy {
    /**
     * Use pull request ID (default)
     * Transforms the branch name to <code>PR-#</code>, example: <code>PR-24</code>
     */
    PR_ID(true, false, false),
    /**
     * Use pull request ID and title
     * Transforms the branch name to <code>PR-#: {PR-title}</code>, example: <code>PR-24: My feature PR title</code>
     * Note that whitespace characters will occur! This can potentially conflict with other Jenkins plugins.
     */
    PR_ID_TITLE(true, true, false),
    /**
     * Use pull request ID and branch name
     * Transforms the branch name to <code>PR-#_{PR-branch-name}</code>, example: <code>PR-24_feature/my-branch-name</code>
     */
    PR_ID_BRANCH_NAME(true, false, true),
    /**
     * Use pull request title
     * Transforms the branch name to <code>{PR-title}</code>, example: <code>My feature PR title</code>
     * Note that whitespace characters will occur! This can potentially conflict with other Jenkins plugins.
     */
    PR_TITLE(false, true, false),
    /**
     * Use pull request branch name
     * Transforms the branch name to <code>{PR-branch-name}</code>, example: <code>feature/my-branch-name</code>
     */
    PR_BRANCH_NAME(false, false, true);

    private final boolean idPrefix;
    private final boolean prTitle;
    private final boolean prBranch;

    PullRequestNamingStrategy(boolean idPrefix, boolean prTitle, boolean prBranch) {
        this.idPrefix = idPrefix;
        this.prTitle = prTitle;
        this.prBranch = prBranch;
    }

    public boolean showIdPrefix() {
        return idPrefix;
    }

    public boolean showPrTitle() {
        return prTitle;
    }

    public boolean showPrBranch() {
        return prBranch;
    }
}
