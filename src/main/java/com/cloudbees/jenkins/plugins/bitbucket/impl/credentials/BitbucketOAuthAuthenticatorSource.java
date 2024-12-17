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
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import jenkins.authentication.tokens.api.AuthenticationTokenContext;
import jenkins.authentication.tokens.api.AuthenticationTokenException;
import jenkins.authentication.tokens.api.AuthenticationTokenSource;


/**
 * Source for OAuth authenticators.
 */
@Extension
public class BitbucketOAuthAuthenticatorSource extends AuthenticationTokenSource<BitbucketOAuthAuthenticator, StandardUsernamePasswordCredentials> {

    /**
     * Constructor.
     */
    public BitbucketOAuthAuthenticatorSource() {
        super(BitbucketOAuthAuthenticator.class, StandardUsernamePasswordCredentials.class);
    }

    /**
     * Converts username/password credentials to an authenticator.
     *
     * @param standardUsernamePasswordCredentials the username/password combo
     * @return an authenticator that will use them.
     * @throws AuthenticationTokenException if the specific credentials could not be converted.
     */
    @NonNull
    @Override
    public BitbucketOAuthAuthenticator convert(@NonNull StandardUsernamePasswordCredentials standardUsernamePasswordCredentials) throws AuthenticationTokenException {
        return new BitbucketOAuthAuthenticator(standardUsernamePasswordCredentials);
    }

    /**
     * Whether this source works in the given context. For client certs, only HTTPS
     * BitbucketServer instances make sense
     *
     * @param ctx the context
     * @return whether this can authenticate given the context
     */
    @Override
    protected boolean isFit(AuthenticationTokenContext<? super BitbucketOAuthAuthenticator> ctx) {
        return ctx.mustHave(BitbucketAuthenticator.SCHEME, "https")
                && ctx.mustHave(BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE, BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE_CLOUD);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CredentialsMatcher matcher() {
        return new BitbucketOAuthCredentialMatcher();
    }

}
