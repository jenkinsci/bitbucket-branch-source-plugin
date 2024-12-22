package com.cloudbees.jenkins.plugins.bitbucket.impl.extension;

import com.cloudbees.jenkins.plugins.bitbucket.impl.util.URLUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import java.util.Map;

public class BitbucketEnvVarExtension extends GitSCMExtension {

    private final String owner;
    private final String repository;
    private final String projectKey;
    private final String serverURL;

    public BitbucketEnvVarExtension(@Nullable String owner, @NonNull String repository, @Nullable String projectKey, @NonNull String serverURL) {
        this.owner = owner;
        this.repository = repository;
        this.projectKey = projectKey;
        this.serverURL = URLUtils.removeAuthority(serverURL);
    }

    /**
     * Contribute additional environment variables about the target branch.
     * Since source branch could be from a forked repository, for which the
     * credentials in use are not allowed to do nothing, is discarded.
     *
     * @param scm GitSCM used as reference
     * @param env environment variables to be added
     */
    @Override
    public void populateEnvironmentVariables(GitSCM scm, Map<String, String> env) {
        env.put("BITBUCKET_REPOSITORY", repository);
        env.put("BITBUCKET_OWNER", owner);
        env.put("BITBUCKET_PROJECT_KEY", projectKey);
        env.put("BITBUCKET_SERVER_URL", serverURL);
    }
}
