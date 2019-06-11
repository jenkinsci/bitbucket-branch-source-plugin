package com.cloudbees.jenkins.plugins.bitbucket.server.client.cache;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import java.util.Objects;
import java.util.logging.Logger;

public class BitbucketApiKey {

    private static final Logger LOGGER = Logger.getLogger(BitbucketApiKey.class.getName());


    private final String serverUrl;
    private final String ownerName;
    private final String repositoryName;

    public BitbucketApiKey(String serverUrl, String owner, String repository) {
        this.serverUrl = serverUrl;
        this.ownerName = owner;
        this.repositoryName = repository;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BitbucketApiKey)) return false;
        BitbucketApiKey that = (BitbucketApiKey) o;
        return Objects.equals(serverUrl, that.serverUrl) &&
                Objects.equals(ownerName, that.ownerName) &&
                Objects.equals(repositoryName, that.repositoryName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverUrl, ownerName, repositoryName);
    }

    public boolean equalsRepository(BitbucketRepository repository) {
        //LOGGER.info("["+repository.getOwnerName()+"]["+ownerName+"]["+repository.getRepositoryName()+"]["+repositoryName+"]");
        return ownerName.equalsIgnoreCase(repository.getOwnerName()) &&
               repositoryName.equalsIgnoreCase(repository.getRepositoryName());
    }
}
