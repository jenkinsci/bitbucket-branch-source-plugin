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

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.util.Secret;

public class BitbucketOAuthCredentialMatcher implements CredentialsMatcher {
    private static final long serialVersionUID = 6458784517693211197L;

    private static final int CLIENT_OLD_KEY_LENGTH = 18;
    private static final int CLIENT_OLD_SECRET_LENGTH = 32;
    private static final int CLIENT_KEY_LENGTH = 32;
    private static final int CLIENT_SECRET_LENGTH = 76;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(Credentials item) {
        if (!(item instanceof StandardUsernamePasswordCredentials)) { // safety check
            return false;
        }

        StandardUsernamePasswordCredentials credentials = ((StandardUsernamePasswordCredentials) item);
        String username = credentials.getUsername();
        String password = Secret.toString(credentials.getPassword());

        return !isEmail(username) && isSupportedOAuthConsumer(username, password);
    }

    private boolean isEmail(String username) {
        return username.contains(".") && username.contains("@");
    }

    private boolean isSupportedOAuthConsumer(String key, String secret) {
        // Bitbucket Cloud has issued OAuth consumers in both legacy and current lengths.
        return hasLengthPair(key, secret, CLIENT_OLD_KEY_LENGTH, CLIENT_OLD_SECRET_LENGTH)
                || hasLengthPair(key, secret, CLIENT_KEY_LENGTH, CLIENT_SECRET_LENGTH);
    }

    private boolean hasLengthPair(String key, String secret, int expectedKeyLength, int expectedSecretLength) {
        return key.length() == expectedKeyLength && secret.length() == expectedSecretLength;
    }
}
