package com.cloudbees.jenkins.plugins.bitbucket.api.credentials;

import com.github.scribejava.core.builder.api.DefaultApi20;

public class BitbucketOAuth extends DefaultApi20 {
    private static final String OAUTH_ENDPOINT = "https://bitbucket.org/site/oauth2/";

    protected BitbucketOAuth() {
    }

    private static class InstanceHolder {
        private static final BitbucketOAuth INSTANCE = new BitbucketOAuth();
    }

    public static BitbucketOAuth instance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public String getAccessTokenEndpoint() {
        return OAUTH_ENDPOINT + "access_token";
    }

    @Override
    protected String getAuthorizationBaseUrl() {
        return OAUTH_ENDPOINT + "authorize";
    }

}
