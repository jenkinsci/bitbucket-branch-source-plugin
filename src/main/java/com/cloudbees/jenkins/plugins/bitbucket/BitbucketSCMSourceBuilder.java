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
package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.trait.SCMSourceBuilder;

/**
 * A {@link SCMSourceBuilder} that builds {@link BitbucketSCMSource} instances
 *
 * @since 2.2.0
 */
public class BitbucketSCMSourceBuilder extends SCMSourceBuilder<BitbucketSCMSourceBuilder, BitbucketSCMSource> {
    /**
     * The {@link BitbucketSCMSource#getId()}.
     */
    @CheckForNull
    private final String id;
    /**
     * The {@link BitbucketSCMSource#getServerUrl()}
     */
    @NonNull
    private final String serverUrl;
    /**
     * The credentials id or {@code null} to use anonymous scanning.
     */
    @CheckForNull
    private final String credentialsId;
    /**
     * The certificate credentials id or {@code null}
     */
    @CheckForNull
    private final String certificateCredentialsId;
    /**
     * The repository owner.
     */
    @NonNull
    private final String repoOwner;

    /**
     * Constructor.
     *
     * @param id            the {@link BitbucketSCMSource#getId()}
     * @param serverUrl     the {@link BitbucketSCMSource#getServerUrl()}
     * @param credentialsId the credentials id.
     * @param repoOwner     the repository owner.
     * @param repoName      the project name.
     */
    public BitbucketSCMSourceBuilder(@CheckForNull String id, @NonNull String serverUrl,
                                     @CheckForNull String credentialsId, @CheckForNull String certificateCredentialsId,
                                     @NonNull String repoOwner, @NonNull String repoName) {
        super(BitbucketSCMSource.class, repoName);
        this.id = id;
        this.serverUrl = BitbucketEndpointConfiguration.normalizeServerUrl(serverUrl);
        this.credentialsId = credentialsId;
        this.certificateCredentialsId = certificateCredentialsId;
        this.repoOwner = repoOwner;
    }

    /**
     * The id of the {@link BitbucketSCMSource} that is being built.
     *
     * @return the id of the {@link BitbucketSCMSource} that is being built.
     */
    @CheckForNull
    public final String id() {
        return id;
    }

    /**
     * The server url of the {@link BitbucketSCMSource} that is being built.
     *
     * @return the server url of the {@link BitbucketSCMSource} that is being built.
     */
    @NonNull
    public final String serverUrl() {
        return serverUrl;
    }

    /**
     * The credentials that the {@link BitbucketSCMSource} will use.
     *
     * @return the credentials that the {@link BitbucketSCMSource} will use.
     */
    @CheckForNull
    public final String credentialsId() {
        return credentialsId;
    }

    /**
     * The certificate credentials that the {@link BitbucketSCMSource} will use.
     *
     * @return the certificate credentials that the {@link BitbucketSCMSource} will use.
     */
    @CheckForNull
    public final String certificateCredentialsId() { return certificateCredentialsId; }

    /**
     * The repository owner that the {@link BitbucketSCMSource} will be configured to use.
     *
     * @return the repository owner that the {@link BitbucketSCMSource} will be configured to use.
     */
    @NonNull
    public final String repoOwner() {
        return repoOwner;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public BitbucketSCMSource build() {
        BitbucketSCMSource result = new BitbucketSCMSource(repoOwner(), projectName());
        result.setId(id());
        result.setServerUrl(serverUrl());
        result.setCredentialsId(credentialsId());
        result.setCertificateCredentialsId(certificateCredentialsId());
        result.setTraits(traits());
        return result;
    }
}
