package com.cloudbees.jenkins.plugins.bitbucket.api.credentials;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import jenkins.authentication.tokens.api.AuthenticationTokenContext;
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
     */
    @NonNull
    @Override
    public BitbucketOAuthAuthenticator convert(
            @NonNull StandardUsernamePasswordCredentials standardUsernamePasswordCredentials) {
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
    public boolean isFit(AuthenticationTokenContext ctx) {
        return ctx.mustHave(BitbucketAuthenticator.SCHEME, "https") && ctx.mustHave(
                BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE, BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE_CLOUD);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CredentialsMatcher matcher() {
        return new BitbucketOAuthCredentialMatcher();
    }

}
