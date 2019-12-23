package com.cloudbees.jenkins.plugins.bitbucket.api.credentials;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import hudson.util.Secret;

public class BitbucketOAuthCredentialMatcher implements CredentialsMatcher, CredentialsMatcher.CQL {
    private static int keyLenght = 18;
    private static int secretLenght = 32;

    private static final long serialVersionUID = 6458784517693211197L;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(Credentials item) {
        if (!(item instanceof UsernamePasswordCredentials))
            return false;

        UsernamePasswordCredentials usernamePasswordCredential = ((UsernamePasswordCredentials) item);
        String username = usernamePasswordCredential.getUsername();
        boolean isEMail = username.contains(".") && username.contains("@");
        boolean validSecretLenght = Secret.toString(usernamePasswordCredential.getPassword()).length() == secretLenght;
        boolean validKeyLenght = username.length() == keyLenght;

        return !isEMail && validKeyLenght && validSecretLenght;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String describe() {
        return String.format(
                "(username.lenght == %d && password.lenght == %d && !(username CONTAINS \".\" && username CONTAINS \"@\")",
                keyLenght, secretLenght);
    }


}
