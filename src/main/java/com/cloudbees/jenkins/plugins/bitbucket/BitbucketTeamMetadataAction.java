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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRequestException;
import com.cloudbees.jenkins.plugins.bitbucket.avatars.AvatarCache;
import com.cloudbees.jenkins.plugins.bitbucket.avatars.AvatarCacheSource;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.security.ACLContext;
import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.scm.api.metadata.AvatarMetadataAction;

/**
 * Invisible property that retains information about Bitbucket team.
 */
public class BitbucketTeamMetadataAction extends AvatarMetadataAction {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(BitbucketTeamMetadataAction.class.getName());

    private final BitbucketAvatarCacheSource avatarSource;

    public BitbucketTeamMetadataAction(String serverUrl, StandardCredentials credentials, String team) {
        avatarSource = new BitbucketAvatarCacheSource(serverUrl, credentials, team);
    }

    public static class BitbucketAvatarCacheSource implements AvatarCacheSource, Serializable {
        private static final long serialVersionUID = 1L;
        private final String serverUrl;
        private StandardCredentials credentials;
        private final String repoOwner;

        public BitbucketAvatarCacheSource(String serverUrl, StandardCredentials credentials, String repoOwner) {
            this.serverUrl = serverUrl;
            this.credentials = credentials;
            this.repoOwner = repoOwner;
            LOGGER.log(Level.INFO, "Created: {0}", this.toString());
        }

        @Override
        public AvatarImage fetch(StandardCredentials credentials) {
            try {
                return doFetch(credentials);
            } catch (IOException e) {
                if (e.getCause() instanceof BitbucketRequestException) {
                    BitbucketRequestException bre = (BitbucketRequestException) e.getCause();
                    if (bre.getHttpCode()==401) {
                        // credentials not updated here maybe we need to refresh them
                        StandardCredentials standardCredentials = findCredentials();
                        // try again with refreshed credentials
                        // TODO compare previous and new token if we really need to try again
                        if (standardCredentials != null) {
                            this.credentials = standardCredentials;
                            try {
                                return doFetch(this.credentials);
                            } catch (IOException | InterruptedException ex) {
                                LOGGER.log(Level.INFO, ex.getClass().getName()+": " + e.getMessage(), e);
                            }
                        }
                    }
                }
                LOGGER.log(Level.INFO, "IOException: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                LOGGER.log(Level.INFO, "InterruptedException: " + e.getMessage(), e);
            }
            return null;
        }

        private StandardCredentials findCredentials() {
            try (ACLContext ctx = ACL.as(ACL.SYSTEM)) {
                return CredentialsMatchers.firstOrNull(
                        CredentialsProvider.lookupCredentials(
                                credentials.getClass(),
                                (Item) null, // context
                                ACL.SYSTEM,
                                URIRequirementBuilder.fromUri(serverUrl).build()
                        ),
                        CredentialsMatchers.allOf(
                                CredentialsMatchers.withId(credentials.getId()),
                                CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(credentials.getClass()))
                        )
                );
            }
        }

        private AvatarImage doFetch(StandardCredentials credentials) throws IOException, InterruptedException {
            BitbucketAuthenticator authenticator = AuthenticationTokens
                    .convert(BitbucketAuthenticator.authenticationContext(serverUrl), credentials);
            BitbucketApi bitbucket = BitbucketApiFactory.newInstance(serverUrl, authenticator, repoOwner, null);
            return bitbucket.getTeamAvatar();
        }

        @Override
        public AvatarImage fetch() {
            if(this.credentials==null) {
                throw new UnsupportedOperationException("this method can be used only with credentials");
            }
            return this.fetch(this.credentials);
        }

        @Override
        public String hashKey() {
            return "" + serverUrl + "::" + repoOwner + "::" + (credentials != null ? credentials.getId() : "");
        }

        @Override
        public boolean canFetch() {
            return (serverUrl != null && repoOwner != null && !serverUrl.trim().isEmpty()
                    && !repoOwner.trim().isEmpty());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hashCode(hashKey());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            BitbucketAvatarCacheSource that = (BitbucketAvatarCacheSource) o;
            return this.hashKey().equals(that.hashKey());
        }

        @Override
        public String toString() {
            return "BitbucketAvatarSource(" + hashKey() + ")";
        }
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public String getAvatarImageOf(String size) {
        // fall back to the generic bitbucket org icon if no avatar provided
        return avatarSource == null
                ? avatarIconClassNameImageOf(getAvatarIconClassName(), size)
                : AvatarCache.buildUrl(avatarSource, size);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getAvatarIconClassName() {
        return avatarSource == null ? "icon-bitbucket-logo" : null;
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
        if (this.avatarSource == null) {
            return that.avatarSource == null;
        }
        return this.avatarSource.equals(that.avatarSource);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(avatarSource);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "BitbucketTeamMetadataAction{" +
                ", avatarSource='" + avatarSource + '\'' +
                '}';
    }
}
