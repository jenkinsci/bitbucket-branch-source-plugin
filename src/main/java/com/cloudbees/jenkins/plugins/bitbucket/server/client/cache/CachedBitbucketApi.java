package com.cloudbees.jenkins.plugins.bitbucket.server.client.cache;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBranch;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryProtocol;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryType;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketTeam;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.UserRoleInRepository;
import com.cloudbees.jenkins.plugins.bitbucket.filesystem.BitbucketSCMFile;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerAPIClient;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Callable;
import jenkins.scm.api.SCMFile;

public class CachedBitbucketApi implements BitbucketApi {
    private final BitbucketServerAPIClient delegate;
    private final BitbucketApiKey key;

    public CachedBitbucketApi(String serverUrl, @Nullable BitbucketAuthenticator authenticator, String owner, String repository) {

        BitbucketCache instance = BitbucketCache.getInstance();

        key = instance.getKey(serverUrl, owner, repository);
        delegate = instance.getBitbucketServerAPIClient(serverUrl, owner, repository, authenticator);
    }

    @NonNull
    @Override
    public String getOwner() {
        return delegate.getOwner();
    }

    @Override
    public String getRepositoryName() {
        return delegate.getRepositoryName();
    }

    @NonNull
    @Override
    public String getRepositoryUri(@NonNull BitbucketRepositoryType type, @NonNull BitbucketRepositoryProtocol protocol, String cloneLink, @NonNull String owner, @NonNull String repository) {
        return delegate.getRepositoryUri(type,protocol,cloneLink,owner,repository);
    }


    @NonNull
    @Override
    public List<? extends BitbucketPullRequest> getPullRequests() throws IOException, InterruptedException {
        try {
            Callable<List<? extends BitbucketPullRequest>> calculateBranches = new Callable<List<? extends BitbucketPullRequest>>() {

                @Override
                public List<? extends BitbucketPullRequest> call() throws Exception {
                    return delegate.getPullRequests();
                }
            };
            return BitbucketCache.getInstance().getPullRequestsCachedObjects().get(key, calculateBranches );
        } catch (Exception e) {
            throw  new IOException("Error gettting PullRequests",e);
        }
    }

    @NonNull
    @Override
    public BitbucketPullRequest getPullRequestById(@NonNull Integer id) throws IOException, InterruptedException {
        return delegate.getPullRequestById(id);
    }

    @NonNull
    @Override
    public BitbucketRepository getRepository() throws IOException, InterruptedException {
        try {
            Callable<BitbucketRepository> cacheRepository = new Callable<BitbucketRepository>() {
                @Override
                public BitbucketRepository call() throws Exception {
                    return delegate.getRepository();
                }
            };
            return BitbucketCache.getInstance().getRepositoryCachedObjects().get(key,cacheRepository);
        } catch (Exception e) {
            throw new IOException("Error getting Repository",e);
        }
    }

    @Override
    public void postCommitComment(@NonNull String hash, @NonNull String comment) throws IOException, InterruptedException {
        delegate.postCommitComment(hash, comment);
    }

    @Override
    public boolean checkPathExists(@NonNull String branchOrHash, @NonNull String path) throws IOException, InterruptedException {
        try {
            final String hash = branchOrHash;
            final String p = path;
            Callable<Boolean> cacheCheckPathExists = new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return delegate.checkPathExists(hash, p);
                }
            };
            return BitbucketCache.getInstance().getCheckPaths().get(new BitbucketPathKey(key,hash,p),cacheCheckPathExists);
        } catch (Exception e) {
            throw new IOException("Error checkPathExists",e);
        }
    }

    @Override
    public String getDefaultBranch() throws IOException, InterruptedException {
        try {
            Callable<String> cacheDefaultBranch = new Callable<String>() {
                @Override
                public String call() throws Exception {
                    return delegate.getDefaultBranch();
                }
            };
            return BitbucketCache.getInstance().getDefaultBranchCachedObjects().get(key,cacheDefaultBranch);
        } catch (Exception e) {
            throw new IOException("Error getDefaultBranch",e);
        }
    }

    @NonNull
    @Override
    public List<? extends BitbucketBranch> getBranches() throws IOException, InterruptedException {
        try {
            Callable<List<? extends BitbucketBranch>> calculateBranches = new Callable<List<? extends BitbucketBranch>>() {

                @Override
                public List<? extends BitbucketBranch> call() throws Exception {
                    return delegate.getBranches();
                }
            };
            return BitbucketCache.getInstance().getBranchesCachedObjects().get(key, calculateBranches);
        } catch (Exception e) {
            throw  new IOException("Error gettting Branches",e);
        }
    }

    @NonNull
    @Override
    public List<? extends BitbucketBranch> getTags() throws IOException, InterruptedException {
        return delegate.getTags();
    }

    @Override
    public BitbucketCommit resolveCommit(@NonNull String hash) throws IOException, InterruptedException {
        try {
            final String h = hash;
            Callable<BitbucketCommit> resolveCommit = new Callable<BitbucketCommit>() {
                @Override
                public BitbucketCommit call() throws Exception {
                    return delegate.resolveCommit(h);
                }
            };
            return BitbucketCache.getInstance().getCommits().get(hash,resolveCommit);
        } catch (Exception e) {
            throw  new IOException("Error resolveCommit "+hash,e);
        }
    }

    @NonNull
    @Override
    public BitbucketCommit resolveCommit(@NonNull BitbucketPullRequest pull) throws IOException, InterruptedException {
        return delegate.resolveCommit(pull);
    }

    @NonNull
    @Override
    public String resolveSourceFullHash(@NonNull BitbucketPullRequest pull) throws IOException, InterruptedException {
        return delegate.resolveSourceFullHash(pull);
    }

    @Override
    public void registerCommitWebHook(@NonNull BitbucketWebHook hook) throws IOException, InterruptedException {
        delegate.registerCommitWebHook(hook);
    }

    @Override
    public void updateCommitWebHook(@NonNull BitbucketWebHook hook) throws IOException, InterruptedException {
        delegate.updateCommitWebHook(hook);
    }

    @Override
    public void removeCommitWebHook(@NonNull BitbucketWebHook hook) throws IOException, InterruptedException {
        delegate.removeCommitWebHook(hook);
    }

    @NonNull
    @Override
    public List<? extends BitbucketWebHook> getWebHooks() throws IOException, InterruptedException {
        return delegate.getWebHooks();
    }

    @Override
    public BitbucketTeam getTeam() throws IOException, InterruptedException {
        return delegate.getTeam();
    }

    @NonNull
    @Override
    public List<? extends BitbucketRepository> getRepositories(UserRoleInRepository role) throws IOException, InterruptedException {
        return delegate.getRepositories(role);
    }

    @NonNull
    @Override
    public List<? extends BitbucketRepository> getRepositories() throws IOException, InterruptedException {
        return delegate.getRepositories();
    }

    @Override
    public void postBuildStatus(@NonNull BitbucketBuildStatus status) throws IOException, InterruptedException {
        delegate.postBuildStatus(status);
    }

    @Override
    public boolean isPrivate() throws IOException, InterruptedException {
        return delegate.isPrivate();
    }

    @Override
    public Iterable<SCMFile> getDirectoryContent(BitbucketSCMFile parent) throws IOException, InterruptedException {
        return delegate.getDirectoryContent(parent);
    }

    @Override
    public InputStream getFileContent(BitbucketSCMFile file) throws IOException, InterruptedException {
        return delegate.getFileContent(file);
    }
}
