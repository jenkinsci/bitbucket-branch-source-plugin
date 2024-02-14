package com.cloudbees.jenkins.plugins.bitbucket.api.credentials;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import java.net.URI;
import java.net.URISyntaxException;
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
    public BitbucketHref addAuthToken(BitbucketHref bitbucketHref) {
        String link = bitbucketHref.getHref();
        if (!link.startsWith("http")) {
            return bitbucketHref;
        }
        try {
            URI uri = new URI(link);
            String userInfo = "x-token-auth:{" + token.getToken() + "}";
            String newLink = new URI(
                uri.getScheme(),
                userInfo,
                uri.getHost(),
                uri.getPort(),
                uri.getPath(),
                uri.getQuery(),
                uri.getFragment()
            ).toString();
            return new BitbucketHref(bitbucketHref.getName(), newLink);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

}
