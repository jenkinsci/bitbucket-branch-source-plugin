package com.cloudbees.jenkins.plugins.bitbucket.api.credentials;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.credentials.BitbucketOAuthCredentials;
import hudson.util.Secret;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.HttpRequest;
import org.scribe.model.OAuthConfig;
import org.scribe.model.OAuthConstants;
import org.scribe.model.Token;

public class BitbucketOAuthAuthenticator extends BitbucketAuthenticator {

    private Token token;
    private static final Logger LOGGER = Logger.getLogger(BitbucketOAuthAuthenticator.class.getName());

    /**
     * Constructor.
     *
     * @param credentials the key/pass that will be used
     */
    public BitbucketOAuthAuthenticator(BitbucketOAuthCredentials credentials) {
        super(credentials);

        OAuthConfig config;
        try {
            config = new OAuthConfig(credentials.getKey(), Secret.toString(credentials.getSecret()));

            BitbucketOAuthService OAuthService = (BitbucketOAuthService) new BitbucketOAuth().createService(config);

            token = OAuthService.getAccessToken(OAuthConstants.EMPTY_TOKEN, null);
        } catch (IOException | InterruptedException e) {
            // TODO Auto-generated catch block
            LOGGER.log(Level.WARNING, "Could not retrieve credentials: { " + e.getMessage() + "}");
        }


    }

    /**
     * Set up request with token in header
     */
    @Override
    public void configureRequest(HttpRequest request) {
        request.addHeader(OAuthConstants.HEADER, "Bearer " + this.token.getToken());
    }

    @Override
    public String getUserUri() {
        return "x-token-auth:{" + token.getToken() + "}";
    }

}
