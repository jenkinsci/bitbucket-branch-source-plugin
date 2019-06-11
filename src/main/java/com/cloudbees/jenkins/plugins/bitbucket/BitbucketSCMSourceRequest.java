/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBranch;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.TaskListener;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMSourceRequest;

/**
 * The {@link SCMSourceRequest} for bitbucket.
 *
 * @since 2.2.0
 */
public class BitbucketSCMSourceRequest extends SCMSourceRequest {
    /**
     * {@code true} if branch details need to be fetched.
     */
    private final boolean fetchBranches;
    /**
     * {@code true} if tag details need to be fetched.
     */
    private final boolean fetchTags;
    /**
     * {@code true} if origin pull requests need to be fetched.
     */
    private final boolean fetchOriginPRs;
    /**
     * {@code true} if fork pull requests need to be fetched.
     */
    private final boolean fetchForkPRs;
    /**
     * {@code true} if all pull requests from public repositories should be ignored.
     */
    private final boolean skipPublicPRs;
    /**
     * The {@link ChangeRequestCheckoutStrategy} to create for each origin pull request.
     */
    @NonNull
    private final Set<ChangeRequestCheckoutStrategy> originPRStrategies;
    /**
     * The {@link ChangeRequestCheckoutStrategy} to create for each fork pull request.
     */
    @NonNull
    private final Set<ChangeRequestCheckoutStrategy> forkPRStrategies;
    /**
     * The set of pull request numbers that the request is scoped to or {@code null} if the request is not limited.
     */
    @CheckForNull
    private final Set<String> requestedPullRequestNumbers;
    /**
     * The set of origin branch names that the request is scoped to or {@code null} if the request is not limited.
     */
    @CheckForNull
    private final Set<String> requestedOriginBranchNames;
    /**
     * The set of tag names that the request is scoped to or {@code null} if the request is not limited.
     */
    @CheckForNull
    private final Set<String> requestedTagNames;
    /**
     * The {@link BitbucketSCMSource#getRepoOwner()}.
     */
    @NonNull
    private final String repoOwner;
    /**
     * The {@link BitbucketSCMSource#getRepository()}.
     */
    @NonNull
    private final String repository;
    /**
     * The pull request details or {@code null} if not {@link #isFetchPRs()}.
     */
    @CheckForNull
    private Iterable<BitbucketPullRequest> pullRequests;
    /**
     * The branch details or {@code null} if not {@link #isFetchBranches()}.
     */
    @CheckForNull
    private Iterable<BitbucketBranch> branches;
    // TODO private Iterable<BitbucketTag> tags;
    /**
     * The BitbucketApi that is used for the request.
     */
    private BitbucketApi api;
    /**
     * The BitbucketSCMSource that is used for the request.
     */
    private final BitbucketSCMSource source;
    /**
     * A map serving as a cache of pull request IDs to the full set of data about the pull request.
     */
    private final Map<Integer, BitbucketPullRequest> pullRequestData;
    /**
     * The tag details or {@code null} if not {@link #isFetchTags()}.
     */
    @CheckForNull
    private Iterable<BitbucketBranch> tags;

    /**
     * {@code true} if the notifications are disabled.
     */
    @CheckForNull
    private final boolean disableNotifications;

