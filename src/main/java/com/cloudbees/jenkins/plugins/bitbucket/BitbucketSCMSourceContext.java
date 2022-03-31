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
package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.hooks.WebhookConfiguration;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.TaskListener;
import java.util.EnumSet;
import java.util.Set;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMSourceContext;

/**
 * The {@link SCMSourceContext} for bitbucket.
 *
 * @since 2.2.0
 */
public class BitbucketSCMSourceContext extends SCMSourceContext<BitbucketSCMSourceContext, BitbucketSCMSourceRequest> {
    /**
     * {@code true} if the {@link BitbucketSCMSourceRequest} will need information about branches.
     */
    private boolean wantBranches;
    /**
     * {@code true} if the {@link BitbucketSCMSourceRequest} will need information about tags.
     */
    private boolean wantTags;
    /**
     * {@code true} if the {@link BitbucketSCMSourceRequest} will need information about origin pull requests.
     */
    private boolean wantOriginPRs;
    /**
     * {@code true} if the {@link BitbucketSCMSourceRequest} will need information about fork pull requests.
     */
    private boolean wantForkPRs;
    /**
     * {@code true} if all pull requests from public repositories should be ignored.
     */
    private boolean skipPublicPRs;
    /**
     * Set of {@link ChangeRequestCheckoutStrategy} to create for each origin pull request.
     */
    @NonNull
    private final Set<ChangeRequestCheckoutStrategy> originPRStrategies = EnumSet.noneOf(ChangeRequestCheckoutStrategy.class);
    /**
     * Set of {@link ChangeRequestCheckoutStrategy} to create for each fork pull request.
     */
    @NonNull
    private final Set<ChangeRequestCheckoutStrategy> forkPRStrategies = EnumSet.noneOf(ChangeRequestCheckoutStrategy.class);
    /**
     * The {@link WebhookRegistration} to use in this context.
     */
    @NonNull
    private WebhookRegistration webhookRegistration = WebhookRegistration.SYSTEM;
    /**
     * The {@link WebhookConfiguration} to use in this context.
     */
    @NonNull
    private WebhookConfiguration webhookConfiguration = new WebhookConfiguration();
    /**
     * {@code true} if notifications should be disabled in this context.
     */
    private boolean notificationsDisabled;

    /**
     * {@code true} if unstable builds should be considered as successful by Bitbucket.
     */
    private boolean sendSuccessNotificationForUnstableBuild;

    /**
     * {@code false} if not build jobs should be send to Bitbucket.
     */
    private boolean disableNotificationForNotBuildJobs;

    /**
     * Constructor.
     *
     * @param criteria (optional) criteria.
     * @param observer the {@link SCMHeadObserver}.
     */
    public BitbucketSCMSourceContext(@CheckForNull SCMSourceCriteria criteria,
                                     @NonNull SCMHeadObserver observer) {
        super(criteria, observer);
    }

    /**
     * Returns {@code true} if the {@link BitbucketSCMSourceRequest} will need information about branches.
     *
     * @return {@code true} if the {@link BitbucketSCMSourceRequest} will need information about branches.
     */
    public final boolean wantBranches() {
        return wantBranches;
    }

    /**
     * Returns {@code true} if the {@link BitbucketSCMSourceRequest} will need information about tags.
     *
     * @return {@code true} if the {@link BitbucketSCMSourceRequest} will need information about tags.
     */
    public final boolean wantTags() {
        return wantTags;
    }

    /**
     * Returns {@code true} if the {@link BitbucketSCMSourceRequest} will need information about pull requests.
     *
     * @return {@code true} if the {@link BitbucketSCMSourceRequest} will need information about pull requests.
     */
    public final boolean wantPRs() {
        return wantOriginPRs || wantForkPRs;
    }

    /**
     * Returns {@code true} if the {@link BitbucketSCMSourceRequest} will need information about origin pull requests.
     *
     * @return {@code true} if the {@link BitbucketSCMSourceRequest} will need information about origin pull requests.
     */
    public final boolean wantOriginPRs() {
        return wantOriginPRs;
    }

    /**
     * Returns {@code true} if the {@link BitbucketSCMSourceRequest} will need information about fork pull requests.
     *
     * @return {@code true} if the {@link BitbucketSCMSourceRequest} will need information about fork pull requests.
     */
    public final boolean wantForkPRs() {
        return wantForkPRs;
    }

    /**
     * Returns {@code true} if pull requests from public repositories should be skipped.
     *
     * @return {@code true} if pull requests from public repositories should be skipped.
     */
    public final boolean skipPublicPRs() {
        return skipPublicPRs;
    }

    /**
     * Returns the set of {@link ChangeRequestCheckoutStrategy} to create for each origin pull request.
     *
     * @return the set of {@link ChangeRequestCheckoutStrategy} to create for each origin pull request.
     */
    @NonNull
    public final Set<ChangeRequestCheckoutStrategy> originPRStrategies() {
        return originPRStrategies;
    }

    /**
     * Returns the set of {@link ChangeRequestCheckoutStrategy} to create for each fork pull request.
     *
     * @return the set of {@link ChangeRequestCheckoutStrategy} to create for each fork pull request.
     */
    @NonNull
    public final Set<ChangeRequestCheckoutStrategy> forkPRStrategies() {
        return forkPRStrategies;
    }

    /**
     * Returns the {@link WebhookRegistration} mode.
     *
     * @return the {@link WebhookRegistration} mode.
     */
    @NonNull
    public final WebhookRegistration webhookRegistration() {
        return webhookRegistration;
    }

