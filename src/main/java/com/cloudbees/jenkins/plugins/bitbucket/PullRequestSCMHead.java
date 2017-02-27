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
import java.io.ObjectStreamException;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;
import jenkins.scm.api.SCMHead;

/**
 * {@link SCMHead} for a BitBucket Pull request
 * @since FIXME
 */
public class PullRequestSCMHead extends SCMHead implements ChangeRequestSCMHead {

    private static final String PR_BRANCH_PREFIX = "PR-";

    private static final long serialVersionUID = 1L;

    private final String repoOwner;

    private final String repository;

    private final String branchName;

    private final String number;

    private final BranchSCMHead target;

    public PullRequestSCMHead(String repoOwner, String repository, String branchName,
                              String number, BranchSCMHead target) {
        super(PR_BRANCH_PREFIX + number);
        this.repoOwner = repoOwner;
        this.repository = repository;
        this.branchName = branchName;
        this.number = number;
        this.target = target;
    }

    public PullRequestSCMHead(String repoOwner, String repository, String branchName, BitbucketPullRequest pr) {
        this(repoOwner, repository, null, branchName, pr);
    }

    public PullRequestSCMHead(String repoOwner, String repository, BitbucketRepositoryType repositoryType, String branchName, BitbucketPullRequest pr) {
        super(PR_BRANCH_PREFIX + pr.getId());
        this.repoOwner = repoOwner;
        this.repository = repository;
        this.branchName = branchName;
        this.number = pr.getId();
        this.target = new BranchSCMHead(pr.getDestination().getBranch().getName(), repositoryType);
    }

    @SuppressFBWarnings("SE_PRIVATE_READ_RESOLVE_NOT_INHERITED") // because JENKINS-41313
    private Object readResolve() throws ObjectStreamException {
        if ("\u0000".equals(getTarget().getName())) {
            // this was a migration during upgrade to 2.0.0 but has not been rebuilt yet, let's see if we can fix it
            return new SCMHeadWithOwnerAndRepo.PR(repoOwner, repository, getBranchName(), number, target);
        }
        return this;
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

    @NonNull
    @Override
    public SCMHead getTarget() {
        return target;
    }

}
