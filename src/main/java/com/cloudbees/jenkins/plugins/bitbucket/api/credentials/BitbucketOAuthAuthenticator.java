package com.cloudbees.jenkins.plugins.bitbucket.api.credentials;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.httpclient.jdk.JDKHttpClientConfig;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthConstants;
import com.github.scribejava.core.oauth.OAuth20Service;
import hudson.model.Descriptor.FormException;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import jenkins.authentication.tokens.api.AuthenticationTokenException;
import jenkins.util.SetContextClassLoader;
import org.apache.http.HttpRequest;

public class BitbucketOAuthAuthenticator extends BitbucketAuthenticator {

    private OAuth2AccessToken token;

    /**
     * Constructor.
     *
     * @param credentials the key/pass that will be used
     * @throws AuthenticationTokenException
     */
    public BitbucketOAuthAuthenticator(StandardUsernamePasswordCredentials credentials) throws AuthenticationTokenException {
        super(credentials);

        try (SetContextClassLoader cl = new SetContextClassLoader(this.getClass());
                OAuth20Service service = new ServiceBuilder(credentials.getUsername())
                    .apiSecret(credentials.getPassword().getPlainText())
                    .httpClientConfig(JDKHttpClientConfig.defaultConfig())
                    .build(BitbucketOAuth.instance())) {
            token = service.getAccessTokenClientCredentialsGrant();
        } catch (IOException | InterruptedException | ExecutionException e) {
            throw new AuthenticationTokenException(e);
        }
    }

    /**
     * Set up request with token in header
     */
    @Override
    public void configureRequest(HttpRequest request) {
        request.addHeader(OAuthConstants.HEADER, "Bearer " + this.token.getAccessToken());
    }

    @Override
    public StandardUsernameCredentials getCredentialsForSCM() {
        try {
            return new UsernamePasswordCredentialsImpl(
                    CredentialsScope.GLOBAL, getId(), null, "x-token-auth", token.getAccessToken());
        } catch (FormException e) {
            throw new RuntimeException(e);
        }
    }
}
