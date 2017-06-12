/*
 * The MIT License
 *
 * Copyright (c) 2016-2017 CloudBees, Inc.
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
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketPullRequestValueDestination implements BitbucketPullRequestDestination {
    private BitbucketCloudRepository repository;
    private BitbucketCloudBranch branch;
    private BitbucketCloudCommit commit;

    @Override
    @JsonProperty("repository")
    public BitbucketCloudRepository getRepository() {
        return repository;
    }

    @JsonProperty("repository")
    public void setRepository(BitbucketCloudRepository repository) {
        this.repository = repository;
    }

    @Override
    @JsonProperty("branch")
    public BitbucketCloudBranch getBranch() {
        return branch;
    }

    @JsonProperty("branch")
    public void setBranch(BitbucketCloudBranch branch) {
        this.branch = branch;
    }

    @Override
    @JsonProperty("commit")
    public BitbucketCommit getCommit() {
        return commit;
    }

    @JsonProperty("commit")
    public void setCommit(BitbucketCloudCommit commit) {
        this.commit = commit;
    }
}
