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
import com.cloudbees.jenkins.plugins.bitbucket.Messages;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMRevision;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.mixin.ChangeRequestSCMHead2;
import jenkins.scm.api.trait.SCMHeadAuthority;
import jenkins.scm.api.trait.SCMHeadAuthorityDescriptor;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.trait.Discovery;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A {@link Discovery} trait for bitbucket that will discover pull requests from forks of the repository.
 *
 * @since 2.2.0
 */
public class ForkPullRequestDiscoveryTrait extends SCMSourceTrait {
    /**
     * The strategy encoded as a bit-field.
     */
    private final int strategyId;
    /**
     * The authority.
     */
    @NonNull
    private final SCMHeadAuthority<? super BitbucketSCMSourceRequest, ? extends ChangeRequestSCMHead2, ? extends SCMRevision> trust;

    /**
     * Constructor for stapler.
     *
     * @param strategyId the strategy id.
     * @param trust      the authority to use.
     */
    @DataBoundConstructor
    public ForkPullRequestDiscoveryTrait(int strategyId,
                                         @NonNull BitbucketForkTrustPolicy trust) {
        this.strategyId = strategyId;
        this.trust = trust;
    }

    @Deprecated
    public ForkPullRequestDiscoveryTrait(int strategyId,
                                         @NonNull SCMHeadAuthority<? super BitbucketSCMSourceRequest, ? extends ChangeRequestSCMHead2, ? extends SCMRevision> trust) {
        this.strategyId = strategyId;
        this.trust = trust;
    }

    /**
     * Constructor for programmatic instantiation.
     *
     * @param strategies the {@link ChangeRequestCheckoutStrategy} instances.
     * @param trust      the authority.
     */
    public ForkPullRequestDiscoveryTrait(@NonNull Set<ChangeRequestCheckoutStrategy> strategies,
                                         @NonNull SCMHeadAuthority<? super BitbucketSCMSourceRequest, ? extends ChangeRequestSCMHead2, ? extends SCMRevision> trust) {
        this((strategies.contains(ChangeRequestCheckoutStrategy.MERGE) ? 1 : 0)
                + (strategies.contains(ChangeRequestCheckoutStrategy.HEAD) ? 2 : 0), trust);
    }

    /**
     * Gets the strategy id.
     *
     * @return the strategy id.
     */
    public int getStrategyId() {
        return strategyId;
    }

    /**
     * Returns the strategies.
     *
     * @return the strategies.
     */
    @NonNull
    public Set<ChangeRequestCheckoutStrategy> getStrategies() {
        switch (strategyId) {
            case 1:
                return EnumSet.of(ChangeRequestCheckoutStrategy.MERGE);
            case 2:
                return EnumSet.of(ChangeRequestCheckoutStrategy.HEAD);
            case 3:
                return EnumSet.of(ChangeRequestCheckoutStrategy.HEAD, ChangeRequestCheckoutStrategy.MERGE);
            default:
                return EnumSet.noneOf(ChangeRequestCheckoutStrategy.class);
        }
    }

    /**
     * Gets the authority.
     *
     * @return the authority.
     */
    @NonNull
    public SCMHeadAuthority<? super BitbucketSCMSourceRequest, ? extends ChangeRequestSCMHead2, ? extends SCMRevision> getTrust() {
        return trust;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        BitbucketSCMSourceContext ctx = (BitbucketSCMSourceContext) context;
        ctx.wantForkPRs(true);
        ctx.withAuthority(trust);
        ctx.withForkPRStrategies(getStrategies());
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
    @Symbol("bitbucketForkDiscovery")
    @Extension
    @Discovery
    public static class DescriptorImpl extends BitbucketSCMSourceTraitDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.ForkPullRequestDiscoveryTrait_displayName();
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
            result.add(Messages.ForkPullRequestDiscoveryTrait_mergeOnly(), "1");
            result.add(Messages.ForkPullRequestDiscoveryTrait_headOnly(), "2");
            result.add(Messages.ForkPullRequestDiscoveryTrait_headAndMerge(), "3");
            return result;
        }

