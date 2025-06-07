/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket.api;

import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketApiUtils;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import hudson.plugins.git.GitSCM;
import jenkins.authentication.tokens.api.AuthenticationTokenContext;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;

/**
 * Support for various different methods of authenticating with Bitbucket
 */
public interface BitbucketAuthenticator {

    /**
     * The key for bitbucket URL as reported in an {@link AuthenticationTokenContext}
     */
    static final String SERVER_URL = "bitbucket.server.uri";

    /**
     * The key for URL scheme as reported in an {@link AuthenticationTokenContext}
     */
    static final String SCHEME = "bitbucket.server.uri.scheme";

    /**
     * The key for Bitbucket instance type as reported in an {@link AuthenticationTokenContext}
     */
    static final String BITBUCKET_INSTANCE_TYPE = "bitbucket.server.type";

    /**
     * Purpose value for bitbucket cloud (i.e. bitbucket.org)
     */
    static final String BITBUCKET_INSTANCE_TYPE_CLOUD = "BITBUCKET_CLOUD";

    /**
     * Purpose value for bitbucket server
     */
    static final String BITBUCKET_INSTANCE_TYPE_SERVER = "BITBUCKET_SERVER";

    /**
     * @return id of the credentials used.
     */
    String getId();

    /**
     * Configures an {@link HttpClientBuilder}. Override if you need to adjust connection setup.
     * @param builder The client builder.
     */
    default void configureBuilder(HttpClientBuilder builder) {
    }

    /**
     * Configures an {@link HttpClientContext}. Override
     * @param context The connection context
     * @param host host being connected to
     */
    default void configureContext(HttpClientContext context, HttpHost host) {
    }

    /**
     * Configures an {@link HttpRequest}. Override this if your authentication method needs to set headers on a
     * per-request basis.
     *
     * @param request the request.
     */
    default void configureRequest(HttpRequest request) {
    }

    /**
     * Provides credentials that can be used for authenticated interactions with
     * SCM.
     *
     * @return credentials to be passed to
     *         {@link org.jenkinsci.plugins.gitclient.GitClient#setCredentials(StandardUsernameCredentials)}.
     *         If {@code null} force {@link GitSCM} to obtain credentials in the
     *         standard way, from the credential provider, using the credential
     *         identifier provided by {@link #getId()}.
     */
    default StandardUsernameCredentials getCredentialsForSCM() {
        return null;
    }

    /**
     * Generates context that sub-classes can use to determine if they would be able to authenticate against the
     * provided server.
     *
     * @param serverURL The URL being authenticated against
     * @return an {@link AuthenticationTokenContext} for use with the AuthenticationTokens APIs
     */
    public static AuthenticationTokenContext<BitbucketAuthenticator> authenticationContext(String serverURL) {
        if (serverURL == null) {
            serverURL = BitbucketCloudEndpoint.SERVER_URL;
        }

        String scheme = serverURL.split(":")[0].toLowerCase();
        boolean isCloud = BitbucketApiUtils.isCloud(serverURL);

        return AuthenticationTokenContext.builder(BitbucketAuthenticator.class)
                .with(SERVER_URL, serverURL)
                .with(SCHEME, scheme)
                .with(BITBUCKET_INSTANCE_TYPE, isCloud ? BITBUCKET_INSTANCE_TYPE_CLOUD : BITBUCKET_INSTANCE_TYPE_SERVER)
                .build();
    }
}
