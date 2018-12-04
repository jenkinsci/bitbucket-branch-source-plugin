package com.cloudbees.jenkins.plugins.bitbucket.client;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.AbstractBitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
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
                                  @NonNull String owner, @CheckForNull String repository) {
        AbstractBitbucketEndpoint endpoint = BitbucketEndpointConfiguration.get().findEndpoint(BitbucketCloudEndpoint.SERVER_URL);
        boolean enableCache = false;
        int teamCacheDuration = 0;
        int repositoriesCacheDuration = 0;
        if (endpoint != null && endpoint instanceof BitbucketCloudEndpoint) {
            enableCache = ((BitbucketCloudEndpoint) endpoint).isEnableCache();
            teamCacheDuration = ((BitbucketCloudEndpoint) endpoint).getTeamCacheDuration();
            repositoriesCacheDuration = ((BitbucketCloudEndpoint) endpoint).getRepositoriesCacheDuration();
        }
        return new BitbucketCloudApiClient(
                enableCache, teamCacheDuration, repositoriesCacheDuration,
                owner, repository, authenticator);
    }
}
