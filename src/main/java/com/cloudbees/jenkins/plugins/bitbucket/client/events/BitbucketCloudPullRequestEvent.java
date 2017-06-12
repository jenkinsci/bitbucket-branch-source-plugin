/*
 * The MIT License
 *
 * Copyright (c) 2016-2017, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket.client.events;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestEvent;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketPullRequestValue;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudRepositoryOwner;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketCloudPullRequestEvent implements BitbucketPullRequestEvent {

    @JsonProperty("pullrequest")
    private BitbucketPullRequestValue pullRequest;

    private BitbucketCloudRepository repository;

    @Override
    public BitbucketPullRequest getPullRequest() {
        return pullRequest;
    }

    public void setPullRequest(BitbucketPullRequestValue pullRequest) {
        this.pullRequest = pullRequest;
        reconstructMissingData();
    }

    @Override
    public BitbucketRepository getRepository() {
        return repository;
    }

    public void setRepository(BitbucketCloudRepository repository) {
        this.repository = repository;
        reconstructMissingData();
    }

    private void reconstructMissingData() {
        if (this.repository != null && this.pullRequest != null) {
            if (this.pullRequest.getSource() != null
                    && this.pullRequest.getSource().getRepository() != null) {
                if (this.pullRequest.getSource().getRepository().getScm() == null) {
                    this.pullRequest.getSource().getRepository().setScm(repository.getScm());
                }
                if (this.pullRequest.getSource().getRepository().getOwner() == null) {
                    if (this.pullRequest.getSource().getRepository().getOwnerName().equals(this.pullRequest.getAuthorLogin())) {
                        BitbucketCloudRepositoryOwner owner = new BitbucketCloudRepositoryOwner();
                        owner.setUsername(this.pullRequest.getAuthorLogin());
                        owner.setDisplayName(this.pullRequest.getAuthorDisplayName());
                        if (repository.isPrivate()) {
                            this.pullRequest.getSource().getRepository().setPrivate(repository.isPrivate());
                        }
                        this.pullRequest.getSource().getRepository().setOwner(owner);
                    } else if (this.pullRequest.getSource().getRepository().getOwnerName().equals(repository.getOwnerName())) {
                        this.pullRequest.getSource().getRepository().setOwner(repository.getOwner());
                        this.pullRequest.getSource().getRepository().setPrivate(repository.isPrivate());
                    }
                }
            }
            if (this.pullRequest.getSource() != null
                    && this.pullRequest.getSource().getCommit() != null
                    && this.pullRequest.getSource().getBranch() != null
                    && this.pullRequest.getSource().getBranch().getRawNode() == null) {
                this.pullRequest.getSource().getBranch()
                        .setRawNode(this.pullRequest.getSource().getCommit().getHash());
            }
            if (this.pullRequest.getDestination() != null
                    && this.pullRequest.getDestination().getRepository() != null) {
                if (this.pullRequest.getDestination().getRepository().getScm() == null) {
                    this.pullRequest.getDestination().getRepository().setScm(repository.getScm());
                }
                if (this.pullRequest.getDestination().getRepository().getOwner() == null
                        && this.pullRequest.getDestination().getRepository().getOwnerName()
                        .equals(repository.getOwnerName())) {
                    this.pullRequest.getDestination().getRepository().setOwner(repository.getOwner());
                    this.pullRequest.getDestination().getRepository().setPrivate(repository.isPrivate());
                }
            }
            if (this.pullRequest.getDestination() != null
                    && this.pullRequest.getDestination().getCommit() != null
                    && this.pullRequest.getDestination().getBranch() != null
                    && this.pullRequest.getDestination().getBranch().getRawNode() == null) {
                this.pullRequest.getDestination().getBranch()
                        .setRawNode(this.pullRequest.getDestination().getCommit().getHash());
            }
        }
    }

}
