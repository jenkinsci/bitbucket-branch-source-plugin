package com.cloudbees.jenkins.plugins.bitbucket.server.client.cache;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import java.util.Objects;
import java.util.logging.Logger;

public class BitbucketApiKey {

    private static final Logger LOGGER = Logger.getLogger(BitbucketApiKey.class.getName());


    private final String serverUrl;
    private final String ownerName;
    private final String repositoryName;
    private final String username;
    private final String password;

    public BitbucketApiKey(String serverUrl, String owner, String repository, StandardUsernamePasswordCredentials credentials) {
        this.serverUrl = serverUrl;
        this.ownerName = owner;
        this.repositoryName = repository;
        if (credentials != null) {
            this.username = credentials.getUsername();
            this.password = credentials.getPassword().getEncryptedValue();
        } else {
            username = null;
            password = null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BitbucketApiKey)) return false;
        BitbucketApiKey that = (BitbucketApiKey) o;
        return Objects.equals(serverUrl, that.serverUrl) &&
                Objects.equals(ownerName, that.ownerName) &&
                Objects.equals(repositoryName, that.repositoryName) &&
                Objects.equals(username, that.username) &&
                Objects.equals(password, that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverUrl, ownerName, repositoryName, username, password);
    }

    public boolean equalsRepository(BitbucketRepository repository) {
        //LOGGER.info("["+repository.getOwnerName()+"]["+ownerName+"]["+repository.getRepositoryName()+"]["+repositoryName+"]");
        return ownerName.equalsIgnoreCase(repository.getOwnerName()) &&
               repositoryName.equalsIgnoreCase(repository.getRepositoryName());
    }
}
