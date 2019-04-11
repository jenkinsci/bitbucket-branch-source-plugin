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
import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.io.IOException;

@Extension
public class BitbucketCloudApiFactory extends BitbucketApiFactory {
    private static final String PAYLOAD_RESOURCE_ROOTPATH = "/com/cloudbees/jenkins/plugins/bitbucket/client/payload/";
    private static final String API_ENDPOINT = "https://api.bitbucket.org/";

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
        return new BitbucketCloudApiClient(enableCache, teamCacheDuration, repositoriesCacheDuration,
                owner, repository, authenticator) {
            @Override
            protected String getRequest(String path) throws IOException {
                String payloadPath = path.replace(API_ENDPOINT, "").replace('/', '-').replaceAll("[=%&?]", "_");
                payloadPath = PAYLOAD_RESOURCE_ROOTPATH + payloadPath + ".json";

                try (InputStream json = this.getClass().getResourceAsStream(payloadPath)) {
                    if (json == null) {
                        throw new IllegalStateException("Payload for the REST path " + path + " could be found");
                    }
                    return IOUtils.toString(json);
                }
            }
        };
    }
}
