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
package com.cloudbees.jenkins.plugins.bitbucket.impl.avatars;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMNavigatorOwner;
import jenkins.scm.impl.avatars.AvatarImage;
import jenkins.scm.impl.avatars.AvatarImageSource;

public class BitbucketAvatarImageSource implements AvatarImageSource {
    private static final Logger logger = Logger.getLogger(BitbucketAvatarImageSource.class.getName());

    private final String avatarURL;
    private final String serverURL;
    private final String credentialsId;
    private final String scmOwner;
    private transient boolean fetchFailed = false; // NOSONAR, class not implements Serializable but the AvatarCache(.cache) is an action that should be persisted


    public BitbucketAvatarImageSource(@NonNull String avatarURL, @NonNull String serverURL, @NonNull String scmOwner, @Nullable String credentialsId) {
        this.avatarURL = avatarURL;
        this.serverURL = serverURL;
        this.scmOwner = scmOwner;
        this.credentialsId = credentialsId;
    }

    @Override
    public AvatarImage fetch() {
        try {
            if (canFetch()) {
                SCMNavigatorOwner owner = Jenkins.get().getItemByFullName(scmOwner, SCMNavigatorOwner.class);
                if (owner != null) {
                    StandardCredentials credentials = BitbucketCredentials.lookupCredentials(serverURL, owner, credentialsId, StandardCredentials.class);
                    BitbucketAuthenticator authenticator = AuthenticationTokens.convert(BitbucketAuthenticator.authenticationContext(serverURL), credentials);
                    // projectKey and repository are not used to fetch the project avatar
                    // owner can not be null but is not used from the client to retrieve avatar image, we just need authentication
                    try (BitbucketApi client = BitbucketApiFactory.newInstance(serverURL, authenticator, "tmp", null, null)) {
                        return client.getAvatar(avatarURL);
                    }
                } else {
                    logger.log(Level.WARNING, "Item {0} seems to be relocated, perform a 'Scan project Now' action to refresh old data", new Object[] { scmOwner });
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, e, () -> "Fail to fetch avatar image for " + serverURL + " using credentialsId " + credentialsId);
            fetchFailed = true; // do not retry with same serverURL/credentialsId until Jenkins restarts
        }
        return AvatarImage.EMPTY;
    }

    @Override
    public String getId() {
        return credentialsId + "@" + avatarURL;
    }

    @Override
    public boolean canFetch() {
        return !fetchFailed && avatarURL != null && serverURL != null;
    }

}