    /**
     * Constructor.
     *
     * @param source   the source.
     * @param context  the context.
     * @param listener the listener.
     */
    protected BitbucketSCMSourceRequest(@NonNull final BitbucketSCMSource source,
                                        @NonNull BitbucketSCMSourceContext context,
                                        @CheckForNull TaskListener listener) {
        super(source, context, listener);
        this.source = source;
        fetchBranches = context.wantBranches();
        fetchTags = context.wantTags();
        fetchOriginPRs = context.wantOriginPRs();
        fetchForkPRs = context.wantForkPRs();
        skipPublicPRs = context.skipPublicPRs();
        originPRStrategies = fetchOriginPRs && !context.originPRStrategies().isEmpty()
                ? Collections.unmodifiableSet(EnumSet.copyOf(context.originPRStrategies()))
                : Collections.<ChangeRequestCheckoutStrategy>emptySet();
        forkPRStrategies = fetchForkPRs && !context.forkPRStrategies().isEmpty()
                ? Collections.unmodifiableSet(EnumSet.copyOf(context.forkPRStrategies()))
                : Collections.<ChangeRequestCheckoutStrategy>emptySet();
        Set<SCMHead> includes = context.observer().getIncludes();
        if (includes != null) {
            Set<String> pullRequestNumbers = new HashSet<>(includes.size());
            Set<String> branchNames = new HashSet<>(includes.size());
            Set<String> tagNames = new HashSet<>(includes.size());
            for (SCMHead h : includes) {
                if (h instanceof BranchSCMHead) {
                    branchNames.add(h.getName());
                } else if (h instanceof PullRequestSCMHead) {
                    pullRequestNumbers.add(((PullRequestSCMHead) h).getId());
                    if (SCMHeadOrigin.DEFAULT.equals(h.getOrigin())) {
                        branchNames.add(((PullRequestSCMHead) h).getOriginName());
                    }
                    if (((PullRequestSCMHead) h).getCheckoutStrategy() == ChangeRequestCheckoutStrategy.MERGE) {
                        branchNames.add(((PullRequestSCMHead) h).getTarget().getName());
                    }
                } else if (h instanceof BitbucketTagSCMHead) {
                    tagNames.add(h.getName());
                }
            }
            this.requestedPullRequestNumbers = Collections.unmodifiableSet(pullRequestNumbers);
            this.requestedOriginBranchNames = Collections.unmodifiableSet(branchNames);
            this.requestedTagNames = Collections.unmodifiableSet(tagNames);
        } else {
            requestedPullRequestNumbers = null;
            requestedOriginBranchNames = null;
            requestedTagNames = null;
        }
        repoOwner = source.getRepoOwner();
        repository = source.getRepository();
        pullRequestData = new HashMap<>();
        disableNotifications = context.notificationsDisabled();
    }

    /**
     * Returns {@code true} if notifications are disabled.
     *
     * @return {@code true} if notifications are disabled.
     */

    public final boolean isDisableNotifications() {
        return disableNotifications;
    }

    /**
     * Returns {@code true} if branch details need to be fetched.
     *
     * @return {@code true} if branch details need to be fetched.
     */
    public final boolean isFetchBranches() {
        return fetchBranches;
    }

    /**
     * Returns {@code true} if tag details need to be fetched.
     *
     * @return {@code true} if tag details need to be fetched.
     */
    public final boolean isFetchTags() {
        return fetchTags;
    }

    /**
     * Returns {@code true} if pull request details need to be fetched.
     *
     * @return {@code true} if pull request details need to be fetched.
     */
    public final boolean isFetchPRs() {
        return isFetchOriginPRs() || isFetchForkPRs();
    }

    /**
     * Returns {@code true} if origin pull request details need to be fetched.
     *
     * @return {@code true} if origin pull request details need to be fetched.
     */
    public final boolean isFetchOriginPRs() {
        return fetchOriginPRs;
    }

    /**
     * Returns {@code true} if fork pull request details need to be fetched.
     *
     * @return {@code true} if fork pull request details need to be fetched.
     */
    public final boolean isFetchForkPRs() {
        return fetchForkPRs;
    }

    /**
     * Returns {@code true} if pull requests from public repositories should be skipped.
     *
     * @return {@code true} if pull requests from public repositories should be skipped.
     */
    public final boolean isSkipPublicPRs() {
        return skipPublicPRs;
    }

    /**
     * Returns the {@link ChangeRequestCheckoutStrategy} to create for each origin pull request.
     *
     * @return the {@link ChangeRequestCheckoutStrategy} to create for each origin pull request.
     */
    @NonNull
    public final Set<ChangeRequestCheckoutStrategy> getOriginPRStrategies() {
        return originPRStrategies;
    }

    /**
     * Returns the {@link ChangeRequestCheckoutStrategy} to create for each fork pull request.
     *
     * @return the {@link ChangeRequestCheckoutStrategy} to create for each fork pull request.
     */
    @NonNull
    public final Set<ChangeRequestCheckoutStrategy> getForkPRStrategies() {
        return forkPRStrategies;
    }

    /**
     * Returns the {@link ChangeRequestCheckoutStrategy} to create for pull requests of the specified type.
     *
     * @param fork {@code true} to return strategies for the fork pull requests, {@code false} for origin pull requests.
     * @return the {@link ChangeRequestCheckoutStrategy} to create for each pull request.
     */
    @NonNull
    public final Set<ChangeRequestCheckoutStrategy> getPRStrategies(boolean fork) {
        if (fork) {
            return fetchForkPRs ? getForkPRStrategies() : Collections.<ChangeRequestCheckoutStrategy>emptySet();
        }
        return fetchOriginPRs ? getOriginPRStrategies() : Collections.<ChangeRequestCheckoutStrategy>emptySet();
    }

