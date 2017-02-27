/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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
import jenkins.scm.api.metadata.AvatarMetadataAction;

/**
 * Invisible property that retains information about Bitbucket team.
 */
public class BitbucketTeamMetadataAction extends AvatarMetadataAction {
    @CheckForNull
    private final String avatarUrl;

    public BitbucketTeamMetadataAction(@CheckForNull String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

// TODO when bitbucket supports serving avatars with a size request - currently only works if using gravatar or server
//    /**
//     * {@inheritDoc}
//     */
//    @Override
//    public String getAvatarImageOf(String size) {
//        if (avatarUrl == null) {
//            // fall back to the generic github org icon
//            String image = avatarIconClassNameImageOf(getAvatarIconClassName(), size);
//            return image != null
//                    ? image
//                    : (Stapler.getCurrentRequest().getContextPath() + Jenkins.RESOURCE_PATH
//                            + "/plugin/cloudbees-bitbucket-branch-source/images/" + size + "/bitbucket-logo.png");
//        } else {
//            String[] xy = size.split("x");
//            if (xy.length == 0) return avatarUrl;
//            if (avatarUrl.contains("?")) return avatarUrl + "&s=" + xy[0];
//            else return avatarUrl + "?s=" + xy[0];
//        }
//    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getAvatarIconClassName() {
        // TODO when bitbucket supports serving avatars with a size request
        // return avatarUrl == null ? "icon-bitbucket-logo" : null;
        return "icon-bitbucket-logo";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAvatarDescription() {
        return Messages.BitbucketTeamMetadataAction_IconDescription();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BitbucketTeamMetadataAction that = (BitbucketTeamMetadataAction) o;

        if (avatarUrl != null ? !avatarUrl.equals(that.avatarUrl) : that.avatarUrl != null) {
            return false;
        }
        return true;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return (avatarUrl != null ? avatarUrl.hashCode() : 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "BitbucketTeamMetadataAction{" +
                ", avatarUrl='" + avatarUrl + '\'' +
                '}';
    }
}
