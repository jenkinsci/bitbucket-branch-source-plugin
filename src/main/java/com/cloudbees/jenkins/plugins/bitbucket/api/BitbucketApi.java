/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.jenkins.plugins.bitbucket.api;

import com.cloudbees.jenkins.plugins.bitbucket.client.repository.UserRoleInRepository;
import com.cloudbees.jenkins.plugins.bitbucket.filesystem.BitbucketSCMFile;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import jenkins.scm.api.SCMFile;
import jenkins.scm.impl.avatars.AvatarImage;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Provides access to a specific repository.
 * One API object needs to be created for each repository you want to work with.
 */
public interface BitbucketApi extends AutoCloseable {

    /**
     * Returns the owner name for the repository.
     *
     * @return the repository owner name.
     */
    @NonNull
    String getOwner();

    /**
     * Returns the repository name.
     *
     * @return the repository name.
     */
    @CheckForNull
    String getRepositoryName();

    /**
     * Returns the pull requests in the repository.
     *
     * @return the list of pull requests in the repository.
     * @throws IOException if there was a network communications error.
     * @throws InterruptedException if interrupted while waiting on remote communications.
     */
    @NonNull
    List<? extends BitbucketPullRequest> getPullRequests() throws IOException, InterruptedException;

    /**
     * Returns a specific pull request.
     *
     * @param id the pull request ID
     * @return the pull request or null if the PR does not exist
     * @throws IOException if there was a network communications error.
     */
    @NonNull
    BitbucketPullRequest getPullRequestById(@NonNull Integer id) throws IOException;

    /**
     * Returns the repository details.
     *
     * @return the repository specified by {@link #getOwner()}/{@link #getRepositoryName()}
     *      (or null if repositoryName is not set)
     * @throws IOException if there was a network communications error.
     */
    @NonNull
    BitbucketRepository getRepository() throws IOException;

    /**
     * Post a comment to a given commit hash.
     *
     * @param hash commit hash
     * @param comment string to post as comment
     * @throws IOException if there was a network communications error.
     */
    void postCommitComment(@NonNull String hash, @NonNull String comment) throws IOException;

    /**
     * Checks if the given path exists in the repository at the specified branch.
     *
     * @param branchOrHash the branch name or commit hash
     * @param path the path to check for
     * @return true if the path exists
     * @throws IOException if there was a network communications error.
     */
    boolean checkPathExists(@NonNull String branchOrHash, @NonNull String path)
            throws IOException;

    /**
     * Gets the default branch in the repository.
     *
     * @return the default branch in the repository or null if no default branch set
     * @throws IOException if there was a network communications error.
     */
    @CheckForNull
    String getDefaultBranch() throws IOException;

    /**
     * Returns a branch in the repository.
     *
     * @return a branch in the repository.
     * @throws IOException if there was a network communications error.
     */
    @CheckForNull
    BitbucketBranch getBranch(@NonNull String branchName) throws IOException;

    /**
     * Returns the branches in the repository.
     *
     * @return the list of branches in the repository.
     * @throws IOException if there was a network communications error.
     * @throws InterruptedException if interrupted while waiting on remote communications.
     */
    @NonNull
    List<? extends BitbucketBranch> getBranches() throws IOException, InterruptedException;

    /**
     * Returns a tag in the repository.
     *
     * @return a tag in the repository.
     * @throws IOException if there was a network communications error.
     */
    @CheckForNull
    BitbucketBranch getTag(@NonNull String tagName) throws IOException;

     /**
     * Returns the tags in the repository.
     *
     * @return the list of tags in the repository.
     * @throws IOException if there was a network communications error.
     * @throws InterruptedException if interrupted while waiting on remote communications.
     */
    @NonNull
    List<? extends BitbucketBranch> getTags() throws IOException, InterruptedException;

    /**
     * Resolve the commit object given its hash.
     *
     * @param hash the hash to resolve
     * @return the commit object or null if the hash does not exist
     * @throws IOException if there was a network communications error.
     */
    @CheckForNull
    BitbucketCommit resolveCommit(@NonNull String hash) throws IOException;

    /**
     * Resolve the head commit object of the pull request source repository branch.
     *
     * @param pull the pull request to resolve the source hash from
     * @return the source head commit object
     * @throws IOException if there was a network communications error.
     * @since 2.2.14
     */
    @NonNull
    BitbucketCommit resolveCommit(@NonNull BitbucketPullRequest pull) throws IOException;

    /**
     * Resolve the head commit hash of the pull request source repository branch.
     *
     * @param pull the pull request to resolve the source hash from
     * @return the source head hash
     * @throws IOException if there was a network communications error.
     */
    @NonNull
    String resolveSourceFullHash(@NonNull BitbucketPullRequest pull) throws IOException;

    /**
     * Register a webhook on the repository.
     *
     * @param hook the webhook object
     * @throws IOException if there was a network communications error.
     */
    void registerCommitWebHook(@NonNull BitbucketWebHook hook) throws IOException;

    /**
     * Update a webhook on the repository.
     *
     * @param hook the webhook object
     * @throws IOException if there was a network communications error.
     */
    void updateCommitWebHook(@NonNull BitbucketWebHook hook) throws IOException;

