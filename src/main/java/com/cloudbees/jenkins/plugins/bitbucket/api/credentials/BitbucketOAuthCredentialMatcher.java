package com.cloudbees.jenkins.plugins.bitbucket.api.credentials;

import com.cloudbees.jenkins.plugins.bitbucket.credentials.BitbucketOAuthCredentials;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatcher;

public class BitbucketOAuthCredentialMatcher implements CredentialsMatcher, CredentialsMatcher.CQL {
    private static final long serialVersionUID = 6458784517693211197L;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(Credentials item) {
        if (item instanceof BitbucketOAuthCredentials)
            return true;

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String describe() {
        return "BitbucketOAuthCredentials";
    }


}