    /**
     * Returns the {@link WebhookConfiguration} configuration.
     *
     * @return the {@link WebhookConfiguration} configuration.
     */
    @NonNull
    public final WebhookConfiguration webhookConfiguration() {
        return webhookConfiguration;
    }

    /**
     * Returns {@code true} if notifications should be disabled.
     *
     * @return {@code true} if notifications should be disabled.
     */
    public final boolean notificationsDisabled() {
        return notificationsDisabled;

    }

    /**
     * Returns {@code true} if unstable builds should be passed as successful to Bitbucket.
     *
     * @return {@code true} if unstable builds should be passed as successful to Bitbucket.
     */
    public final boolean sendSuccessNotificationForUnstableBuild() {
        return sendSuccessNotificationForUnstableBuild;
    }

    /**
     * Returns {@code false} if not build jobs should be passed to Bitbucket.
     *
     * @return {@code false} if not build jobs should be passed to Bitbucket.
     */
    public boolean disableNotificationForNotBuildJobs() {
        return disableNotificationForNotBuildJobs;
    }

    /**
     * Adds a requirement for branch details to any {@link BitbucketSCMSourceRequest} for this context.
     *
     * @param include {@code true} to add the requirement or {@code false} to leave the requirement as is (makes
     *                simpler with method chaining)
     * @return {@code this} for method chaining.
     */
    @NonNull
    public final BitbucketSCMSourceContext wantBranches(boolean include) {
        wantBranches = wantBranches || include;
        return this;
    }

    /**
     * Adds a requirement for tag details to any {@link BitbucketSCMSourceRequest} for this context.
     *
     * @param include {@code true} to add the requirement or {@code false} to leave the requirement as is (makes
     *                simpler with method chaining)
     * @return {@code this} for method chaining.
     */
    @NonNull
    public final BitbucketSCMSourceContext wantTags(boolean include) {
        wantTags = wantTags || include;
        return this;
    }

    /**
     * Adds a requirement for origin pull request details to any {@link BitbucketSCMSourceRequest} for this context.
     *
     * @param include {@code true} to add the requirement or {@code false} to leave the requirement as is (makes
     *                simpler with method chaining)
     * @return {@code this} for method chaining.
     */
    @NonNull
    public final BitbucketSCMSourceContext wantOriginPRs(boolean include) {
        wantOriginPRs = wantOriginPRs || include;
        return this;
    }

    /**
     * Adds a requirement for fork pull request details to any {@link BitbucketSCMSourceRequest} for this context.
     *
     * @param include {@code true} to add the requirement or {@code false} to leave the requirement as is (makes
     *                simpler with method chaining)
     * @return {@code this} for method chaining.
     */
    @NonNull
    public final BitbucketSCMSourceContext wantForkPRs(boolean include) {
        wantForkPRs = wantForkPRs || include;
        return this;
    }

    /**
     * Controls the skipping of pull requests from public repositories.
     *
     * @param skipPublicPRs {@code true} if pull requests from public repositories should be skipped.
     * @return {@code this} for method chaining.
     */
    public final BitbucketSCMSourceContext skipPublicPRs(boolean skipPublicPRs) {
        this.skipPublicPRs = skipPublicPRs;
        return this;
    }

    /**
     * Defines the {@link ChangeRequestCheckoutStrategy} instances to create for each origin pull request.
     *
     * @param strategies the strategies.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public final BitbucketSCMSourceContext withOriginPRStrategies(
            @NonNull Set<ChangeRequestCheckoutStrategy> strategies) {
        originPRStrategies.addAll(strategies);
        return this;
    }

    /**
     * Defines the {@link ChangeRequestCheckoutStrategy} instances to create for each fork pull request.
     *
     * @param strategies the strategies.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public final BitbucketSCMSourceContext withForkPRStrategies(
            @NonNull Set<ChangeRequestCheckoutStrategy> strategies) {
        forkPRStrategies.addAll(strategies);
        return this;
    }

    /**
     * Defines the {@link WebhookRegistration} mode to use in this context.
     *
     * @param configuration the webhook configuration.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public final BitbucketSCMSourceContext webhookConfiguration(WebhookConfiguration configuration) {
        webhookConfiguration = configuration;
        return this;
    }

    /**
     * Defines the {@link WebhookRegistration} mode to use in this context.
     *
     * @param mode the mode.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public final BitbucketSCMSourceContext webhookRegistration(WebhookRegistration mode) {
        webhookRegistration = mode;
        return this;
    }

    /**
     * Defines the notification mode to use in this context.
     *
     * @param disabled {@code true} to disable automatic notifications.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public final BitbucketSCMSourceContext withNotificationsDisabled(boolean disabled) {
        this.notificationsDisabled = disabled;
        return this;
    }

    /**
     * Defines behaviour of unstable builds in Bitbucket.
     *
     * @param isUnstableBuildSuccess {@code true} to consider unstable builds successful when notifying Bitbucket.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public final BitbucketSCMSourceContext withSendSuccessNotificationForUnstableBuild(boolean isUnstableBuildSuccess) {
        this.sendSuccessNotificationForUnstableBuild = isUnstableBuildSuccess;
        return this;
    }

    /**
     * Defines behaviour of not-built jobs in Bitbucket.
     *
     * @param disabled {@code false} to report not-built jobs to Bitbucket.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public final BitbucketSCMSourceContext withDisableNotificationForNotBuildJobs(boolean disabled) {
        this.disableNotificationForNotBuildJobs = disabled;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public BitbucketSCMSourceRequest newRequest(@NonNull SCMSource scmSource, TaskListener taskListener) {
        return new BitbucketSCMSourceRequest((BitbucketSCMSource) scmSource, this, taskListener);
    }
}
