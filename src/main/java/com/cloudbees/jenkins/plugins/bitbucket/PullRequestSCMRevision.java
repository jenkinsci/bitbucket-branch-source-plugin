package com.cloudbees.jenkins.plugins.bitbucket;

import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMRevision;
import javax.annotation.Nonnull;

/**
 * Revision of a Pull Request
 */
public class PullRequestSCMRevision extends AbstractGitSCMSource.SCMRevisionImpl {

    private static final long serialVersionUID = 1L;

    private final @Nonnull String baseHash;

    PullRequestSCMRevision(@Nonnull PullRequestSCMHead head,@Nonnull String baseHash,@Nonnull String pullHash) {
        super(head,pullHash);
        this.baseHash = baseHash;
    }

    /**
     * The commit hash of the base branch we are tracking.
     * If {@link PullRequestSCMHead#isMerge}, this would be the current head of the base branch.
     * Otherwise it would be the PR’s {@code .base.sha}, the common ancestor of the PR branch and the base branch.
     * @return String of the base Hash commit of the PR
     */
    public @Nonnull String getBaseHash() {
        return baseHash;
    }

    /**
     * @return The commit hash of the head of the pull request branch.
     */
    public @Nonnull String getPullHash() {
        return getHash();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PullRequestSCMRevision)) {
            return false;
        }
        PullRequestSCMRevision other = (PullRequestSCMRevision) o;
        return getHead().equals(other.getHead()) && baseHash.equals(other.baseHash) && getPullHash().equals(other.getPullHash());
    }

    @Override
    public int hashCode() {
        return getHash().hashCode();
    }

    @Override
    public String toString() {
        return getHead() instanceof PullRequestSCMHead && ((PullRequestSCMHead) getHead()).isMerge() ? getPullHash() + "+" + baseHash : getPullHash();
    }

}
