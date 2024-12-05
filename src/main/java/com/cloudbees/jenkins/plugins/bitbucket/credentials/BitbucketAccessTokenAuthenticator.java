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

package com.cloudbees.jenkins.plugins.bitbucket.credentials;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Descriptor.FormException;
import hudson.util.Secret;
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
     * @param request to configure with the access token
     */
    public void configureRequest(HttpRequest request) {
        request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token.getPlainText());
    }

    /**
     * Provides with the Git command line interface.
     * <p>
     * As per documentation the username must be x-token-auth and the password
     * is the token.
     *
     * @return the UsernamePasswordCredentials credential to be used with Git
     *         command line interface
     */
    @Override
    public StandardUsernameCredentials getCredentialsForSCM() {
        try {
            return new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, getId(), null, "x-token-auth", token.getPlainText());
        } catch (FormException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getId() {
        return credentialsId;
    }
}
