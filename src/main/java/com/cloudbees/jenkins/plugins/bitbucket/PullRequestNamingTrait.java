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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.trait.Discovery;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * A {@link SCMSourceTrait} trait for Bitbucket that will internally rename pull request branches according to a
 * naming strategy in order to customize the display names. This does not affect the pull request names on Bitbucket.
 *
 * @since 2.10.0
 */
public class PullRequestNamingTrait extends SCMSourceTrait {
    /**
     * The pull request naming strategy.
     */
    @NonNull
    private final PullRequestNamingStrategy namingStrategy;
    /**
     * The pull request naming strategy exclusion regular expression.
     */
    @NonNull
    private final String namingExcludeRegex;
    /**
     * The compiled pull request naming strategy exclusion regular expression {@link Pattern}.
     */
    @CheckForNull
    private Pattern namingExcludePattern;

    /**
     * Constructor for both stapler and programmatic instantiation.
     *
     * @param namingStrategy the {@link PullRequestNamingStrategy}.
     * @param namingExcludeRegex pull request naming strategy exclusion regular expression.
     */
    @DataBoundConstructor
    public PullRequestNamingTrait(PullRequestNamingStrategy namingStrategy, String namingExcludeRegex) {
        this.namingStrategy = namingStrategy;
        this.namingExcludeRegex = namingExcludeRegex;
        this.namingExcludePattern = Pattern.compile(namingExcludeRegex);
    }

    /**
     * Gets the {@link PullRequestNamingStrategy}.
     *
     * @return the {@link PullRequestNamingStrategy}.
     */
    @NonNull
    public PullRequestNamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    /**
     * Gets the pull request naming strategy exclusion regular expression.
     *
     * @return the regular expression.
     */
    @NonNull
    public String getNamingExcludeRegex() {
        return namingExcludeRegex;
    }

    /**
     * Gets the compiled pull request naming strategy exclusion regular expression {@link Pattern}.
     *
     * @return the compiled {@link Pattern}.
     */
    @NonNull
    private Pattern getNamingExcludePattern() {
        if (namingExcludePattern == null) {
            namingExcludePattern = Pattern.compile(getNamingExcludeRegex());
        }
        return namingExcludePattern;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        BitbucketSCMSourceContext ctx = (BitbucketSCMSourceContext) context;
        ctx.pullRequestNamingStrategy(getNamingStrategy());
        ctx.pullRequestNamingExcludePattern(getNamingExcludePattern());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean includeCategory(@NonNull SCMHeadCategory category) {
        return category instanceof ChangeRequestSCMHeadCategory;
    }

    /**
     * Our descriptor.
     */
    @Symbol("bitbucketPullRequestNaming")
    @Extension
    @Discovery
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.PullRequestNamingTrait_displayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<? extends SCMSourceContext> getContextClass() {
            return BitbucketSCMSourceContext.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<? extends SCMSource> getSourceClass() {
            return BitbucketSCMSource.class;
        }

        /**
         * Populates the naming strategy options.
         *
         * @return the naming strategy options.
         */
        @NonNull
        @Restricted(NoExternalUse.class)
        @SuppressWarnings("unused") // stapler
        public ListBoxModel doFillNamingStrategyItems() {
            ListBoxModel result = new ListBoxModel();
            result.add(Messages.PullRequestNamingTrait_pullRequestId(), "PR_ID");
            result.add(Messages.PullRequestNamingTrait_pullRequestIdTitle(), "PR_ID_TITLE");
            result.add(Messages.PullRequestNamingTrait_pullRequestIdBranchName(), "PR_ID_BRANCH_NAME");
            result.add(Messages.PullRequestNamingTrait_pullRequestTitle(), "PR_TITLE");
            result.add(Messages.PullRequestNamingTrait_pullRequestBranchName(), "PR_BRANCH_NAME");
            return result;
        }

        /**
         * Form validation for the pull request naming strategy exclude {@link Pattern} expression.
         *
         * @param value the regular expression.
         * @return the validation results.
         */
        @Restricted(NoExternalUse.class) // stapler
        public FormValidation doCheckNamingExcludeRegex(@QueryParameter String value) {
            try {
                Pattern.compile(value);
                return FormValidation.ok();
            } catch (PatternSyntaxException e) {
                return FormValidation.error(e.getMessage());
            }
        }
    }
}
