package com.cloudbees.jenkins.plugins.bitbucket.api;

import com.cloudbees.jenkins.plugins.bitbucket.api.credentials.BitbucketUsernamePasswordAuthenticator;
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
     * @param repository  the (optional) repository name.
     * @return the {@link BitbucketApi}.
     */
    @NonNull
    protected abstract BitbucketApi create(@Nullable String serverUrl,
                                           @Nullable BitbucketAuthenticator authenticator,
                                           @NonNull String owner,
                                           @CheckForNull String repository);

    @NonNull
    @Deprecated
    protected BitbucketApi create(@Nullable String serverUrl,
                                           @Nullable StandardUsernamePasswordCredentials credentials,
                                           @NonNull String owner,
                                           @CheckForNull String repository) {
        BitbucketAuthenticator auth = credentials != null ? new BitbucketUsernamePasswordAuthenticator(credentials) : null;
        return create(serverUrl, auth, owner, repository);
    }

    /**
     * Creates a {@link BitbucketApi} for the specified URL with the supplied credentials, owner and (optional)
     * repository.
     *
     * @param serverUrl   the server URL.
     * @param authenticator the (optional) authenticator.
     * @param owner       the owner name.
     * @param repository  the (optional) repository name.
     * @return the {@link BitbucketApi}.
     * @throws IllegalArgumentException if the supplied URL is not supported.
     */
    @NonNull
    public static BitbucketApi newInstance(@Nullable String serverUrl,
                                           @Nullable BitbucketAuthenticator authenticator,
                                           @NonNull String owner,
                                           @CheckForNull String repository) {
        for (BitbucketApiFactory factory : ExtensionList.lookup(BitbucketApiFactory.class)) {
            if (factory.isMatch(serverUrl)) {
                return factory.create(serverUrl, authenticator, owner, repository);
            }
        }
        throw new IllegalArgumentException("Unsupported Bitbucket server URL: " + serverUrl);
    }

    @NonNull
    @Deprecated
    public static BitbucketApi newInstance(@Nullable String serverUrl,
                                           @Nullable StandardUsernamePasswordCredentials credentials,
                                           @NonNull String owner,
                                           @CheckForNull String repository) {
        BitbucketAuthenticator auth = credentials != null ? new BitbucketUsernamePasswordAuthenticator(credentials) : null;
        return newInstance(serverUrl, auth, owner, repository);
    }
}
