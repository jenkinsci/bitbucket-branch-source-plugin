/*
 * The MIT License
 *
 * Copyright (c) 2024, Falco Nikolas
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
import org.apache.commons.lang3.StringUtils;

// Although the CredentialsMatcher documentation says that the best practice
// is to implement CredentialsMatcher.CQL too, this class does not implement
// CredentialsMatcher.CQL, for the following reasons:
//
// * CQL supports neither method calls like StringUtils.isNotBlank(username)
//   nor any regular-expression matching that could be used instead.
// * There don't seem to be any public credential-provider plugins that
//   would benefit from CQL.
public class BitbucketUsernamePasswordCredentialMatcher implements CredentialsMatcher {
    private static final long serialVersionUID = -9196480589659636909L;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(Credentials item) {
        if (!(item instanceof StandardUsernamePasswordCredentials)) { // safety check
            return false;
        }

        StandardUsernamePasswordCredentials credential = ((StandardUsernamePasswordCredentials) item);
        String username = credential.getUsername();
        String password = Secret.toString(credential.getPassword());
        return StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password);
    }
}
