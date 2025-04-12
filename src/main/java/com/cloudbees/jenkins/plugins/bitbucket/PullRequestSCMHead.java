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
import com.cloudbees.jenkins.plugins.bitbucket.api.PullRequestBranchType;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.mixin.ChangeRequestSCMHead2;

/**
 * {@link SCMHead} for a Bitbucket pull request
 *
 * @since 2.0.0
 */
public class PullRequestSCMHead extends SCMHead implements ChangeRequestSCMHead2 {

    private static final long serialVersionUID = 1L;

    private final String repoOwner;

    private final String repository;

    private final String branchName;

    private final PullRequestBranchType branchType;

    private final String number;

    private final String title;

    private final BranchSCMHead target;

    private final SCMHeadOrigin origin;

    private final ChangeRequestCheckoutStrategy strategy;

    public PullRequestSCMHead(String name, String repoOwner, String repository, String branchName, PullRequestBranchType branchType,
                              String number, String title, BranchSCMHead target, SCMHeadOrigin origin,
                              ChangeRequestCheckoutStrategy strategy) {
        super(name);
        this.repoOwner = repoOwner;
        this.repository = repository;
        this.branchName = branchName;
        this.branchType = branchType;
        this.number = number;
        this.title = title;
        this.target = target;
        this.origin = origin;
        this.strategy = strategy;
    }

    public PullRequestSCMHead(String name, String repoOwner, String repository, String branchName,
                              BitbucketPullRequest pr, SCMHeadOrigin origin, ChangeRequestCheckoutStrategy strategy) {
        this(name, repoOwner, repository, branchName, pr.getSource().getBranchType(),
             pr.getId(), pr.getTitle(), new BranchSCMHead(pr.getDestination().getBranch().getName()),
             origin, strategy);
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

    public PullRequestBranchType getBranchType() {
        return branchType;
    }

}
