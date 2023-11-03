package com.cloudbees.jenkins.plugins.bitbucket.api.credentials;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BitbucketOAuthCredentialMatcher implements CredentialsMatcher, CredentialsMatcher.CQL {
    private static int keyLength = 18;
    private static int secretLength = 32;

    private static final long serialVersionUID = 6458784517693211197L;
    private static final Logger LOGGER = Logger.getLogger(BitbucketOAuthCredentialMatcher.class.getName());

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(Credentials item) {
        if (!(item instanceof UsernamePasswordCredentials))
            return false;

        if(item.getClass().getName().equals("com.cloudbees.jenkins.plugins.amazonecr.AmazonECSRegistryCredential")) {
            return false;
        }

        try {
            UsernamePasswordCredentials usernamePasswordCredential = ((UsernamePasswordCredentials) item);
            String username = usernamePasswordCredential.getUsername();
            boolean isEMail = username.contains(".") && username.contains("@");
            boolean validSecretLength = usernamePasswordCredential.getPassword().getPlainText().length() == secretLength;
            boolean validKeyLength = username.length() == keyLength;

            return !isEMail && validKeyLength && validSecretLength;
        } catch (RuntimeException e) {
            LOGGER.log(Level.FINE, "Caught exception validating credential", e);
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String describe() {
        return String.format(
                "(username.lenght == %d && password.lenght == %d && !(username CONTAINS \".\" && username CONTAINS \"@\")",
                keyLength, secretLength);
    }


}
