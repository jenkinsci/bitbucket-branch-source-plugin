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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.cloud;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketTagSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.BranchSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBranch;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPushEvent;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPushEvent.Reference;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPushEvent.Target;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.api.HasBranches;
import com.cloudbees.jenkins.plugins.bitbucket.api.HasPullRequests;
import com.cloudbees.jenkins.plugins.bitbucket.api.HasTags;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudBranch;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import org.apache.commons.lang3.Strings;

final class CloudPushEvent extends AbstractSCMHeadEvent<BitbucketPushEvent> implements HasTags, HasPullRequests, HasBranches {

    CloudPushEvent(Type type, BitbucketPushEvent payload, String origin) {
        super(type, payload, origin);
    }

    @NonNull
    @Override
    public String getSourceName() {
        return getRepository().getRepositoryName();
    }

    @NonNull
    @Override
    public Map<SCMHead, SCMRevision> heads(@NonNull SCMSource source) {
        if (!(source instanceof BitbucketSCMSource)) {
            return Collections.emptyMap();
        }
        BitbucketSCMSource src = (BitbucketSCMSource) source;
        if (!isServerURLMatch(src.getServerUrl())) {
            return Collections.emptyMap();
        }
        if (!Strings.CI.equals(src.getRepoOwner(), getPayload().getRepository().getOwnerName())) {
            return Collections.emptyMap();
        }
        if (!src.getRepository().equalsIgnoreCase(getPayload().getRepository().getRepositoryName())) {
            return Collections.emptyMap();
        }

        Map<SCMHead, SCMRevision> result = new HashMap<>();
        for (BitbucketPushEvent.Change change: getPayload().getChanges()) {
            Reference changeRef = change.isClosed() ? change.getOld(): change.getNew();
            Target target = changeRef.getTarget();

            SCMHead head = null;
            String eventType = changeRef.getType();
            if ("tag".equals(eventType)) {
                // for BB Cloud date is valued only in case of annotated tag
                Date tagDate = changeRef.getDate() != null ? changeRef.getDate() : target.getDate();
                if (tagDate == null) {
                    // fall back to the jenkins time when the request is processed
                    tagDate = new Date();
                }
                head = new BitbucketTagSCMHead(changeRef.getName(), tagDate.getTime());
            } else {
                head = new BranchSCMHead(changeRef.getName());
            }
            result.put(head, new AbstractGitSCMSource.SCMRevisionImpl(head, target.getHash()));
        }
        return result;
    }

    @Override
    protected BitbucketRepository getRepository() {
        return getPayload().getRepository();
    }

    @Override
    public Iterable<BitbucketBranch> getTags(BitbucketSCMSource src) {
        List<BitbucketBranch> tags = new ArrayList<>();
        for (BitbucketPushEvent.Change change: getPayload().getChanges()) {
            Reference changeRef = change.isCreated() ? change.getNew() : change.getOld();
            Target target = changeRef.getTarget();

            String eventType = changeRef.getType();
            if ("tag".equals(eventType)) {
                // for BB Cloud date is valued only in case of annotated tag
                Date tagDate = changeRef.getDate() != null ? changeRef.getDate() : target.getDate();
                if (tagDate == null) {
                    // fall back to the jenkins time when the request is processed
                    tagDate = new Date();
                }
                BitbucketCloudBranch tagRef = new BitbucketCloudBranch(changeRef.getName(), target.getHash(), tagDate.getTime());
                tagRef.setAuthor(target.getAuthor());
                tags.add(tagRef);
            }
        }
        return tags;
    }

    @Override
    public Iterable<BitbucketPullRequest> getPullRequests(BitbucketSCMSource src) {
        return Collections.emptyList();
    }

    @Override
    public Iterable<BitbucketBranch> getBranches(BitbucketSCMSource src) {
        List<BitbucketBranch> branches = new ArrayList<>();
        for (BitbucketPushEvent.Change change: getPayload().getChanges()) {
            Reference changeRef = change.isCreated() ? change.getNew() : change.getOld();
            Target target = changeRef.getTarget();

            String eventType = changeRef.getType();
            if ("branch".equals(eventType)) {
                // for BB Cloud date is valued only in case of annotated tag
                Date commitDate = changeRef.getDate() != null ? changeRef.getDate() : target.getDate();
                if (commitDate == null) {
                    // fall back to the jenkins time when the request is processed
                    commitDate = new Date();
                }
                branches.add(new BitbucketCloudBranch(changeRef.getName(), target.getHash(), commitDate.getTime()));
            }
        }
        return branches;
    }
}
