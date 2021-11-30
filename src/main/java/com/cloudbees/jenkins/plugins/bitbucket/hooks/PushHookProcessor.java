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

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMNavigator;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketTagSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.BranchSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPushEvent;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPushEvent.Reference;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPushEvent.Target;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudWebhookPayload;
import com.cloudbees.jenkins.plugins.bitbucket.client.events.BitbucketCloudPushEvent;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerWebhookPayload;
import com.cloudbees.jenkins.plugins.bitbucket.server.events.BitbucketServerPushEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.scm.SCM;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;

public class PushHookProcessor extends HookProcessor {

    private static final Logger LOGGER = Logger.getLogger(PushHookProcessor.class.getName());

    @Override
    public void process(HookEventType hookEvent, String payload, BitbucketType instanceType, String origin) {
        if (payload != null) {
            BitbucketPushEvent push;
            if (instanceType == BitbucketType.SERVER) {
                push = BitbucketServerWebhookPayload.pushEventFromPayload(payload);
            } else {
                push = BitbucketCloudWebhookPayload.pushEventFromPayload(payload);
            }
            if (push != null) {
                String owner = push.getRepository().getOwnerName();
                final String repository = push.getRepository().getRepositoryName();
                if (push.getChanges().isEmpty()) {
                    LOGGER.log(Level.INFO, "Received hook from Bitbucket. Processing push event on {0}/{1}",
                            new Object[]{owner, repository});
                    scmSourceReIndex(owner, repository);
                } else {
                    SCMHeadEvent.Type type = null;
                    for (BitbucketPushEvent.Change change: push.getChanges()) {
                        if ((type == null || type == SCMEvent.Type.CREATED) && change.isCreated()) {
                            type = SCMEvent.Type.CREATED;
                        } else if ((type == null || type == SCMEvent.Type.REMOVED) && change.isClosed()) {
                            type = SCMEvent.Type.REMOVED;
                        } else {
                            type = SCMEvent.Type.UPDATED;
                        }
                    }
                    SCMHeadEvent.fireLater(new SCMHeadEvent<BitbucketPushEvent>(type, push, origin) {
                        @Override
                        public boolean isMatch(@NonNull SCMNavigator navigator) {
                            if (!(navigator instanceof BitbucketSCMNavigator)) {
                                return false;
                            }
                            BitbucketSCMNavigator bbNav = (BitbucketSCMNavigator) navigator;
                            if (!isServerUrlMatch(bbNav.getServerUrl())) {
                                return false;
                            }
                            return bbNav.getRepoOwner().equalsIgnoreCase(getPayload().getRepository().getOwnerName());
                        }

                        private boolean isServerUrlMatch(String serverUrl) {
                            if (serverUrl == null || BitbucketCloudEndpoint.SERVER_URL.equals(serverUrl)) {
                                // this is a Bitbucket cloud navigator
                                if (getPayload() instanceof BitbucketServerPushEvent) {
                                    return false;
                                }
                            } else {
                                // this is a Bitbucket server navigator
                                if (getPayload() instanceof BitbucketCloudPushEvent) {
                                    return false;
                                }
                                Map<String, List<BitbucketHref>> links = getPayload().getRepository().getLinks();
                                if (links != null && links.containsKey("self")) {
                                    boolean matches = false;
                                    for (BitbucketHref link : links.get("self")) {
                                        try {
                                            URI navUri = new URI(serverUrl);
                                            URI evtUri = new URI(link.getHref());
                                            if (navUri.getHost().equalsIgnoreCase(evtUri.getHost())) {
                                                matches = true;
                                                break;
                                            }
                                        } catch (URISyntaxException e) {
                                            // ignore
                                        }
                                    }
                                    return matches;
                                }
                            }
                            return true;
                        }

                        @NonNull
                        @Override
                        public String getSourceName() {
                            return getPayload().getRepository().getRepositoryName();
                        }

                        @NonNull
                        @Override
                        public Map<SCMHead, SCMRevision> heads(@NonNull SCMSource source) {
                            if (!(source instanceof BitbucketSCMSource)) {
                                return Collections.emptyMap();
                            }
                            BitbucketSCMSource src = (BitbucketSCMSource) source;
                            if (!isServerUrlMatch(src.getServerUrl())) {
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
                        public boolean isMatch(@NonNull SCM scm) {
                            // TODO
                            return false;
                        }
                    }, BitbucketSCMSource.getEventDelaySeconds(), TimeUnit.SECONDS);
                }
            }
        }
    }
}