    /**
     * Returns the {@link ChangeRequestCheckoutStrategy} to create for each pull request.
     *
     * @return a map of the {@link ChangeRequestCheckoutStrategy} to create for each pull request keyed by whether the
     * strategy applies to forks or not ({@link Boolean#FALSE} is the key for origin pull requests)
     */
    public final Map<Boolean, Set<ChangeRequestCheckoutStrategy>> getPRStrategies() {
        Map<Boolean, Set<ChangeRequestCheckoutStrategy>> result = new HashMap<>();
        for (Boolean fork : new Boolean[]{Boolean.TRUE, Boolean.FALSE}) {
            result.put(fork, getPRStrategies(fork));
        }
        return result;
    }

    /**
     * Returns requested pull request numbers.
     *
     * @return the requested pull request numbers or {@code null} if the request was not scoped to a subset of pull
     * requests.
     */
    @CheckForNull
    public final Set<String> getRequestedPullRequestNumbers() {
        return requestedPullRequestNumbers;
    }

    /**
     * Gets requested origin branch names.
     *
     * @return the requested origin branch names or {@code null} if the request was not scoped to a subset of branches.
     */
    @CheckForNull
    public final Set<String> getRequestedOriginBranchNames() {
        return requestedOriginBranchNames;
    }

    /**
     * Gets requested tag names.
     *
     * @return the requested tag names or {@code null} if the request was not scoped to a subset of tags.
     */
    @CheckForNull
    public final Set<String> getRequestedTagNames() {
        return requestedTagNames;
    }

    /**
     * Returns the {@link BitbucketSCMSource#getRepoOwner()}
     *
     * @return the {@link BitbucketSCMSource#getRepoOwner()}
     */
    @NonNull
    public final String getRepoOwner() {
        return repoOwner;
    }

    /**
     * Returns the {@link BitbucketSCMSource#getRepository()}.
     *
     * @return the {@link BitbucketSCMSource#getRepository()}.
     */
    @NonNull
    public final String getRepository() {
        return repository;
    }

    /**
     * Provides the requests with the pull request details.
     *
     * @param pullRequests the pull request details.
     */
    public final void setPullRequests(@CheckForNull Iterable<BitbucketPullRequest> pullRequests) {
        this.pullRequests = pullRequests;
    }

    /**
     * Returns the pull request details or an empty list if either the request did not specify to {@link #isFetchPRs()}
     * or if the pull request details have not been provided by {@link #setPullRequests(Iterable)} yet.
     *
     * @return the pull request details (may be empty)
     */
    @NonNull
    public final Iterable<BitbucketPullRequest> getPullRequests() {
        return Util.fixNull(pullRequests);
    }

    /**
     * Retrieves the full details of a pull request.
     * @param id The id of the pull request to retrieve the details about.
     * @return The {@link BitbucketPullRequest} object.
     * @throws IOException If the request to retrieve the full details encounters an issue.
     * @throws InterruptedException If the request to retrieve the full details is interrupted.
     */
    @SuppressWarnings("unused") // Used by extension trait plugin
    public final BitbucketPullRequest getPullRequestById(Integer id) throws IOException, InterruptedException {
        if (!pullRequestData.containsKey(id)) {
            pullRequestData.put(id, getBitbucketApiClient().getPullRequestById(id));
        }

        return pullRequestData.get(id);
    }

    private final BitbucketApi getBitbucketApiClient() {
        if (api == null) {
            api = source.buildBitbucketClient();
        }

        return api;
    }

    /**
     * Provides the requests with the branch details.
     *
     * @param branches the branch details.
     */
    public final void setBranches(@CheckForNull Iterable<BitbucketBranch> branches) {
        this.branches = branches;
    }

    /**
     * Returns the branch details or an empty list if either the request did not specify to {@link #isFetchBranches()}
     * or if the branch details have not been provided by {@link #setBranches(Iterable)} yet.
     *
     * @return the branch details (may be empty)
     */
    @NonNull
    public final Iterable<BitbucketBranch> getBranches() {
        return Util.fixNull(branches);
    }

    /**
     * Provides the requests with the tag details.
     *
     * @param tags the tag details.
     */
    public final void setTags(@CheckForNull Iterable<BitbucketBranch> tags) {
        this.tags = tags;
    }

    /**
     * Returns the branch details or an empty list if either the request did not specify to {@link #isFetchTags()}
     * or if the tag details have not been provided by {@link #setTags(Iterable)} yet.
     *
     * @return the tag details (may be empty)
     */
    @NonNull
    public final Iterable<BitbucketBranch> getTags() {
        return Util.fixNull(tags);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        if (pullRequests instanceof Closeable) {
            ((Closeable) pullRequests).close();
        }
        if (branches instanceof Closeable) {
            ((Closeable) branches).close();
        }
        super.close();
    }
}
