package com.cloudbees.jenkins.plugins.bitbucket.api;

import org.eclipse.jgit.util.Base64;
import org.scribe.builder.api.DefaultApi20;
import org.scribe.model.*;
import org.scribe.oauth.OAuth20ServiceImpl;

public class BitbucketOAuthService extends OAuth20ServiceImpl {
    private static final String GRANT_TYPE_KEY = "grant_type";
    private static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";

    private DefaultApi20 api;
    private OAuthConfig config;

    public BitbucketOAuthService(DefaultApi20 api, OAuthConfig config) {
        super(api, config);
        this.api = api;
        this.config = config;
    }

    @Override
    public Token getAccessToken(Token requestToken, Verifier verifier) {
        OAuthRequest request = new OAuthRequest(api.getAccessTokenVerb(), api.getAccessTokenEndpoint());
        request.addHeader(OAuthConstants.HEADER, this.getHttpBasicAuthHeaderValue());
        request.addBodyParameter(GRANT_TYPE_KEY, GRANT_TYPE_CLIENT_CREDENTIALS);
        Response response = request.send();

        return api.getAccessTokenExtractor().extract(response.getBody());
    }

    @Override
    public void signRequest(Token accessToken, OAuthRequest request) {
        request.addHeader(OAuthConstants.HEADER, this.getBearerAuthHeaderValue(accessToken));
    }

    private String getHttpBasicAuthHeaderValue() {
        String authStr = config.getApiKey() + ":" + config.getApiSecret();

        return "Basic " + Base64.encodeBytes(authStr.getBytes());
    }

    private String getBearerAuthHeaderValue(Token token) {
        return "Bearer " + token.getToken();
    }
}
