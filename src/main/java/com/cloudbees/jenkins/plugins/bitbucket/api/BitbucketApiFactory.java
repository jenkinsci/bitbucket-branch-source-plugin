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

import com.cloudbees.jenkins.plugins.bitbucket.impl.credentials.BitbucketUsernamePasswordAuthenticator;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import java.net.URL;

/**
 * Factory for creating {@link BitbucketApi} instances to connect to a given server {@link URL}.
 *
 * @since 2.1.0
 */
public abstract class BitbucketApiFactory implements ExtensionPoint {

    /**
     * Tests if the supplied URL is supported by this factory.
     *
     * @param serverUrl the server URL (may be {@code null}, e.g. for Bitbucket Cloud)
     * @return {@code true} if this factory can connect to the specified URL.
     */
    protected abstract boolean isMatch(@Nullable String serverUrl);

    /**
     * Creates a {@link BitbucketApi} for the specified URL with the supplied credentials, owner and (optional)
     * repository.
     *
     * @param serverUrl   the server URL.
     * @param authenticator the (optional) authenticator.
     * @param owner       the owner name.
     * @param projectKey  the (optional) project key.
     * @param repository  the (optional) repository name.
     * @return the {@link BitbucketApi}.
     */
    @NonNull
    protected abstract BitbucketApi create(@Nullable String serverUrl,
                                           @Nullable BitbucketAuthenticator authenticator,
                                           @NonNull String owner,
                                           @CheckForNull String projectKey,
                                           @CheckForNull String repository);

    @NonNull
    @Deprecated
    protected BitbucketApi create(@Nullable String serverUrl,
                                           @Nullable StandardUsernamePasswordCredentials credentials,
                                           @NonNull String owner,
                                           @CheckForNull String repository) {
        BitbucketAuthenticator auth = credentials != null ? new BitbucketUsernamePasswordAuthenticator(credentials) : null;
        return create(serverUrl, auth, owner, null, repository);
    }

    /**
     * Creates a {@link BitbucketApi} for the specified URL with the supplied credentials, owner and (optional)
     * repository.
     *
     * @param serverURL   the server URL.
     * @param authenticator the (optional) authenticator.
     * @param owner       the owner name.
     * @param projectKey  the (optional) project key.
     * @param repository  the (optional) repository name.
     * @return the {@link BitbucketApi}.
     * @throws IllegalArgumentException if the supplied URL is not supported.
     */
    @NonNull
    public static BitbucketApi newInstance(@Nullable String serverURL,
                                           @Nullable BitbucketAuthenticator authenticator,
                                           @NonNull String owner,
                                           @CheckForNull String projectKey,
                                           @CheckForNull String repository) {
        for (BitbucketApiFactory factory : ExtensionList.lookup(BitbucketApiFactory.class)) {
            if (factory.isMatch(serverURL)) {
                return factory.create(serverURL, authenticator, owner, projectKey, repository);
            }
        }
        throw new IllegalArgumentException("Unsupported Bitbucket server URL: " + serverURL);
    }

    @NonNull
    @Deprecated
    public static BitbucketApi newInstance(@Nullable String serverUrl,
                                           @Nullable StandardUsernamePasswordCredentials credentials,
                                           @NonNull String owner,
                                           @CheckForNull String projectKey,
                                           @CheckForNull String repository) {
        BitbucketAuthenticator auth = credentials != null ? new BitbucketUsernamePasswordAuthenticator(credentials) : null;
        return newInstance(serverUrl, auth, owner, projectKey, repository);
    }
}
