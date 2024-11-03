package com.cloudbees.jenkins.plugins.bitbucket.api.credentials;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Descriptor.FormException;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpRequest;
import org.scribe.model.OAuthConfig;
import org.scribe.model.OAuthConstants;
import org.scribe.model.Token;

public class BitbucketOAuthAuthenticator extends BitbucketAuthenticator {

    private Token token;

    /**
     * Constructor.
     *
     * @param credentials the key/pass that will be used
     */
    public BitbucketOAuthAuthenticator(StandardUsernamePasswordCredentials credentials) {
        super(credentials);

        OAuthConfig config = new OAuthConfig(credentials.getUsername(), credentials.getPassword().getPlainText());

        BitbucketOAuthService OAuthService = (BitbucketOAuthService) new BitbucketOAuth().createService(config);

        token = OAuthService.getAccessToken(OAuthConstants.EMPTY_TOKEN, null);
    }

    /**
     * Set up request with token in header
     */
    @Override
    public void configureRequest(HttpRequest request) {
        request.addHeader(OAuthConstants.HEADER, "Bearer " + this.token.getToken());
    }

    @Override
    public StandardUsernameCredentials getCredentialsForSCM() {
        try {
            return new UsernamePasswordCredentialsImpl(
                    CredentialsScope.GLOBAL, getId(), null, StringUtils.EMPTY, token.getToken());
        } catch (FormException e) {
            throw new RuntimeException(e);
        }
    }
}
