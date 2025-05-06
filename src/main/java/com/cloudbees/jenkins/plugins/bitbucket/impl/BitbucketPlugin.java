/*
 * The MIT License
 *
 * Copyright (c) 2025, Nikolas Falco
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
package com.cloudbees.jenkins.plugins.bitbucket.impl;

import com.cloudbees.jenkins.plugins.bitbucket.impl.avatars.BitbucketRepoAvatarMetadataAction;
import com.cloudbees.jenkins.plugins.bitbucket.impl.avatars.BitbucketTeamAvatarMetadataAction;
import com.cloudbees.jenkins.plugins.bitbucket.impl.extension.FallbackToOtherRepositoryGitSCMExtension;
import com.cloudbees.jenkins.plugins.bitbucket.trait.BitbucketBuildStatusNotificationsTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.BranchDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.BranchDiscoveryTrait.BranchSCMHeadAuthority;
import com.cloudbees.jenkins.plugins.bitbucket.trait.BranchDiscoveryTrait.ExcludeOriginPRBranchesSCMHeadFilter;
import com.cloudbees.jenkins.plugins.bitbucket.trait.BranchDiscoveryTrait.OnlyOriginPRBranchesSCMHeadFilter;
import com.cloudbees.jenkins.plugins.bitbucket.trait.ForkPullRequestDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.ForkPullRequestDiscoveryTrait.TrustEveryone;
import com.cloudbees.jenkins.plugins.bitbucket.trait.ForkPullRequestDiscoveryTrait.TrustNobody;
import com.cloudbees.jenkins.plugins.bitbucket.trait.ForkPullRequestDiscoveryTrait.TrustTeamForks;
import com.cloudbees.jenkins.plugins.bitbucket.trait.OriginPullRequestDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.OriginPullRequestDiscoveryTrait.OriginChangeRequestSCMHeadAuthority;
import com.cloudbees.jenkins.plugins.bitbucket.trait.PublicRepoPullRequestFilterTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.SSHCheckoutTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.TagDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.TagDiscoveryTrait.TagSCMHeadAuthority;
import com.cloudbees.jenkins.plugins.bitbucket.trait.WebhookConfigurationTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.WebhookRegistrationTrait;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Items;
import jenkins.plugins.git.MergeWithGitSCMExtension;

public class BitbucketPlugin {

    /**
     * Mapping classes after refactoring for backward compatibility.
     */
    @Initializer(before = InitMilestone.PLUGINS_STARTED)
    public static void aliases() {
        Items.XSTREAM2.addCompatibilityAlias("com.cloudbees.jenkins.plugins.bitbucket.MergeWithGitSCMExtension", MergeWithGitSCMExtension.class);
        Items.XSTREAM2.addCompatibilityAlias("com.cloudbees.jenkins.plugins.bitbucket.FallbackToOtherRepositoryGitSCMExtension", FallbackToOtherRepositoryGitSCMExtension.class);
        Items.XSTREAM2.addCompatibilityAlias("com.cloudbees.jenkins.plugins.bitbucket.BitbucketTeamMetadataAction", BitbucketTeamAvatarMetadataAction.class);
        Items.XSTREAM2.addCompatibilityAlias("com.cloudbees.jenkins.plugins.bitbucket.BitbucketRepoMetadataAction", BitbucketRepoAvatarMetadataAction.class);
        Items.XSTREAM2.addCompatibilityAlias("com.cloudbees.jenkins.plugins.bitbucket.BitbucketBuildStatusNotificationsTrait", BitbucketBuildStatusNotificationsTrait.class);
        Items.XSTREAM2.addCompatibilityAlias("com.cloudbees.jenkins.plugins.bitbucket.BranchDiscoveryTrait", BranchDiscoveryTrait.class);
        Items.XSTREAM2.addCompatibilityAlias("com.cloudbees.jenkins.plugins.bitbucket.BranchDiscoveryTrait$BranchSCMHeadAuthority", BranchSCMHeadAuthority.class);
        Items.XSTREAM2.addCompatibilityAlias("com.cloudbees.jenkins.plugins.bitbucket.BranchDiscoveryTrait$ExcludeOriginPRBranchesSCMHeadFilter", ExcludeOriginPRBranchesSCMHeadFilter.class);
        Items.XSTREAM2.addCompatibilityAlias("com.cloudbees.jenkins.plugins.bitbucket.BranchDiscoveryTrait$OnlyOriginPRBranchesSCMHeadFilter", OnlyOriginPRBranchesSCMHeadFilter.class);
        Items.XSTREAM2.addCompatibilityAlias("com.cloudbees.jenkins.plugins.bitbucket.ForkPullRequestDiscoveryTrait", ForkPullRequestDiscoveryTrait.class);
        Items.XSTREAM2.addCompatibilityAlias("com.cloudbees.jenkins.plugins.bitbucket.ForkPullRequestDiscoveryTrait$TrustEveryone", TrustEveryone.class);
        Items.XSTREAM2.addCompatibilityAlias("com.cloudbees.jenkins.plugins.bitbucket.ForkPullRequestDiscoveryTrait$TrustNobody", TrustNobody.class);
        Items.XSTREAM2.addCompatibilityAlias("com.cloudbees.jenkins.plugins.bitbucket.ForkPullRequestDiscoveryTrait$TrustTeamForks", TrustTeamForks.class);
        Items.XSTREAM2.addCompatibilityAlias("com.cloudbees.jenkins.plugins.bitbucket.OriginPullRequestDiscoveryTrait", OriginPullRequestDiscoveryTrait.class);
        Items.XSTREAM2.addCompatibilityAlias("com.cloudbees.jenkins.plugins.bitbucket.OriginPullRequestDiscoveryTrait$OriginChangeRequestSCMHeadAuthority", OriginChangeRequestSCMHeadAuthority.class);
        Items.XSTREAM2.addCompatibilityAlias("com.cloudbees.jenkins.plugins.bitbucket.PublicRepoPullRequestFilterTrait", PublicRepoPullRequestFilterTrait.class);
        Items.XSTREAM2.addCompatibilityAlias("com.cloudbees.jenkins.plugins.bitbucket.SSHCheckoutTrait", SSHCheckoutTrait.class);
        Items.XSTREAM2.addCompatibilityAlias("com.cloudbees.jenkins.plugins.bitbucket.TagDiscoveryTrait", TagDiscoveryTrait.class);
        Items.XSTREAM2.addCompatibilityAlias("com.cloudbees.jenkins.plugins.bitbucket.TagDiscoveryTrait$TagSCMHeadAuthority", TagSCMHeadAuthority.class);
        Items.XSTREAM2.addCompatibilityAlias("com.cloudbees.jenkins.plugins.bitbucket.WebhookConfigurationTrait", WebhookConfigurationTrait.class);
        Items.XSTREAM2.addCompatibilityAlias("com.cloudbees.jenkins.plugins.bitbucket.WebhookRegistrationTrait", WebhookRegistrationTrait.class);
    }

}
