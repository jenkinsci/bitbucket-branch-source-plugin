/*
 * The MIT License
 *
 * Copyright (c) 2026, Nikolas Falco
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
package com.cloudbees.jenkins.plugins.bitbucket.impl.details;

import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMRevision;
import hudson.model.Actionable;
import jenkins.model.details.Detail;
import jenkins.model.details.DetailGroup;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMDetailGroup;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;

public class BitbucketCommitDetail extends Detail {

    public BitbucketCommitDetail(Actionable object) {
        super(object);
    }

    @Override
    public String getIconClassName() {
        return "symbol-git-commit-outline plugin-ionicons-api";
    }

    @Override
    public String getDisplayName() {
        SCMRevision revision = getRevision();

        if (revision == null) {
            return null;
        }

        if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl abstractRevision) {
            return abstractRevision.getHash();
        }

        if (revision instanceof PullRequestSCMRevision pullRequestSCMRevision) {
            return pullRequestSCMRevision.getPull().toString();
        }

        return null;
    }

    @Override
    public String getLink() {
        SCMRevision revision = getRevision();

        if (revision == null) {
            return null;
        }

        String repoURL = new BitbucketRepositoryDetail(getObject()).getLink() + "/commits/";

        if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl abstractRevision) {
            return repoURL + abstractRevision.getHash();
        }

        if (revision instanceof PullRequestSCMRevision pullRequestSCMRevision) {
            return repoURL + pullRequestSCMRevision.getPull().toString();
        }

        return null;
    }

    @Override
    public DetailGroup getGroup() {
        return SCMDetailGroup.get();
    }

    private SCMRevision getRevision() {
        SCMRevisionAction scmRevisionAction = getObject().getAction(SCMRevisionAction.class);

        if (scmRevisionAction == null) {
            return null;
        }

        return scmRevisionAction.getRevision();
    }
}
