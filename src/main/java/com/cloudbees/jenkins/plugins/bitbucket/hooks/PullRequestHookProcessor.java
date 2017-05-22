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
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequestEvent;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryType;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudWebhookPayload;
import com.cloudbees.jenkins.plugins.bitbucket.client.events.BitbucketCloudPullRequestEvent;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerWebhookPayload;
import com.cloudbees.jenkins.plugins.bitbucket.server.events.BitbucketServerPullRequestEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.scm.SCM;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import org.codehaus.jackson.map.ObjectMapper;

import static com.cloudbees.jenkins.plugins.bitbucket.hooks.HookEventType.PULL_REQUEST_DECLINED;
import static com.cloudbees.jenkins.plugins.bitbucket.hooks.HookEventType.PULL_REQUEST_MERGED;

public class PullRequestHookProcessor extends HookProcessor {

    private static final Logger LOGGER = Logger.getLogger(PullRequestHookProcessor.class.getName());

    @Override
    public void process(final HookEventType hookEvent, String payload, BitbucketType instanceType, String origin) {
        if (payload != null) {
            BitbucketPullRequestEvent pull;
            if (instanceType == BitbucketType.SERVER) {
                pull = BitbucketServerWebhookPayload.pullRequestEventFromPayload(payload);
            } else {
                pull = BitbucketCloudWebhookPayload.pullRequestEventFromPayload(payload);
            }
            if (pull != null) {
                SCMEvent.Type eventType;
                switch (hookEvent) {
                    case PULL_REQUEST_CREATED:
                        eventType = SCMEvent.Type.CREATED;
                        break;
                    case PULL_REQUEST_DECLINED:
                    case PULL_REQUEST_MERGED:
                        eventType = SCMEvent.Type.REMOVED;
                        break;
                    default:
                        eventType = SCMEvent.Type.UPDATED;
                        break;
                }
                // assume updated as a catch-all type
                SCMHeadEvent.fireNow(new SCMHeadEvent<BitbucketPullRequestEvent>(eventType, pull, origin) {
                    @Override
                    public boolean isMatch(@NonNull SCMNavigator navigator) {
                        if (!(navigator instanceof BitbucketSCMNavigator)) {
                            return false;
                        }
                        BitbucketSCMNavigator bbNav = (BitbucketSCMNavigator) navigator;
                        if (!isBitbucketServerUrlMatch(bbNav.getBitbucketServerUrl())) {
                            return false;
                        }
                        if (!bbNav.getRepoOwner().equalsIgnoreCase(getPayload().getRepository().getOwnerName())) {
                            return false;
                        }
                        return true;
                    }

                    private boolean isBitbucketServerUrlMatch(String serverUrl) {
                        if (serverUrl == null) {
                            // this is a Bitbucket cloud navigator
                            if (getPayload() instanceof BitbucketServerPullRequestEvent) {
                                return false;
                            }
                        } else {
                            // this is a Bitbucket server navigator
                            if (getPayload() instanceof BitbucketCloudPullRequestEvent) {
                                return false;
                            }
                            Map<String, List<BitbucketHref>> links = getPayload().getRepository().getLinks();
                            if (links != null && links.containsKey("self")) {
                                boolean matches = false;
                                for (BitbucketHref link: links.get("self")) {
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
                        if (!isBitbucketServerUrlMatch(src.getBitbucketServerUrl())) {
                            return Collections.emptyMap();
                        }
                        if (!src.getRepoOwner().equalsIgnoreCase(getPayload().getRepository().getOwnerName())) {
                            return Collections.emptyMap();
                        }
                        if (!src.getRepository().equalsIgnoreCase(getPayload().getRepository().getRepositoryName())) {
                            return Collections.emptyMap();
                        }
                        BitbucketRepositoryType type =
                                BitbucketRepositoryType.fromString(getPayload().getRepository().getScm());
                        if (type == null) {
                            LOGGER.log(Level.INFO, "Received event for unknown repository type: {0}",
                                    getPayload().getRepository().getScm());
                            return Collections.emptyMap();
                        }
                        Map<SCMHead, SCMRevision> result = new HashMap<>(1);
                        PullRequestSCMHead head = new PullRequestSCMHead(
                                getPayload().getPullRequest().getSource().getRepository().getOwnerName(),
                                getPayload().getPullRequest().getSource().getRepository().getRepositoryName(),
                                type,
                                getPayload().getPullRequest().getSource().getBranch().getName(),
                                getPayload().getPullRequest()
                        );
                        if (hookEvent == PULL_REQUEST_DECLINED || hookEvent == PULL_REQUEST_MERGED) {
                            // special case for repo being deleted
                            result.put(head, null);
                        } else {
                            switch (type) {
                                case GIT:
                                    result.put(head, new AbstractGitSCMSource.SCMRevisionImpl(head,
                                            getPayload().getPullRequest().getSource().getCommit().getHash()));
                                    break;
                                case MERCURIAL:
                                    result.put(head, new BitbucketSCMSource.MercurialRevision(head,
                                            getPayload().getPullRequest().getSource().getCommit().getHash()));
                                    break;
                                default:
                                    LOGGER.log(Level.INFO, "Received event for unknown repository type: {0}", type);
                                    break;
                            }
                        }
                        return result;
                    }

                    @Override
                    public boolean isMatch(@NonNull SCM scm) {
                        // TODO
                        return false;
                    }
                });
            }
        }
    }

}
