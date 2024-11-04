package com.cloudbees.jenkins.plugins.bitbucket.api.credentials;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.httpclient.HttpClient;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthConstants;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.github.scribejava.httpclient.apache.ApacheHttpClientConfig;
import com.github.scribejava.httpclient.apache.ApacheProvider;
import hudson.model.Descriptor.FormException;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import jenkins.authentication.tokens.api.AuthenticationTokenException;
import org.apache.commons.lang.StringUtils;
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

        HttpClient httpClient = new ApacheProvider().createClient(ApacheHttpClientConfig.defaultConfig());
        OAuth20Service service = new ServiceBuilder(credentials.getUsername())
            .apiSecret(credentials.getPassword().getPlainText())
            .httpClient(httpClient)
         // .httpClientConfig(ApacheHttpClientConfig.defaultConfig()) the ServiceLoader does not work well with Jenkins plugin classloader
            .build(BitbucketOAuth.instance());

        try {
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
                    CredentialsScope.GLOBAL, getId(), null, StringUtils.EMPTY, token.getAccessToken());
        } catch (FormException e) {
            throw new RuntimeException(e);
        }
    }
}
