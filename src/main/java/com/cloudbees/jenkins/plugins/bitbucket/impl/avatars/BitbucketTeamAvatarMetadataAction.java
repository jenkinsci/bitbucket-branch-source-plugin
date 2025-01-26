/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket.impl.avatars;

import com.cloudbees.jenkins.plugins.bitbucket.Messages;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketApiUtils;
import hudson.Util;
import java.util.Objects;
import jenkins.scm.api.metadata.AvatarMetadataAction;
import jenkins.scm.impl.avatars.AvatarCache;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Invisible property that retains information about the Bitbucket team avatar.
 */
public class BitbucketTeamAvatarMetadataAction extends AvatarMetadataAction {
    private static final long serialVersionUID = -7472619697440514373L;

    private final String avatarURL;
    private final String serverURL;
    private final String credentialsId;
    private final String owner;

    @DataBoundConstructor
    public BitbucketTeamAvatarMetadataAction(String avatarURL, String serverURL, String owner, String credentialsId) {
        this.avatarURL = Util.fixEmptyAndTrim(avatarURL);
        this.serverURL = Util.fixEmptyAndTrim(serverURL);
        this.owner = Util.fixEmptyAndTrim(owner);
        this.credentialsId = Util.fixEmptyAndTrim(credentialsId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAvatarImageOf(String size) {
        if (avatarURL == null) {
            if (BitbucketApiUtils.isCloud(serverURL)) {
                // fall back to the generic bitbucket.org icon if no avatar provided
                return super.getAvatarImageOf(size);
            } else {
                // server logic
                return AvatarCache.buildUrl(new BitbucketServerAvatar(serverURL, owner, credentialsId), size);
            }
        } else {
            return cachedResizedImageOf(avatarURL, size);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAvatarIconClassName() {
        return avatarURL == null ? "icon-bitbucket-logo" : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAvatarDescription() {
        return Messages.BitbucketTeamMetadataAction_IconDescription();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        BitbucketTeamAvatarMetadataAction other = (BitbucketTeamAvatarMetadataAction) obj;
        return Objects.equals(avatarURL, other.avatarURL)
                && Objects.equals(serverURL, other.serverURL)
                && Objects.equals(owner, other.owner)
                && Objects.equals(credentialsId, other.credentialsId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(avatarURL, serverURL, owner, credentialsId);
    }
}
