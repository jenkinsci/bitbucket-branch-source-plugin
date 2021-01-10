package com.cloudbees.jenkins.plugins.bitbucket.api.credentials;

import org.scribe.builder.api.DefaultApi20;
import org.scribe.extractors.AccessTokenExtractor;
import org.scribe.extractors.JsonTokenExtractor;
import org.scribe.model.OAuthConfig;
import org.scribe.model.Verb;
import org.scribe.oauth.OAuthService;

public class BitbucketOAuth extends DefaultApi20 {
    public static final String OAUTH_ENDPOINT = "https://bitbucket.org/site/oauth2/";

    @Override
    public String getAccessTokenEndpoint() {
        return OAUTH_ENDPOINT + "access_token";
    }

    @Override
    public String getAuthorizationUrl(OAuthConfig config) {
        return OAUTH_ENDPOINT + "authorize";
    }

    @Override
    public Verb getAccessTokenVerb() {
        return Verb.POST;
    }

    @Override
    public AccessTokenExtractor getAccessTokenExtractor() {
        return new JsonTokenExtractor();
    }

    @Override
    public OAuthService createService(OAuthConfig config) {
        return new BitbucketOAuthService(this, config);
    }
}
