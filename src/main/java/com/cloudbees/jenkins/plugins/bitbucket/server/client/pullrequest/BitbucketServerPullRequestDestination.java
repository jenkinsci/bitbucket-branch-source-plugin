/*
 * The MIT License
 *
 * Copyright (c) 2016 CloudBees, Inc., Nikolas Falco
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

package com.cloudbees.jenkins.plugins.bitbucket.server.client.pullrequest;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBranch;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestDestination;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.branch.BitbucketServerBranch;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.branch.BitbucketServerCommit;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketServerRepository;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BitbucketServerPullRequestDestination implements BitbucketPullRequestDestination {

    @JsonProperty("displayId")
    private String branchName;
    @JsonProperty
    private String latestCommit;

    private BitbucketServerRepository repository;
    private BitbucketServerBranch branch;
    @JsonProperty
    private BitbucketServerCommit commit;

    @Override
    public BitbucketRepository getRepository() {
        return repository;
    }

    @Override
    public BitbucketBranch getBranch() {
        if (branch == null) {
            branch = new BitbucketServerBranch(branchName, latestCommit);
        }
        return branch;
    }

    @Override
    public BitbucketCommit getCommit() {
        if (branch != null && commit == null) {
            commit = new BitbucketServerCommit(branch.getMessage(), latestCommit, branch.getDateMillis(), branch.getAuthor());
        }
        return commit;
    }

    public void setRepository(BitbucketServerRepository repository) {
        this.repository = repository;
    }

}
