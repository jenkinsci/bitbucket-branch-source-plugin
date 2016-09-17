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
package com.cloudbees.jenkins.plugins.bitbucket.server.events;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestEvent;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.pullrequest.BitbucketServerPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketServerRepository;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketServerPullRequestEvent implements BitbucketPullRequestEvent {

    @JsonProperty("pullrequest")
    private BitbucketServerPullRequest pullRequest;

    private BitbucketServerRepository repository;

    @Override
    public BitbucketServerPullRequest getPullRequest() {
        return pullRequest;
    }

    public void setPullRequest(BitbucketServerPullRequest pullRequest) {
        this.pullRequest = pullRequest;
    }

    @Override
    public BitbucketServerRepository getRepository() {
        return repository;
    }

    public void setRepository(BitbucketServerRepository repository) {
        this.repository = repository;
    }

}
