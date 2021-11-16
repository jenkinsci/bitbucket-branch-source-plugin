package com.cloudbees.jenkins.plugins.bitbucket.api.credentials;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import hudson.util.Secret;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

/**
 * Authenticator that uses an access token.
 */
public class BitbucketAccessTokenAuthenticator extends BitbucketAuthenticator {

    private final Secret token;

    /**
     * Constructor.
     *
     * @param credentials the access token that will be used
     */
    public BitbucketAccessTokenAuthenticator(StringCredentials credentials) {
        super(credentials);
        token = credentials.getSecret();
    }

    /**
     * Provides the access token as header.
     *
     * @param request the request
     */
    public void configureRequest(HttpRequest request) {
        request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token.getPlainText());
    }
}
