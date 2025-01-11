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
package com.cloudbees.jenkins.plugins.bitbucket.hooks;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketTagSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.BranchSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPushEvent;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPushEvent.Reference;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPushEvent.Target;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;

final class PushEvent extends AbstractSCMHeadEvent<BitbucketPushEvent> {

    PushEvent(Type type, BitbucketPushEvent payload, String origin) {
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
        if (!src.getRepoOwner().equalsIgnoreCase(getPayload().getRepository().getOwnerName())) {
            return Collections.emptyMap();
        }
        if (!src.getRepository().equalsIgnoreCase(getPayload().getRepository().getRepositoryName())) {
            return Collections.emptyMap();
        }

        Map<SCMHead, SCMRevision> result = new HashMap<>();
        for (BitbucketPushEvent.Change change: getPayload().getChanges()) {
            if (change.isClosed()) {
                result.put(new BranchSCMHead(change.getOld().getName()), null);
            } else {
                // created is true
                Reference newChange = change.getNew();
                Target target = newChange.getTarget();

                SCMHead head = null;
                String eventType = newChange.getType();
                if ("tag".equals(eventType)) {
                    // for BB Cloud date is valued only in case of annotated tag
                    Date tagDate = newChange.getDate() != null ? newChange.getDate() : target.getDate();
                    if (tagDate == null) {
                        // fall back to the jenkins time when the request is processed
                        tagDate = new Date();
                    }
                    head = new BitbucketTagSCMHead(newChange.getName(), tagDate.getTime());
                } else {
                    head = new BranchSCMHead(newChange.getName());
                }
                result.put(head, new AbstractGitSCMSource.SCMRevisionImpl(head, target.getHash()));
            }
        }
        return result;
    }

    @Override
    protected BitbucketRepository getRepository() {
        return getPayload().getRepository();
    }
}
