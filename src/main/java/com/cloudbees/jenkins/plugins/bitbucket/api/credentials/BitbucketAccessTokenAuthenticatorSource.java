package com.cloudbees.jenkins.plugins.bitbucket.api.credentials;

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
    public boolean isFit(AuthenticationTokenContext ctx) {
        return ctx.mustHave(BitbucketAuthenticator.SCHEME, "https")
            && ctx.mustHave(BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE, BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE_SERVER);
    }
}
