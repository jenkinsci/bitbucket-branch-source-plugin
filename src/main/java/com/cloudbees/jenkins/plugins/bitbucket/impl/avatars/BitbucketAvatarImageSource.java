package com.cloudbees.jenkins.plugins.bitbucket.impl.avatars;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketApiUtils;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.scm.impl.avatars.AvatarImage;
import jenkins.scm.impl.avatars.AvatarImageSource;

public class BitbucketAvatarImageSource implements AvatarImageSource {
    private static final Logger logger = Logger.getLogger(BitbucketAvatarImageSource.class.getName());

    private final String avatarURL;
    private final String serverURL;
    private final String owner;
    private final String credentialsId;
    private transient boolean fetchFailed = false; // NOSONAR, class not implements Serializable but the AvatarCache(.cache) is an action that should be persisted

    public BitbucketAvatarImageSource(String serverURL, String owner, String credentialsId) {
        this.serverURL = serverURL;
        this.owner = owner;
        this.credentialsId = credentialsId;
        this.avatarURL = null;
    }

    public BitbucketAvatarImageSource(String avatarURL, String credentialsId) {
        this.avatarURL = avatarURL;
        this.credentialsId = credentialsId;
        this.serverURL = null;
        this.owner = null;
    }

    @Override
    public AvatarImage fetch() {
        try {
            if (canFetch()) {
                StandardCredentials credentials = BitbucketCredentials.lookupCredentials(serverURL, null /* what if credentials are stored in the organisation folder?*/, credentialsId, StandardCredentials.class);
                BitbucketAuthenticator authenticator = AuthenticationTokens.convert(BitbucketAuthenticator.authenticationContext(serverURL), credentials);
                // projectKey and repository are not used to get the project avatar
                try (BitbucketApi client = BitbucketApiFactory.newInstance(serverURL, authenticator, owner, null, null)) {
                    if (BitbucketApiUtils.isCloud(serverURL)) {
                        return ((BitbucketCloudApiClient) client).getProjectAvatar(avatarURL);
                    } else {
                        return client.getTeamAvatar();
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, e, () -> "Fail to fetch avatar image for " + serverURL + " and owner " + owner + " using credentialsId " + credentialsId);
            fetchFailed = true; // do not retry with same serverURL/credentialsId until Jenkins restarts
        }
        return AvatarImage.EMPTY;
    }

    @Override
    public String getId() {
        return "" + credentialsId + "@" + serverURL + "/" + owner;
    }

    @Override
    public boolean canFetch() {
        return !fetchFailed && serverURL != null && credentialsId != null; // is credentialsId valid??
    }

}