        /**
         * Returns the list of appropriate {@link SCMHeadAuthorityDescriptor} instances.
         *
         * @return the list of appropriate {@link SCMHeadAuthorityDescriptor} instances.
         */
        @SuppressWarnings("unchecked")
        @NonNull
        public List<SCMHeadAuthorityDescriptor> getTrustDescriptors() {
            return SCMHeadAuthority._for(
                    BitbucketSCMSourceRequest.class,
                    PullRequestSCMHead.class,
                    PullRequestSCMRevision.class,
                    SCMHeadOrigin.Fork.class
            );
        }

        /**
         * Returns the default trust for new instances of {@link ForkPullRequestDiscoveryTrait}.
         *
         * @return the default trust for new instances of {@link ForkPullRequestDiscoveryTrait}.
         */
        @NonNull
        public SCMHeadAuthority<?, ?, ?> getDefaultTrust() {
            return new TrustTeamForks();
        }
    }

    /**
     * Trust policy for forked pull requests.
     * <p>
     * This reduces generics in the databound constructor method signature as a workaround for JENKINS-26535
     */
    public static abstract class BitbucketForkTrustPolicy extends SCMHeadAuthority<BitbucketSCMSourceRequest, PullRequestSCMHead, PullRequestSCMRevision> {
    }

    /**
     * An {@link SCMHeadAuthority} that trusts nothing.
     */
    public static class TrustNobody extends BitbucketForkTrustPolicy {

        /**
         * Constructor.
         */
        @DataBoundConstructor
        public TrustNobody() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean checkTrusted(@NonNull BitbucketSCMSourceRequest request, @NonNull PullRequestSCMHead head) {
            return false;
        }

        /**
         * Our descriptor.
         */
        @Symbol("bitbucketTrustNobody")
        @Extension
        public static class DescriptorImpl extends SCMHeadAuthorityDescriptor {

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.ForkPullRequestDiscoveryTrait_nobodyDisplayName();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isApplicableToOrigin(@NonNull Class<? extends SCMHeadOrigin> originClass) {
                return SCMHeadOrigin.Fork.class.isAssignableFrom(originClass);
            }
        }
    }

    /**
     * An {@link SCMHeadAuthority} that trusts forks belonging to the same account.
     */
    public static class TrustTeamForks extends BitbucketForkTrustPolicy {

        /**
         * Constructor.
         */
        @DataBoundConstructor
        public TrustTeamForks() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean checkTrusted(@NonNull BitbucketSCMSourceRequest request, @NonNull PullRequestSCMHead head)
                throws IOException, InterruptedException {
            if (!head.getOrigin().equals(SCMHeadOrigin.DEFAULT)) {
                return StringUtils.equalsIgnoreCase(head.getRepoOwner(), request.getRepoOwner());
            }
            return false;
        }

        /**
         * Our descriptor.
         */
        @Symbol({"bitbucketTrustTeam", "bitbucketTrustProject"})
        @Extension
        public static class DescriptorImpl extends SCMHeadAuthorityDescriptor {

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.ForkPullRequestDiscoveryTrait_teamDisplayName();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isApplicableToOrigin(@NonNull Class<? extends SCMHeadOrigin> originClass) {
                return SCMHeadOrigin.Fork.class.isAssignableFrom(originClass);
            }
        }
    }

    /**
     * An {@link SCMHeadAuthority} that trusts everyone.
     */
    public static class TrustEveryone extends BitbucketForkTrustPolicy {
        /**
         * Constructor.
         */
        @DataBoundConstructor
        public TrustEveryone() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean checkTrusted(@NonNull BitbucketSCMSourceRequest request, @NonNull PullRequestSCMHead head) {
            return true;
        }

        /**
         * Our descriptor.
         */
        @Symbol("bitbucketTrustEveryone")
        @Extension
        public static class DescriptorImpl extends SCMHeadAuthorityDescriptor {

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.ForkPullRequestDiscoveryTrait_everyoneDisplayName();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isApplicableToOrigin(@NonNull Class<? extends SCMHeadOrigin> originClass) {
                return SCMHeadOrigin.Fork.class.isAssignableFrom(originClass);
            }
        }
    }
}
