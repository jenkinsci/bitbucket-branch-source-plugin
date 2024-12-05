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
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import jenkins.authentication.tokens.api.AuthenticationTokenContext;
import jenkins.authentication.tokens.api.AuthenticationTokenSource;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

/**
 * Source for access token authenticators.
 */
@Extension
public class BitbucketAccessTokenAuthenticatorSource extends AuthenticationTokenSource<BitbucketAccessTokenAuthenticator, StringCredentials> {

    /**
     * Constructor.
     */
    public BitbucketAccessTokenAuthenticatorSource() {
        super(BitbucketAccessTokenAuthenticator.class, StringCredentials.class);
    }

    /**
     * Converts string credentials to an authenticator.
     *
     * @param credentials the access token
     * @return an authenticator that will use the access token
     */
    @NonNull
    @Override
    public BitbucketAccessTokenAuthenticator convert(@NonNull StringCredentials credentials) {
        return new BitbucketAccessTokenAuthenticator(credentials);
    }

    /**
     * Whether this source works in the given context.
     *
     * @param ctx the context
     * @return whether this can authenticate given the context
     */
    @Override
    protected boolean isFit(AuthenticationTokenContext<? super BitbucketAccessTokenAuthenticator> ctx) {
        return ctx.mustHave(BitbucketAuthenticator.SCHEME, "https")
            && (ctx.mustHave(BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE, BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE_SERVER)
            || ctx.mustHave(BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE, BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE_CLOUD));
    }
}
