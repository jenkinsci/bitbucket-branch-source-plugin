package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import java.io.IOException;
import java.util.Objects;
import java.util.StringJoiner;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMNavigatorOwner;
import jenkins.scm.api.metadata.AvatarMetadataAction;
import jenkins.scm.impl.avatars.AvatarCache;
import jenkins.scm.impl.avatars.AvatarImage;
import jenkins.scm.impl.avatars.AvatarImageSource;

public class BitbucketServerProjectAvatarAction extends AvatarMetadataAction implements AvatarImageSource {

    // This can change when SCMNavigatorOwner is moved but this Action is only persisted
    // when BitbucketSCMNavigator.retrieveActions which, at the moment of writing, happens on webhook events and indexing.
    // Hence, implementing ItemListener to monitor of location changes seems impractical.
    private String ownerFullName;
    private String serverUrl;
    private String credentialsId;
    private String projectKey;
    // owner can be moved around or credential can get blocked or revoked.
    private transient boolean canFetch = true;

    public BitbucketServerProjectAvatarAction(SCMNavigatorOwner owner, BitbucketSCMNavigator navigator) {
        this(owner.getFullName(), navigator.getServerUrl(), navigator.getCredentialsId(), navigator.getRepoOwner());
    }

    public BitbucketServerProjectAvatarAction(String ownerFullName, String serverUrl, String credentialsId, String projectKey) {
        this.ownerFullName = Util.fixEmpty(ownerFullName);
        this.serverUrl = Util.fixEmpty(serverUrl);
        this.credentialsId = Util.fixEmpty(credentialsId);
        this.projectKey = Util.fixEmpty(projectKey);
    }

    @Override
    public String getAvatarIconClassName() {
        if (!canFetch()) {
            return "icon-bitbucket-scm-navigator";
        }
        return null;
    }

    @Override
    public String getAvatarDescription() {
        return Messages.BitbucketServerProjectAvatarMetadataAction_IconDescription();
    }

    @Override
    public String getAvatarImageOf(@NonNull String size) {
        if (canFetch()) {
            return AvatarCache.buildUrl(this, size);
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BitbucketServerProjectAvatarAction that = (BitbucketServerProjectAvatarAction) o;
        return Objects.equals(serverUrl, that.serverUrl) && Objects.equals(credentialsId, that.credentialsId) && Objects.equals(projectKey, that.projectKey) && Objects.equals(ownerFullName, that.ownerFullName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverUrl, credentialsId, projectKey, ownerFullName);
    }

    @Override
    public AvatarImage fetch() {
        if (canFetch()) {
            return doFetch();
        }
        return null;
    }

    private AvatarImage doFetch() {
        SCMNavigatorOwner owner = Jenkins.get().getItemByFullName(ownerFullName, SCMNavigatorOwner.class);
        if (owner != null) {
            StandardCredentials credentials = BitbucketCredentials.lookupCredentials(
                serverUrl,
                owner,
                credentialsId,
                StandardCredentials.class
            );

            BitbucketAuthenticator authenticator = AuthenticationTokens.convert(BitbucketAuthenticator.authenticationContext(serverUrl), credentials);

            BitbucketApi bitbucket = BitbucketApiFactory.newInstance(serverUrl, authenticator, projectKey, null, null);
            try {
                return bitbucket.getTeamAvatar();
            } catch (IOException e) {
                canFetch = false;
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            // Owner was probably relocated
            canFetch = false;
        }
        return null;
    }

    @Override
    public String getId() {
        return new StringJoiner("::")
            .add(serverUrl)
            .add(credentialsId)
            .add(projectKey)
            .add(ownerFullName)
            .toString();
    }

    @Override
    public boolean canFetch() {
        return canFetch && ownerFullName != null && serverUrl != null && projectKey != null;
    }

}
