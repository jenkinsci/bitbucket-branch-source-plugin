/*
 * The MIT License
 *
 * Copyright (c) 2025, Nikolas Falco
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
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Actionable;
import jenkins.model.details.Detail;
import jenkins.model.details.DetailGroup;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMDetailGroup;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;

public class BitbucketCommitDetails extends Detail {
    public BitbucketCommitDetails(Actionable object) {
        super(object);
    }

    @Override
    public String getIconClassName() {
        return "symbol-git-commit-outline plugin-ionicons-api";
    }

    @Nullable
    private String getHash(@CheckForNull SCMRevision revision) {
        if (revision != null) {
            if (revision instanceof PullRequestSCMRevision prRev) {
                revision = prRev.getPull();
            }
            if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl scRev) {
                return scRev.getHash();
            }
        }
        return null;
    }

    @Override
    public String getDisplayName() {
        SCMRevision revision = getRevision();

        String hash = getHash(revision);
        if (hash != null) {
            return hash.substring(0, 7);
        }

        return null;
    }

    @Override
    public String getLink() {
        SCMRevision revision = getRevision();

        String hash = getHash(revision);
        if (hash != null) {
//            Run<?, ?> run = (Run<?, ?>) getObject();
//            BitbucketLink repoLink = run.getParent().getAction(BitbucketLink.class);
//            return repoLink.getUrl() + "/commits/" + prRev.getPull();
            return new BitbucketRepositoryDetail(getObject()).getLink() + "/commit/" + hash;
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