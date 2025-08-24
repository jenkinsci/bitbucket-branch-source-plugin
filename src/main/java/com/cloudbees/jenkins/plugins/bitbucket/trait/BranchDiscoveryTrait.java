/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket.trait;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSourceContext;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSourceRequest;
import com.cloudbees.jenkins.plugins.bitbucket.BranchSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.Messages;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.trait.SCMHeadAuthority;
import jenkins.scm.api.trait.SCMHeadAuthorityDescriptor;
import jenkins.scm.api.trait.SCMHeadFilter;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.impl.trait.Discovery;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * A {@link Discovery} trait for bitbucket that will discover branches on the repository.
 *
 * @since 2.2.0
 */
public class BranchDiscoveryTrait extends SCMSourceTrait {
    /**
     * The strategy encoded as a bit-field.
     * <dl>
     *     <dt>Bit 0</dt>
     *     <dd>Build branches that are not filed as a PR</dd>
     *     <dt>Bit 1</dt>
     *     <dd>Build branches that are filed as a PR</dd>
     * </dl>
     */
    private final int strategyId;

    /**
     * Regex of branches that should always be included regardless of whether a merge request exists or not.
     */
    private String branchesAlwaysIncludedRegex;

    /**
     * The compiled {@link Pattern} of the branchesAlwaysIncludedRegex.
     */
    @CheckForNull
    private transient Pattern branchesAlwaysIncludedRegexPattern;

    /**
     * Constructor for stapler.
     *
     * @param strategyId the strategy id.
     */
    @DataBoundConstructor
    public BranchDiscoveryTrait(int strategyId) {
        this.strategyId = strategyId;
    }

    /**
     * Constructor for legacy code.
     *
     * @param buildBranch       build branches that are not filed as a PR.
     * @param buildBranchWithPr build branches that are also PRs.
     */
    public BranchDiscoveryTrait(boolean buildBranch, boolean buildBranchWithPr) {
        this.strategyId = (buildBranch ? 1 : 0) + (buildBranchWithPr ? 2 : 0);
    }

    /**
     * Returns the strategy id.
     *
     * @return the strategy id.
     */
    public int getStrategyId() {
        return strategyId;
    }

    /**
     * Returns the branchesAlwaysIncludedRegex.
     *
     * @return the branchesAlwaysIncludedRegex.
     */
    public String getBranchesAlwaysIncludedRegex() {
        return branchesAlwaysIncludedRegex;
    }

    /**
     * Sets the branchesAlwaysIncludedRegex.
     */
    @DataBoundSetter
    public void setBranchesAlwaysIncludedRegex(@CheckForNull String branchesAlwaysIncludedRegex) {
        this.branchesAlwaysIncludedRegex = Util.fixEmptyAndTrim(branchesAlwaysIncludedRegex);
    }

    /**
     * Returns the compiled {@link Pattern} of the branchesAlwaysIncludedRegex.
     *
     * @return the branchesAlwaysIncludedRegexPattern.
     */
    public Pattern getBranchesAlwaysIncludedRegexPattern() {
        if (branchesAlwaysIncludedRegex != null && branchesAlwaysIncludedRegexPattern == null) {
            branchesAlwaysIncludedRegexPattern = Pattern.compile(branchesAlwaysIncludedRegex);
        }

        return branchesAlwaysIncludedRegexPattern;
    }

    /**
     * Returns {@code true} if building branches that are not filed as a PR.
     *
     * @return {@code true} if building branches that are not filed as a PR.
     */
    @Restricted(NoExternalUse.class)
    public boolean isBuildBranch() {
        return (strategyId & 1) != 0;
    }

    /**
     * Returns {@code true} if building branches that are filed as a PR.
     *
     * @return {@code true} if building branches that are filed as a PR.
     */
    @Restricted(NoExternalUse.class)
    public boolean isBuildBranchesWithPR() {
        return (strategyId & 2) != 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        BitbucketSCMSourceContext ctx = (BitbucketSCMSourceContext) context;
        ctx.wantBranches(true);
        ctx.withAuthority(new BranchSCMHeadAuthority());
        switch (strategyId) {
            case 1:
                ctx.wantOriginPRs(true);
                ctx.withFilter(new ExcludeOriginPRBranchesSCMHeadFilter(getBranchesAlwaysIncludedRegexPattern()));
                break;
            case 2:
                ctx.wantOriginPRs(true);
                ctx.withFilter(new OnlyOriginPRBranchesSCMHeadFilter(getBranchesAlwaysIncludedRegexPattern()));
                break;
            case 3:
            default:
                // we don't care if it is a PR or not, we're taking them all, no need to ask for PRs and no need
                // to filter
                break;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean includeCategory(@NonNull SCMHeadCategory category) {
        return category.isUncategorized();
    }

    /**
     * Our descriptor.
     */
    @Symbol("bitbucketBranchDiscovery")
    @Extension
    @Discovery
    public static class DescriptorImpl extends BitbucketSCMSourceTraitDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.BranchDiscoveryTrait_displayName();
        }

        /**
         * Populates the strategy options.
         *
         * @return the strategy options.
         */
        @NonNull
        @Restricted(NoExternalUse.class)
        public ListBoxModel doFillStrategyIdItems() {
            ListBoxModel result = new ListBoxModel();
            result.add(Messages.BranchDiscoveryTrait_excludePRs(), "1");
            result.add(Messages.BranchDiscoveryTrait_onlyPRs(), "2");
            result.add(Messages.BranchDiscoveryTrait_allBranches(), "3");
            return result;
        }

        @NonNull
        @Restricted(NoExternalUse.class)
        @RequirePOST
        public FormValidation doCheckBranchesAlwaysIncludedRegex(@CheckForNull @AncestorInPath Item context, @QueryParameter String value) {
            if(context == null) {
                Jenkins.get().checkPermission(Jenkins.MANAGE);
            } else {
                context.checkPermission(Item.CONFIGURE);
            }

            if (value == null || value.isBlank()) {
                return FormValidation.ok();
            }
            try {
                Pattern.compile(value);
                return FormValidation.ok();
            } catch (PatternSyntaxException ex) {
                return FormValidation.error(ex.getMessage());
            }
        }
    }

