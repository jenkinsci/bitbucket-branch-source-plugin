package com.cloudbees.jenkins.plugins.bitbucket.credentials;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Descriptor.FormException;
import hudson.util.Secret;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

/**
 * Authenticator that uses an access token.
 */
public class BitbucketAccessTokenAuthenticator implements BitbucketAuthenticator {

    private final String credentialsId;
    private final Secret token;

    /**
     * Constructor.
     *
     * @param credentials the access token that will be used
     */
    public BitbucketAccessTokenAuthenticator(StringCredentials credentials) {
        this.credentialsId = credentials.getId();
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

    @Override
    public StandardUsernameCredentials getCredentialsForSCM() {
        try {
            return new UsernamePasswordCredentialsImpl(
                    CredentialsScope.GLOBAL, getId(), null, StringUtils.EMPTY, token.getPlainText());
        } catch (FormException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getId() {
        return credentialsId;
    }
}
