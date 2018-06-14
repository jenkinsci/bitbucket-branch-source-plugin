package com.cloudbees.jenkins.plugins.bitbucket.server.client.cache;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBranch;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerAPIClient;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class BitbucketCache {

    private static final Logger LOGGER = Logger.getLogger(BitbucketCache.class.getName());

    public static final long TIMEOUT = 600_000L;
    public static final int MAX_SIZE=1000;

    private static BitbucketCache instance = new BitbucketCache();

    public Map<BitbucketApiKey,BitbucketServerAPIClient> bitbucketApiMap = new HashMap<>();

    private CachedObjects<BitbucketApiKey,BitbucketRepository> repositoryCachedObjects = new CachedObjects<>(TIMEOUT);
    private CachedObjects<BitbucketApiKey,List<? extends BitbucketBranch>> branchesCachedObjects = new CachedObjects<>(TIMEOUT);
    private CachedObjects<BitbucketApiKey,List<? extends BitbucketPullRequest>> pullRequestsCachedObjects = new CachedObjects<>(TIMEOUT);
    private CachedObjects<BitbucketApiKey,String> defaultBranchCachedObjects = new CachedObjects<>(TIMEOUT);

    private CachedObjects<BitbucketPathKey,Boolean> checkPaths = new CachedObjects<>(TIMEOUT);

    private CachedLRUObjects<String, BitbucketCommit> commits = new CachedLRUObjects<>(MAX_SIZE);


    public static BitbucketCache getInstance() {
        return instance;
    }

    public BitbucketApiKey getKey(String serverUrl, String owner, String repository, StandardUsernamePasswordCredentials credentials) {
        return new BitbucketApiKey(serverUrl, owner, repository, credentials);
    }

    public BitbucketServerAPIClient getBitbucketServerAPIClient(String serverUrl, String owner, String repository, StandardUsernamePasswordCredentials credentials) {
        BitbucketApiKey key = getKey(serverUrl, owner, repository, credentials);
        synchronized (instance) {
            BitbucketServerAPIClient delegate = instance.bitbucketApiMap.get(key);
            if (delegate == null ) {
                delegate = new BitbucketServerAPIClient(serverUrl, owner, repository, credentials, false);
                instance.bitbucketApiMap.put(key,delegate);
            }
            return delegate;
        }
    }

    public void invalidate(final BitbucketRepository repository) {
        LOGGER.info("Invalidating Cache" );
        CachedObjects.IFilter<BitbucketApiKey> filter = new CachedObjects.IFilter<BitbucketApiKey>() {

            @Override
            public boolean matches(BitbucketApiKey key) {
                return key.equalsRepository(repository);
            }
        };
        branchesCachedObjects.invalidate(filter);
        pullRequestsCachedObjects.invalidate(filter);

        CachedObjects.IFilter<BitbucketPathKey> filter1 = new CachedObjects.IFilter<BitbucketPathKey>() {
            @Override
            public boolean matches(BitbucketPathKey key) {
                return key.getApiKey().equalsRepository(repository);
            }
        };
        checkPaths.invalidate(filter1);
        LOGGER.info("Done Invalidating Cache");
    }

    public Map<BitbucketApiKey, BitbucketServerAPIClient> getBitbucketApiMap() {
        return bitbucketApiMap;
    }

    public CachedObjects<BitbucketApiKey, BitbucketRepository> getRepositoryCachedObjects() {
        return repositoryCachedObjects;
    }

    public CachedObjects<BitbucketApiKey, List<? extends BitbucketBranch>> getBranchesCachedObjects() {
        return branchesCachedObjects;
    }

    public CachedObjects<BitbucketApiKey, List<? extends BitbucketPullRequest>> getPullRequestsCachedObjects() {
        return pullRequestsCachedObjects;
    }

    public CachedObjects<BitbucketApiKey, String> getDefaultBranchCachedObjects() {
        return defaultBranchCachedObjects;
    }

    public CachedObjects<BitbucketPathKey, Boolean> getCheckPaths() {
        return checkPaths;
    }

    public CachedLRUObjects<String, BitbucketCommit> getCommits() {
        return commits;
    }
}
