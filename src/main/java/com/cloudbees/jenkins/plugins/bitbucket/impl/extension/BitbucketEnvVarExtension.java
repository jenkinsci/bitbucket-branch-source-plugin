package com.cloudbees.jenkins.plugins.bitbucket.impl.extension;

import com.cloudbees.jenkins.plugins.bitbucket.impl.util.URLUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import java.util.Map;
import java.util.Objects;
import org.kohsuke.stapler.DataBoundConstructor;

public class BitbucketEnvVarExtension extends GitSCMExtension {

    private final String owner;
    private final String repository;
    private final String projectKey;
    private final String serverURL;

    @DataBoundConstructor
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
        env.put("BITBUCKET_REPOSITORY", getRepository());
        env.put("BITBUCKET_OWNER", getOwner());
        env.put("BITBUCKET_PROJECT_KEY", getProjectKey());
        env.put("BITBUCKET_SERVER_URL", getServerURL());
    }

    public String getOwner() {
        return owner;
    }

    public String getRepository() {
        return repository;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getServerURL() {
        return serverURL;
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, projectKey, repository, serverURL);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        BitbucketEnvVarExtension other = (BitbucketEnvVarExtension) obj;
        return Objects.equals(owner, other.owner)
                && Objects.equals(projectKey, other.projectKey)
                && Objects.equals(repository, other.repository)
                && Objects.equals(serverURL, other.serverURL);
    }

    @Extension
    // No @Symbol because Pipeline users should not configure this in other ways than this plugin provides
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Contribute additional environment variables about the target branch.";
        }
    }
}