    /**
     * Remove the webhook (ID field required) from the repository.
     *
     * @param hook the webhook object
     * @throws IOException if there was a network communications error.
     */
    void removeCommitWebHook(@NonNull BitbucketWebHook hook) throws IOException;

    /**
     * Returns the webhooks defined in the repository.
     *
     * @return the list of webhooks registered in the repository.
     * @throws IOException if there was a network communications error.
     */
    @NonNull
    List<? extends BitbucketWebHook> getWebHooks() throws IOException;

    /**
     * Returns the team of the current owner or {@code null} if the current owner is not a team.
     *
     * @return the team profile of the current owner, or {@code null} if {@link #getOwner()} is not a team ID.
     * @throws IOException if there was a network communications error.
     */
    @CheckForNull
    BitbucketTeam getTeam() throws IOException;

    /**
     * Returns the team Avatar of the current owner or {@code null} if the current owner is not a team.
     *
     * @return the team profile of the current owner, or {@code null} if {@link #getOwner()} is not a team ID.
     * @throws IOException  if there was a network communications error.
     * @deprecated Use {@link #getAvatar(String)} with the avatar url link gather from repository, project, workspace or user.
     */
    @CheckForNull
    @Deprecated(since = "935.0.0", forRemoval = true)
    AvatarImage getTeamAvatar() throws IOException;

    /**
     * Returns an Avatar image from the given URL.
     * <p>
     * The URL link could come from repository, project, workspace or user links.
     *
     * @return the resource image.
     * @throws IOException  if there was a network communications error.
     */
    @CheckForNull
    AvatarImage getAvatar(@NonNull String url) throws IOException;

    /**
     * Returns the repositories where the user has the given role.
     *
     * @param role Filter repositories by the owner having this role in.
     *             See {@link UserRoleInRepository} for more information.
     *             Use role = null if the repoOwner is a team ID.
     * @return the repositories list (it can be empty)
     * @throws IOException if there was a network communications error.
     * @throws InterruptedException if interrupted while waiting on remote communications.
     */
    @NonNull
    List<? extends BitbucketRepository> getRepositories(@CheckForNull UserRoleInRepository role)
            throws IOException, InterruptedException;

    /**
     * Returns all the repositories for the current owner (even if it's a regular user or a team).
     *
     * @return all repositories for the current {@link #getOwner()}
     * @throws IOException if there was a network communications error.
     * @throws InterruptedException if interrupted while waiting on remote communications.
     */
    @NonNull
    List<? extends BitbucketRepository> getRepositories() throws IOException, InterruptedException;

    /**
     * Set the build status for the given commit hash.
     *
     * @param status the status object to be serialized
     * @throws IOException if there was a network communications error.
     */
    void postBuildStatus(@NonNull BitbucketBuildStatus status) throws IOException;

    /**
     * Returns {@code true} if and only if the repository is private.
     *
     * @return {@code true} if the repository ({@link #getOwner()}/{@link #getRepositoryName()}) is private.
     * @throws IOException if there was a network communications error.
     */
    boolean isPrivate() throws IOException;

    /**
     * Returns a list of all children file for the given folder.
     *
     * @param parent to list
     * @return an iterable of {@link SCMFile} children of the given folder.
     * @throws IOException if there was a network communications error.
     * @throws InterruptedException if interrupted while waiting on remote communications.
     */
    @NonNull
    @Restricted(NoExternalUse.class)
    Iterable<SCMFile> getDirectoryContent(BitbucketSCMFile parent) throws IOException, InterruptedException;

    /**
     * Return an input stream for the given file.
     *
     * @param file an instance of SCM file
     * @return the stream of the given {@link SCMFile}
     * @throws IOException if there was a network communications error.
     */
    @Restricted(NoExternalUse.class)
    InputStream getFileContent(BitbucketSCMFile file) throws IOException;

    /**
     * Return the metadata for the given file.
     *
     * @param file an instance of SCM file
     * @return a {@link SCMFile} file with updated the metadata
     * @throws IOException if there was a network communications error.
     */
    @NonNull
    @Restricted(NoExternalUse.class)
    SCMFile getFile(@NonNull BitbucketSCMFile file) throws IOException;

    /**
     * {@inheritDoc}
     */
    @Override
    void close() throws IOException;

    /**
     * Return a set of base informations between the two given commits.
     *
     * @param from the commit or reference containing the changes we wish to
     *        preview or {@code null} to get changes since the beginning.
     * @param to the commit or reference representing the state to which we want
     *        to compare the first commit
     * @return the list of commit between first commit and second source commit.
     * @throws IOException if there was a network communications error.
     */
    @NonNull
    List<? extends BitbucketCommit> getCommits(@CheckForNull String from, @NonNull String to) throws IOException;

//    /**
//     * Return a set changes between the two given commits.
//     *
//     * @param firstCommit the commit containing the changes we wish to preview
//     *        or {@code null} to use the unique parent of secondCommit
//     * @param secondCommit the commit representing the state to which we want to
//     *        compare the first commit
//     * @return the file changes between first commit and second source commit.
//     * @throws IOException if there was a network communications error.
//     */
//    @NonNull
//    List<BitbucketCloudCommitDiffStat> getCommitsChanges(String fromCommit, String toCommit) throws IOException;

}
