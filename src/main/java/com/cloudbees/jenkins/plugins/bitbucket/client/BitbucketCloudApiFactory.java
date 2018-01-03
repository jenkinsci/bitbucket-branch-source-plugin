package com.cloudbees.jenkins.plugins.bitbucket.client;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
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
    protected BitbucketApi create(@Nullable String serverUrl, @Nullable StandardUsernamePasswordCredentials credentials,
                                  @Nullable StandardCertificateCredentials certCreds,
                                  @NonNull String owner, @CheckForNull String repository) {
        return new BitbucketCloudApiClient(owner, repository, credentials);
    }
}
