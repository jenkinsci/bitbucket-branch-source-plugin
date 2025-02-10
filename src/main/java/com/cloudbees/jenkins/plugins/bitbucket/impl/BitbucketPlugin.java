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
        Items.XSTREAM2.addCompatibilityAlias("com.cloudbees.jenkins.plugins.bitbucket.BitbucketTeamMetadataAction", BitbucketTeamAvatarMetadataAction.class);
        Items.XSTREAM2.addCompatibilityAlias("com.cloudbees.jenkins.plugins.bitbucket.BitbucketRepoMetadataAction", BitbucketRepoAvatarMetadataAction.class);
    }

}
