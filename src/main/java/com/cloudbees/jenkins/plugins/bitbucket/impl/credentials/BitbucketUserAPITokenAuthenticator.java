/*
 * The MIT License
 *
 * Copyright (c) 2025, Falco Nikolas
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
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Descriptor.FormException;
import hudson.util.Secret;
import java.nio.charset.StandardCharsets;
import org.apache.hc.client5.http.utils.Base64;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;

/**
 * Authenticator that uses a username and password (probably the default)
 */
public class BitbucketUserAPITokenAuthenticator implements BitbucketAuthenticator {

    private final String encodedAuth;
    private final String credentialsId;
    private final Secret password;

    /**
     * Constructor.
     * @param credentials the username/password that will be used
     */
    public BitbucketUserAPITokenAuthenticator(StandardUsernamePasswordCredentials credentials) {
        credentialsId = credentials.getId();
        password = credentials.getPassword();
        String auth = credentials.getUsername() + ":" + Secret.toString(password);
        encodedAuth = Base64.encodeBase64String(auth.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Override
    public void configureRequest(HttpRequest request) {
        final String authHeader = "Basic " + encodedAuth;
        request.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
    }

    @Override
    public StandardUsernameCredentials getCredentialsForSCM() {
        try {
            return new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, getId(), "User API token for " + getId(), "x-bitbucket-api-token-auth", Secret.toString(password));
        } catch (FormException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getId() {
        return credentialsId;
    }

}
