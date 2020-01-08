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

import hudson.util.ListBoxModel;
import jenkins.scm.api.SCMHeadObserver;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public class PullRequestNamingTraitTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Test
    public void given__idBranchNameStrategy__when__appliedToContext__then__strategiesCorrect() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assertThat(ctx.pullRequestNamingStrategy(), is(PullRequestNamingStrategy.PR_ID));
        PullRequestNamingTrait instance = new PullRequestNamingTrait(PullRequestNamingStrategy.PR_ID_BRANCH_NAME, "test");
        instance.decorateContext(ctx);
        assertThat(ctx.pullRequestNamingStrategy(), is(PullRequestNamingStrategy.PR_ID_BRANCH_NAME));
        assertThat(ctx.pullRequestNamingStrategy().showIdPrefix(), is(true));
        assertThat(ctx.pullRequestNamingStrategy().showPrTitle(), is(false));
        assertThat(ctx.pullRequestNamingStrategy().showPrBranch(), is(true));
    }

    @Test
    public void given__namingExcludeRegex__when__appliedToContext__then__excludePatternCorrect() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assertThat(ctx.pullRequestNamingExcludePattern().pattern(), emptyString());
        PullRequestNamingTrait instance = new PullRequestNamingTrait(PullRequestNamingStrategy.PR_ID, "test");
        instance.decorateContext(ctx);
        assertThat(ctx.pullRequestNamingExcludePattern().pattern(), is("test"));
        assertThat(ctx.pullRequestNamingExcludePattern().pattern(), not("toast"));
    }

    @Test
    public void given__descriptor__when__displayingOptions__then__allCorrect() {
        ListBoxModel options =
                j.jenkins.getDescriptorByType(PullRequestNamingTrait.DescriptorImpl.class).doFillNamingStrategyItems();
        assertThat(options.size(), is(5));
        assertThat(options.get(0).value, is("PR_ID"));
        assertThat(options.get(1).value, is("PR_ID_TITLE"));
        assertThat(options.get(2).value, is("PR_ID_BRANCH_NAME"));
        assertThat(options.get(3).value, is("PR_TITLE"));
        assertThat(options.get(4).value, is("PR_BRANCH_NAME"));
    }
}
