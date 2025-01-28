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
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import hudson.util.Secret;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Although the CredentialsMatcher documentation says that the best practice
 * is to implement CredentialsMatcher.CQL too, this class does not implement
 * CredentialsMatcher.CQL, for the following reasons:
 *
 * - CQL supports neither method calls like username.contains(".")
 *   nor any regular-expression matching that could be used instead;
 * - there don't seem to be any public credential-provider plugins that
 *    would benefit from CQL.
 */
public class BitbucketOAuthCredentialMatcher implements CredentialsMatcher {
    private static final long serialVersionUID = 6458784517693211197L;
    private static final Logger logger = Logger.getLogger(BitbucketOAuthCredentialMatcher.class.getName());

    private static final int CLIENT_KEY_LENGTH = 18;
    private static final int CLIENT_SECRET_LENGTH = 32;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(Credentials item) {
        if (!(item instanceof UsernamePasswordCredentials)) {
            return false;
        }

        try {
            UsernamePasswordCredentials usernamePasswordCredential = ((UsernamePasswordCredentials) item);
            String username = usernamePasswordCredential.getUsername();
            String password;
            try {
                password = Secret.toString(usernamePasswordCredential.getPassword());
            } catch (Exception e) {
                // JENKINS-75184
                return false;
            }

            boolean isEMail = username.contains(".") && username.contains("@");
            boolean validSecretLength = password.length() == CLIENT_SECRET_LENGTH;
            boolean validKeyLength = username.length() == CLIENT_KEY_LENGTH;

            return !isEMail && validKeyLength && validSecretLength;
        } catch (RuntimeException e) {
            logger.log(Level.FINE, "Caught exception validating credential", e);
            return false;
        }
    }
}
