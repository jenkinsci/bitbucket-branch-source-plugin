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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryType;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import java.io.ObjectStreamException;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadMigration;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.mixin.ChangeRequestSCMHead2;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * {@link SCMHead} for a Bitbucket pull request
 *
 * @since 2.0.0
 */
public class PullRequestSCMHead extends SCMHead implements ChangeRequestSCMHead2 {

    private static final String PR_BRANCH_PREFIX = "PR-";

    private static final long serialVersionUID = 1L;

    private final String repoOwner;

    private final String repository;

    private final String branchName;

    private final String number;

    private final String title;

    private final BranchSCMHead target;

    private final SCMHeadOrigin origin;

    private final ChangeRequestCheckoutStrategy strategy;

    public PullRequestSCMHead(String name, String repoOwner, String repository, String branchName,
                              String number, String title, BranchSCMHead target, SCMHeadOrigin origin,
                              ChangeRequestCheckoutStrategy strategy) {
        super(name);
        this.repoOwner = repoOwner;
        this.repository = repository;
        this.branchName = branchName;
        this.number = number;
        this.title = title;
        this.target = target;
        this.origin = origin;
        this.strategy = strategy;
    }

    public PullRequestSCMHead(String name, String repoOwner, String repository, String branchName,
                              BitbucketPullRequest pr, SCMHeadOrigin origin, ChangeRequestCheckoutStrategy strategy) {
        super(name);
        this.repoOwner = repoOwner;
        this.repository = repository;
        this.branchName = branchName;
        this.number = pr.getId();
        this.title = pr.getTitle();
        this.target = new BranchSCMHead(pr.getDestination().getBranch().getName());
        this.origin = origin;
        this.strategy = strategy;
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    public PullRequestSCMHead(String repoOwner, String repository, String branchName,
                              String number, BranchSCMHead target, SCMHeadOrigin origin) {
        this(PR_BRANCH_PREFIX + number, null, repoOwner, repository, branchName, number, target, origin,
                ChangeRequestCheckoutStrategy.HEAD);
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    public PullRequestSCMHead(String repoOwner, String repository, String branchName,
                              String number, String title, BranchSCMHead target, SCMHeadOrigin origin) {
        this(PR_BRANCH_PREFIX + number, repoOwner, repository, branchName, number, title, target, origin,
                ChangeRequestCheckoutStrategy.HEAD);
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    public PullRequestSCMHead(String repoOwner, String repository, String branchName,
                              String number, BranchSCMHead target) {
        this(repoOwner, repository, branchName, number, target, null);
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    public PullRequestSCMHead(String repoOwner, String repository, String branchName, BitbucketPullRequest pr) {
        this(repoOwner, repository, branchName, pr, null);
    }

    @Deprecated
    @Restricted(DoNotUse.class)
    public PullRequestSCMHead(String repoOwner, String repository, String branchName, BitbucketPullRequest pr,
                              SCMHeadOrigin origin) {
        this(PR_BRANCH_PREFIX + pr.getId(), repoOwner, repository, branchName, pr, origin,
            ChangeRequestCheckoutStrategy.HEAD);
    }

    @SuppressWarnings("deprecation")
    @SuppressFBWarnings("SE_PRIVATE_READ_RESOLVE_NOT_INHERITED") // because JENKINS-41313
    private Object readResolve() throws ObjectStreamException {
        if ("\u0000".equals(getTarget().getName())) {
            // this was a migration during upgrade to 2.0.0 but has not been rebuilt yet, let's see if we can fix it
            return new SCMHeadWithOwnerAndRepo.PR(repoOwner, repository, getBranchName(), number, target);
        }
        if (origin == null || strategy == null) {
            // this was a pre-2.2.0 head, let's see if we can populate the origin / strategy details
            return new FixLegacy(this);
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPronoun() {
        return Messages.PullRequestSCMHead_Pronoun();
    }

    public String getRepoOwner() {
        return repoOwner;
    }

    public String getRepository() {
        return repository;
    }

    public String getBranchName() {
        return branchName;
    }

    public BitbucketRepositoryType getRepositoryType() {
        return target.getRepositoryType();
    }

    @NonNull
    @Override
    public String getId() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    @NonNull
    @Override
    public SCMHead getTarget() {
        return target;
    }

    @NonNull
    @Override
    public ChangeRequestCheckoutStrategy getCheckoutStrategy() {
        return strategy;
    }

    @NonNull
    @Override
    public String getOriginName() {
        return branchName;
    }

    @NonNull
    @Override
    public SCMHeadOrigin getOrigin() {
        return origin == null ? SCMHeadOrigin.DEFAULT : origin;
    }

    /**
     * Used to handle data migration.
     *
     * @see FixLegacyMigration1
     * @deprecated used for data migration.
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    public static class FixLegacy extends PullRequestSCMHead {

        FixLegacy(PullRequestSCMHead copy) {
            super(copy.getName(), copy.repoOwner, copy.repository, copy.branchName, copy.number,
                    copy.getTitle(), copy.target, copy.getOrigin(), ChangeRequestCheckoutStrategy.HEAD);
        }
    }

    /**
     * Used to handle data migration.
     *
     * @deprecated used for data migration.
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    @Extension
    public static class FixLegacyMigration1 extends
            SCMHeadMigration<BitbucketSCMSource, FixLegacy, AbstractGitSCMSource.SCMRevisionImpl> {
        public FixLegacyMigration1() {
            super(BitbucketSCMSource.class, FixLegacy.class, AbstractGitSCMSource.SCMRevisionImpl.class);
        }

        @Override
        public PullRequestSCMHead migrate(@NonNull BitbucketSCMSource source, @NonNull FixLegacy head) {
            return new PullRequestSCMHead(
                    head.getName(),
                    head.getRepoOwner(),
                    head.getRepository(),
                    head.getBranchName(),
                    head.getId(),
                    head.getTitle(),
                    (BranchSCMHead) head.getTarget(),
                    source.originOf(head.getRepoOwner(), head.getRepository()),
                    ChangeRequestCheckoutStrategy.HEAD // legacy is always HEAD
            );
        }

        @Override
        public SCMRevision migrate(@NonNull BitbucketSCMSource source,
                                   @NonNull AbstractGitSCMSource.SCMRevisionImpl revision) {
            PullRequestSCMHead head = migrate(source, (FixLegacy) revision.getHead());
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