    /**
     * Trusts branches from the origin repository.
     */
    public static class BranchSCMHeadAuthority extends SCMHeadAuthority<SCMSourceRequest, BranchSCMHead, SCMRevision> {
        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean checkTrusted(@NonNull SCMSourceRequest request, @NonNull BranchSCMHead head) {
            return true;
        }

        /**
         * Out descriptor.
         */
        @Extension
        public static class DescriptorImpl extends SCMHeadAuthorityDescriptor {
            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return "Trust origin branches";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isApplicableToOrigin(@NonNull Class<? extends SCMHeadOrigin> originClass) {
                return SCMHeadOrigin.Default.class.isAssignableFrom(originClass);
            }
        }
    }

    /**
     * Filter that excludes branches that are also filed as a pull request.
     */
    public static class ExcludeOriginPRBranchesSCMHeadFilter extends SCMHeadFilter {

        /**
         * The compiled {@link Pattern} of the branchesAlwaysIncludedRegex.
         */
        private final Pattern branchesAlwaysIncludedRegexPattern;

        public ExcludeOriginPRBranchesSCMHeadFilter() {
            branchesAlwaysIncludedRegexPattern = null;
        }

        /**
         * Constructor
         *
         * @param branchesAlwaysIncludedRegexPattern the branchesAlwaysIncludedRegexPattern.
         */
        public ExcludeOriginPRBranchesSCMHeadFilter(Pattern branchesAlwaysIncludedRegexPattern) {
            this.branchesAlwaysIncludedRegexPattern = branchesAlwaysIncludedRegexPattern;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isExcluded(@NonNull SCMSourceRequest request, @NonNull SCMHead head) {
            if (head instanceof BranchSCMHead && request instanceof BitbucketSCMSourceRequest) {
                if (branchesAlwaysIncludedRegexPattern != null
                        && branchesAlwaysIncludedRegexPattern
                                .matcher(head.getName())
                                .matches()) {
                    request.listener()
                            .getLogger()
                            .println("Include branch " + head.getName()
                                    + " because branch name matches always included pattern");
                    return false;
                }
                BitbucketSCMSourceRequest req = (BitbucketSCMSourceRequest) request;
                String fullName = req.getRepoOwner() + "/" + req.getRepository();
                try {
                    for (BitbucketPullRequest pullRequest : req.getPullRequests()) {
                        BitbucketRepository source = pullRequest.getSource().getRepository();
                        if (StringUtils.equalsIgnoreCase(fullName, source.getFullName())
                                && pullRequest.getSource().getBranch().getName().equals(head.getName())) {
                            request.listener().getLogger().println("Discard branch " + head.getName()
                                    + " because current strategy excludes branches that are also filed as a pull request");
                            return true;
                        }
                    }
                } catch (IOException | InterruptedException e) {
                    // should never happens because data in the requests has been already initialised
                    e.printStackTrace(request.listener().getLogger());
                }
            }
            return false;
        }
    }

    /**
     * Filter that excludes branches that are not also filed as a pull request.
     */
    public static class OnlyOriginPRBranchesSCMHeadFilter extends SCMHeadFilter {

        /**
         * The compiled {@link Pattern} of the branchesAlwaysIncludedRegex.
         */
        private final Pattern branchesAlwaysIncludedRegexPattern;

        public OnlyOriginPRBranchesSCMHeadFilter() {
            branchesAlwaysIncludedRegexPattern = null;
        }

        /**
         * Constructor
         *
         * @param branchesAlwaysIncludedRegexPattern the branchesAlwaysIncludedRegexPattern.
         */
        public OnlyOriginPRBranchesSCMHeadFilter(Pattern branchesAlwaysIncludedRegexPattern) {
            this.branchesAlwaysIncludedRegexPattern = branchesAlwaysIncludedRegexPattern;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isExcluded(@NonNull SCMSourceRequest request, @NonNull SCMHead head) {
            if (head instanceof BranchSCMHead && request instanceof BitbucketSCMSourceRequest) {
                if (branchesAlwaysIncludedRegexPattern != null
                        && branchesAlwaysIncludedRegexPattern
                                .matcher(head.getName())
                                .matches()) {
                    request.listener()
                            .getLogger()
                            .println("Include branch " + head.getName()
                                    + " because branch name matches always included pattern");
                    return false;
                }

                BitbucketSCMSourceRequest req = (BitbucketSCMSourceRequest) request;
                String fullName = req.getRepoOwner() + "/" + req.getRepository();
                try {
                    for (BitbucketPullRequest pullRequest : req.getPullRequests()) {
                        BitbucketRepository source = pullRequest.getSource().getRepository();
                        if (fullName.equalsIgnoreCase(source.getFullName())
                                && pullRequest.getSource().getBranch().getName().equals(head.getName())) {
                            return false;
                        }
                    }
                    request.listener()
                            .getLogger()
                            .println(
                                    "Discard branch " + head.getName()
                                            + " because current strategy excludes branches that are not also filed as a pull request");
                    return true;
                } catch (IOException | InterruptedException e) {
                    // should never happens because data in the requests has been already initialised
                    e.printStackTrace(request.listener().getLogger());
                }
            }
            return false;
        }
    }
}
