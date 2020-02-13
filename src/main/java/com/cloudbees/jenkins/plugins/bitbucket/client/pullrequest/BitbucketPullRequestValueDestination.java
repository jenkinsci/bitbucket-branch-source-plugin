/*
 * The MIT License
 *
 * Copyright (c) 2016-2017 CloudBees, Inc., Nikolas Falco
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
 *
 */

package com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestDestination;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudBranch;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudCommit;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudRepository;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Date;

public class BitbucketPullRequestValueDestination implements BitbucketPullRequestDestination {
    private BitbucketCloudRepository repository;
    private BitbucketCloudBranch branch;
    private BitbucketCloudCommit commit;

    @JsonCreator
    public BitbucketPullRequestValueDestination(@NonNull @JsonProperty("repository") BitbucketCloudRepository repository,
                                                @JsonProperty("branch") BitbucketCloudBranch branch,
                                                @NonNull @JsonProperty("commit") BitbucketCloudCommit commit) {
        this.repository = repository;
        this.branch = branch;
        this.commit = commit;

        // It is possible for a PR's original destination to no longer exist.
        if(this.branch != null) {
            // redound available the informations into impl objects
            this.branch.setRawNode(commit.getHash());
        }
    }

    @Override
    public BitbucketCloudRepository getRepository() {
        return repository;
    }

    public void setRepository(BitbucketCloudRepository repository) {
        this.repository = repository;
    }

    @Override
    public BitbucketCloudBranch getBranch() {
        return branch;
    }

    public void setBranch(BitbucketCloudBranch branch) {
        this.branch = branch;
    }

    @Override
    public BitbucketCommit getCommit() {
        if (branch != null && commit != null) {
            // initialise commit value using branch closure if not already valued
            if (commit.getAuthor() == null) {
                commit.setAuthor(branch.getAuthor());
            }
            if (commit.getMessage() == null) {
                commit.setMessage(branch.getMessage());
            }
            if (commit.getDateMillis() == 0) {
                commit.setDate(new StdDateFormat().format(new Date(branch.getDateMillis())));
            }
        }
        return commit;
    }

    public void setCommit(BitbucketCloudCommit commit) {
        this.commit = commit;
    }
}
