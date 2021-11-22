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
package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadMigration;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Legacy class retained to allow for graceful migration of serialized data.
 * @deprecated use {@link PullRequestSCMHead} or {@link BranchSCMHead}
 */
@Deprecated
public class SCMHeadWithOwnerAndRepo extends SCMHead {

    private static final Logger LOGGER = Logger.getLogger(SCMHeadWithOwnerAndRepo.class.getName());
    private static final Map<BitbucketSCMSource, SoftReference<Map<String, String>>> cache = new WeakHashMap<>();

    private static final long serialVersionUID = 1L;

    private final String repoOwner;

    private final String repoName;

    private transient PullRequestAction metadata;

    public SCMHeadWithOwnerAndRepo(String repoOwner, String repoName, String branchName) {
        super(branchName);
        this.repoOwner = repoOwner;
        this.repoName = repoName;
    }

    private Object readResolve() throws ObjectStreamException {
        if (metadata != null) {
            // we just want to flag this as a PR, the legacy data did not contain the required information
            // then the temporary PR class will be resolved by GitMigrationImpl when the
            // context to look up the correct target is (hopefully) available. If the context is not available
            // then worst case  we will end up triggering a rebuild on next index / event via take-over
            return new PR(repoOwner, repoName, getName(), metadata.getId(), new BranchSCMHead("\u0000"));
        }
        return new BranchSCMHead(getName());
    }

    /**
     * Marker class to ensure that we do not attempt to apply a {@link SCMHeadMigration} on all
     * {@link PullRequestSCMHead} instances, rather we only apply it on ones that need migration. We need to use a
     * {@link ChangeRequestSCMHead} in order to retain the correct categorization of {@link SCMHead} instances
     * in the event that the {@link GitMigrationImpl} fail to resolve the target.
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    public static class PR extends PullRequestSCMHead {

        public PR(String repoOwner, String repository, String branchName, String number,
                  BranchSCMHead target) {
            super(repoOwner, repository, branchName, number, target, null);
        }
    }

    private static Map<String, String> getTargets(BitbucketSCMSource source) {
        synchronized (cache) {
            SoftReference<Map<String, String>> ref = cache.get(source);
            Map<String, String> targets = ref == null ? null : ref.get();
            if (targets != null) {
                return targets;
            }
        }
        Map<String, String> targets = new HashMap<>();
        try {
            final BitbucketApi bitbucket = BitbucketApiFactory.newInstance(
                    source.getServerUrl(),
                    source.authenticator(),
                    source.getRepoOwner(),
                    source.getRepository()
            );
            for (BitbucketPullRequest pr : bitbucket.getPullRequests()) {
                targets.put(pr.getId(), pr.getDestination().getBranch().getName());
            }
        } catch (RuntimeException | IOException | InterruptedException e) {
            // log this at fine as we give the usage based detail later.
            LOGGER.log(Level.FINE, "Cannot resolve pull request targets", e);
        }
        synchronized (cache) {
            cache.put(source, new SoftReference<>(targets));
        }
        return targets;
    }

    @Restricted(NoExternalUse.class)
    @Extension
    public static class GitMigrationImpl extends SCMHeadMigration<BitbucketSCMSource, PR, AbstractGitSCMSource.SCMRevisionImpl>{

        public GitMigrationImpl() {
            super(BitbucketSCMSource.class, PR.class, AbstractGitSCMSource.SCMRevisionImpl.class);
        }

        @Override
        public PullRequestSCMHead migrate(@NonNull BitbucketSCMSource source, @NonNull PR head) {
            Map<String, String> targets = getTargets(source);
            String target = targets.get(head.getId());
            if (target == null) {
                LOGGER.log(Level.WARNING, "Could not determine target branch for PR {0}. This may result in a rebuild",
                        head.getId());
                target = "\u0000";
            }
            return new PullRequestSCMHead(
                    head.getName(),
                    head.getRepoOwner(),
                    head.getRepository(),
                    head.getBranchName(),
                    head.getId(),
                    head.getTitle(),
                    new BranchSCMHead(target),
                    source.originOf(head.getRepoOwner(), head.getRepository()),
                    ChangeRequestCheckoutStrategy.HEAD
            );
        }

        @Override
        public SCMRevision migrate(@NonNull BitbucketSCMSource source,
                                   @NonNull AbstractGitSCMSource.SCMRevisionImpl revision) {
            PullRequestSCMHead head = migrate(source, (PR) revision.getHead());
            return head != null ? new PullRequestSCMRevision<>(head,
                    // ChangeRequestCheckoutStrategy.HEAD means we ignore the target revision,
                    // so we can leave it null as a placeholder
                    new AbstractGitSCMSource.SCMRevisionImpl(head.getTarget(), null),
                    new AbstractGitSCMSource.SCMRevisionImpl(head, revision.getHash()
                    )
            ) : null;
        }
    }

}
