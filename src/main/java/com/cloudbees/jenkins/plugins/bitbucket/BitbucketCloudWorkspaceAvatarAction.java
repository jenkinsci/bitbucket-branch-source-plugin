package com.cloudbees.jenkins.plugins.bitbucket;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import jenkins.scm.api.metadata.AvatarMetadataAction;

/**
 * Avatar link returned by <a href="https://developer.atlassian.com/cloud/bitbucket/rest/api-group-workspaces/#api-workspaces-workspace-get">Get Workspace</a> Bitbucket REST API is public at the moment of writing. Hence, reusing using SCM API plugin provided APIs.
 */
public class BitbucketCloudWorkspaceAvatarAction extends AvatarMetadataAction {
    @CheckForNull
    private String avatar;

    public BitbucketCloudWorkspaceAvatarAction(@CheckForNull String avatar) {
        this.avatar = avatar;
    }

    @Override
    public String getAvatarIconClassName() {
        if (avatar == null) {
            return "icon-bitbucket-scm-navigator";
        }
        return null;
    }

    @Override
    public String getAvatarDescription() {
        return Messages.BitbucketCloudWorkspaceAvatarMetadataAction_IconDescription();
    }

    @Override
    public String getAvatarImageOf(@NonNull String size) {
        if (avatar != null) {
            return cachedResizedImageOf(avatar, size);
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BitbucketCloudWorkspaceAvatarAction that = (BitbucketCloudWorkspaceAvatarAction) o;

        return Objects.equals(avatar, that.avatar);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(avatar);
    }

    @Override
    public String toString() {
        return "BitbucketCloudWorkspaceAvatarAction{" +
            "avatar='" + avatar + '\'' +
            '}';
    }
}
