/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.jenkins.plugins.bitbucket.impl.credentials;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketException;
import com.cloudbees.jenkins.plugins.bitbucket.client.Cache;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.httpclient.jdk.JDKHttpClientConfig;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuth2AccessTokenErrorResponse;
import com.github.scribejava.core.model.OAuthConstants;
import com.github.scribejava.core.oauth.OAuth20Service;
import hudson.model.Descriptor.FormException;
import hudson.util.Secret;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import jenkins.util.SetContextClassLoader;
import jenkins.util.SystemProperties;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.HttpRequest;

public class BitbucketOAuthAuthenticator implements BitbucketAuthenticator {
    private static final String OAUTH2_CACHE_TIMEOUT_PROPERTY_NAME = "bitbucket.oauth2.cache.timeout";
    private static final Cache<String, OAuth2AccessToken> cacheToken = new Cache<>(SystemProperties.getInteger(OAUTH2_CACHE_TIMEOUT_PROPERTY_NAME, 300), TimeUnit.SECONDS);

    private final String credentialsId;
    private final String username;
    private final Secret password;

    /**
     * Constructor.
     *
     * @param credentials the key/pass that will be used
     */
    public BitbucketOAuthAuthenticator(StandardUsernamePasswordCredentials credentials) {
        this.credentialsId = credentials.getId();
        this.username = credentials.getUsername();
        this.password = credentials.getPassword();
    }

    private OAuth2AccessToken getToken() {
        try {
            String plainSecret = Secret.toString(password);
            String cacheKey = DigestUtils.md2Hex(StringUtils.join(new String[] { credentialsId, username, plainSecret }, '/'));
            return cacheToken.get(cacheKey, () -> {
                try (SetContextClassLoader cl = new SetContextClassLoader(this.getClass());
                    OAuth20Service service = new ServiceBuilder(username)
                        .apiSecret(plainSecret)
                        .httpClientConfig(JDKHttpClientConfig.defaultConfig())
                        .build(BitbucketOAuth.instance())) {
                    return service.getAccessTokenClientCredentialsGrant();
                }
            });
        } catch (ExecutionException e) {
            OAuth2AccessTokenErrorResponse oauthEx = BitbucketAuthenticatorUtils.unwrap(e, OAuth2AccessTokenErrorResponse.class);
            if (oauthEx != null) {
                throw new BitbucketException(oauthEx.getErrorDescription() + ". Please check configured OAuth credentials client id and secret are correct.", e);
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Set up request with token in header
     */
    @Override
    public void configureRequest(HttpRequest request) {
        request.addHeader(OAuthConstants.HEADER, "Bearer " + getToken().getAccessToken());
    }

    @Override
    public StandardUsernameCredentials getCredentialsForSCM() {
        try {
            return new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, getId(), "OAuth2 token for " + getId(), "x-token-auth", getToken().getAccessToken());
        } catch (FormException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getId() {
        return credentialsId;
    }
}
