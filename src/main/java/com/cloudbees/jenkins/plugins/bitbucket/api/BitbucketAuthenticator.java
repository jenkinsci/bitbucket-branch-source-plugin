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

import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import jenkins.authentication.tokens.api.AuthenticationTokenContext;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.HttpClientBuilder;

/**
 * Support for various different methods of authenticating with Bitbucket
 */
public abstract class BitbucketAuthenticator {

    private final String id;

    /**
     * The key for bitbucket URL as reported in an {@link AuthenticationTokenContext}
     */
    public static final String SERVER_URL = "bitbucket.server.uri";

    /**
     * The key for URL scheme as reported in an {@link AuthenticationTokenContext}
     */
    public static final String SCHEME = "bitbucket.server.uri.scheme";

    /**
     * The key for Bitbucket instance type as reported in an {@link AuthenticationTokenContext}
     */
    public static final String BITBUCKET_INSTANCE_TYPE = "bitbucket.server.type";

    /**
     * Purpose value for bitbucket cloud (i.e. bitbucket.org)
     */
    public static final String BITBUCKET_INSTANCE_TYPE_CLOUD = "BITBUCKET_CLOUD";

    /**
     * Purpose value for bitbucket server
     */
    public static final String BITBUCKET_INSTANCE_TYPE_SERVER = "BITBUCKET_SERVER";

    /**
     * Constructor
     *
     * @param credentials credentials instance this authenticator will use
     */
    public BitbucketAuthenticator(StandardCredentials credentials) {
        id = credentials.getId();
    }

    /**
     * @return id of the credentials used.
     */
    public String getId() {
        return id;
    }

    /**
     * Configures an {@link HttpClientBuilder}. Override if you need to adjust connection setup.
     * @param builder The client builder.
     */
    public void configureBuilder(HttpClientBuilder builder) {
        // override to configure HttpClientBuilder
    }

    /**
     * Configures an {@link HttpClientContext}. Override
     * @param context The connection context
     * @param host host being connected to
     */
    public void configureContext(HttpClientContext context, HttpHost host) {
        // override to configure HttpClientContext
    }

    /**
     * Configures an {@link HttpRequest}. Override this if your authentication method needs to set headers on a
     * per-request basis.
     *
     * @param request the request.
     */
    public void configureRequest(HttpRequest request) {
        // override to configure HttpRequest
    }

    /**
     * Return the user to be used in the clone Uri. Override this if your
     * authentication method needs to set the user in the repository Uri
     *
     * @return user name to use in the repository Uri
     */
    public String getUserUri() {
        // override to return a user
        return "";
    }

    /**
     * Generates context that sub-classes can use to determine if they would be able to authenticate against the
     * provided server.
     *
     * @param serverUrl The URL being authenticated against
     * @return an {@link AuthenticationTokenContext} for use with the AuthenticationTokens APIs
     */
    public static AuthenticationTokenContext<BitbucketAuthenticator> authenticationContext(String serverUrl) {
        if (serverUrl == null) {
            serverUrl = BitbucketCloudEndpoint.SERVER_URL;
        }

        String scheme = serverUrl.split(":")[0].toLowerCase();
        boolean isCloud = serverUrl.equals(BitbucketCloudEndpoint.SERVER_URL);

        return AuthenticationTokenContext.builder(BitbucketAuthenticator.class)
                .with(SERVER_URL, serverUrl)
                .with(SCHEME, scheme)
                .with(BITBUCKET_INSTANCE_TYPE, isCloud ? BITBUCKET_INSTANCE_TYPE_CLOUD : BITBUCKET_INSTANCE_TYPE_SERVER)
                .build();
    }
}
