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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestEvent;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudBranch;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketPullRequestValue;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketPullRequestValueDestination;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketPullRequestValueRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudRepositoryOwner;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import org.apache.commons.lang.StringUtils;

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
            BitbucketPullRequestValueRepository source = this.pullRequest.getSource();
            if (source != null) {
                BitbucketCloudRepository sourceRepository = source.getRepository();
                if (sourceRepository != null) {
                    if (sourceRepository.getScm() == null) {
                        sourceRepository.setScm(repository.getScm());
                    }
                    if (sourceRepository.getOwner() == null) {
                        if (!sourceRepository.getOwnerName().equals(repository.getOwnerName())) { // i.e., a fork
                            BitbucketCloudRepositoryOwner owner = new BitbucketCloudRepositoryOwner();
                            owner.setUsername(sourceRepository.getOwnerName());
                            owner.setDisplayName(this.pullRequest.getAuthorLogin());
                            if (repository.isPrivate()) {
                                sourceRepository.setPrivate(repository.isPrivate());
                            }
                            sourceRepository.setOwner(owner);
                        } else { // origin branch
                            sourceRepository.setOwner(repository.getOwner());
                            sourceRepository.setPrivate(repository.isPrivate());
                        }
                    }
                }
            }
            if (source != null) {
                BitbucketCloudBranch sourceBranch = source.getBranch();
                BitbucketCommit sourceCommit = source.getCommit();
                if (sourceCommit != null
                    && sourceBranch != null) {
                    if (sourceBranch.getRawNode() == null) {
                        sourceBranch.setRawNode(source.getCommit().getHash());
                    }
                    if (sourceBranch.getDateMillis() == 0) {
                        sourceBranch.setDateMillis(toDate(sourceCommit.getDate()));
                    }
                }
            }
            BitbucketPullRequestValueDestination destination = this.pullRequest.getDestination();
            if (destination != null
                && destination.getRepository() != null) {
                if (destination.getRepository().getScm() == null) {
                    destination.getRepository().setScm(repository.getScm());
                }
                if (destination.getRepository().getOwner() == null
                    && destination.getRepository().getOwnerName()
                    .equals(repository.getOwnerName())) {
                    destination.getRepository().setOwner(repository.getOwner());
                    destination.getRepository().setPrivate(repository.isPrivate());
                }
            }
            if (destination != null
                && destination.getCommit() != null
                && destination.getBranch() != null
                && destination.getBranch().getRawNode() == null) {
                destination.getBranch()
                    .setRawNode(destination.getCommit().getHash());
            }
        }
    }

    private long toDate(String dateStr){
        if(StringUtils.isBlank(dateStr)){
            return 0;
        }
        final SimpleDateFormat dateParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        dateParser.setTimeZone(TimeZone.getTimeZone("GMT"));
        try {
            return dateParser.parse(dateStr).getTime();
        } catch (ParseException e) {
            return 0;
        }
    }

}
