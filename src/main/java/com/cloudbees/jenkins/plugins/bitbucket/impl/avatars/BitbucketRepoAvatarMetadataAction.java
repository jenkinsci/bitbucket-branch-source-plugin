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
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Util;
import java.util.Objects;
import jenkins.scm.api.metadata.AvatarMetadataAction;
import jenkins.scm.impl.avatars.AvatarCache;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Invisible property that retains information about Bitbucket repository.
 */
public class BitbucketRepoAvatarMetadataAction extends AvatarMetadataAction {
    private static final long serialVersionUID = 6159334180425135341L;

    private final String scm;
    private String avatarURL;

    public BitbucketRepoAvatarMetadataAction(@CheckForNull BitbucketRepository repo) {
        this("git");
        if (repo != null) {
            this.avatarURL = repo.getAvatar();
        }
    }

    @DataBoundConstructor
    public BitbucketRepoAvatarMetadataAction(String scm) {
        this.scm = scm;
    }

    public String getScm() {
        return scm;
    }

    public String getAvatarURL() {
        return avatarURL;
    }

    @DataBoundSetter
    public void setAvatarURL(String avatarURL) {
        this.avatarURL = Util.fixEmptyAndTrim(avatarURL);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAvatarImageOf(String size) {
        if (avatarURL == null) {
            return super.getAvatarImageOf(size);
        } else {
            return AvatarCache.buildUrl(avatarURL, size);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAvatarIconClassName() {
        if (avatarURL != null) {
            return null; // trigger #getAvatarImageOf(String) if this class override #getAvatarImageOf(String)
        }
        if ("git".equals(scm)) {
            return "icon-bitbucket-repo-git";
        }
        return "icon-bitbucket-repo";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAvatarDescription() {
        if ("git".equals(scm)) {
            return Messages.BitbucketRepoMetadataAction_IconDescription_Git();
        }
        return Messages.BitbucketRepoMetadataAction_IconDescription();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BitbucketRepoAvatarMetadataAction that = (BitbucketRepoAvatarMetadataAction) o;
        return Objects.equals(scm, that.scm)
                && Objects.equals(avatarURL, that.avatarURL);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scm, avatarURL);
    }

}
