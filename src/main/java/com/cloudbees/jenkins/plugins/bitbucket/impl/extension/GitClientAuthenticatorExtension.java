/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket.impl.extension;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketCredentialsUtils;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.Item;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.security.ACL;
import hudson.security.ACLContext;
import java.util.Objects;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

public class GitClientAuthenticatorExtension extends GitSCMExtension {

    @Deprecated
    private transient StandardUsernameCredentials credentials;
    private final String url;
    private final String serverURL;
    private final String scmOwner;
    private final String credentialsId;

    @Deprecated(since = "936.0.0", forRemoval = true)
    public GitClientAuthenticatorExtension(String url, StandardUsernameCredentials credentials) {
        this.url = url;
        this.credentialsId = credentials != null ? credentials.getId() : null;
        this.credentials = credentials;
        this.serverURL = null;
        this.scmOwner = null;
    }

    @DataBoundConstructor
    public GitClientAuthenticatorExtension(@NonNull String url, @NonNull String serverURL, @CheckForNull String scmOwner, @Nullable String credentialsId) {
        this.url = url;
        this.serverURL = serverURL;
        this.scmOwner = scmOwner;
        this.credentialsId = credentialsId;
    }

    @Override
    public GitClient decorate(GitSCM scm, GitClient git) throws GitException {
        StandardUsernameCredentials credentials = this.credentials;
        if (credentialsId != null) {
            BitbucketAuthenticator authenticator = authenticator();
            if (authenticator == null) {
                throw new IllegalStateException("No credentialsId " + getCredentialsId() + " found for project " + scmOwner + " and server " + serverURL);
            }
            credentials = authenticator.getCredentialsForSCM();
        }
        if (credentials != null) {
            if (url == null) {
                git.setCredentials(credentials);
            } else {
                git.addCredentials(url, credentials);
            }
        }
        return git;
    }

    @CheckForNull
    private BitbucketAuthenticator authenticator() {
        if (serverURL == null) {
            throw new IllegalStateException("Some required data are missing, perform a 'Scan project Now' action to refresh old data");
        }
        StandardCredentials credentials;
        if (scmOwner != null) {
            Item owner = null;
            // to access item when security (not matrix) is enabled or
            // logged user does not have READ(DISCOVER) access on the item
            try (ACLContext as = ACL.as2(ACL.SYSTEM2)) {
                owner = Jenkins.get().getItemByFullName(scmOwner, Item.class);
            }
            if (owner == null) {
                throw new IllegalStateException("Item " + scmOwner + " seems to be relocated, perform a 'Scan project Now' action to refresh old data");
            }
            credentials = BitbucketCredentialsUtils.lookupCredentials(owner, serverURL, credentialsId, StandardCredentials.class);
        } else {
            credentials = BitbucketCredentialsUtils.lookupCredentials(Jenkins.get(), serverURL, credentialsId, StandardCredentials.class);
        }
        return AuthenticationTokens.convert(BitbucketAuthenticator.authenticationContext(serverURL), credentials);
    }

    @Deprecated(since = "936.0.0", forRemoval = true)
    public StandardUsernameCredentials getCredentials() {
        return credentials;
    }

    public String getUrl() {
        return url;
    }

    public String getServerURL() {
        return serverURL;
    }

    public String getScmOwner() {
        return scmOwner;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, serverURL, scmOwner, credentialsId);
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
        GitClientAuthenticatorExtension other = (GitClientAuthenticatorExtension) obj;
        return Objects.equals(credentialsId, other.credentialsId)
                && Objects.equals(serverURL, other.serverURL)
                && Objects.equals(scmOwner, other.scmOwner)
                && Objects.equals(url, other.url);
    }

    @Extension
    // No @Symbol because Pipeline users should not configure this in other ways than this plugin provides
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Translated git client credentials.";
        }
    }
}
