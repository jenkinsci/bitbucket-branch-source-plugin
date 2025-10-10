/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket.client;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpointProvider;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;

@Extension
public class BitbucketCloudApiFactory extends BitbucketApiFactory {
    @Override
    protected boolean isMatch(@Nullable String serverUrl) {
        return serverUrl == null || BitbucketCloudEndpoint.SERVER_URL.equals(serverUrl);
    }

    @NonNull
    @Override
    protected BitbucketApi create(@Nullable String serverUrl, @Nullable BitbucketAuthenticator authenticator,
                                  @NonNull String owner, @CheckForNull String projectKey, @CheckForNull String repository) {
        BitbucketCloudEndpoint endpoint = BitbucketEndpointProvider
                .lookupEndpoint(BitbucketCloudEndpoint.SERVER_URL, BitbucketCloudEndpoint.class)
                .orElse(null);
        boolean enableCache = false;
        int teamCacheDuration = 360;
        int repositoriesCacheDuration = 180;
        if (endpoint != null) {
            enableCache = endpoint.isEnableCache();
            teamCacheDuration = endpoint.getTeamCacheDuration();
            repositoriesCacheDuration = endpoint.getRepositoriesCacheDuration();
        }
        return new BitbucketCloudApiClient(
                enableCache, teamCacheDuration, repositoriesCacheDuration,
                owner, projectKey, repository, authenticator);
    }
}
