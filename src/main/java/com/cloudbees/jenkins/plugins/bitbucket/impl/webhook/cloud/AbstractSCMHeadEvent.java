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

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMNavigator;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.events.BitbucketCloudPullRequestEvent;
import com.cloudbees.jenkins.plugins.bitbucket.client.events.BitbucketCloudPushEvent;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketApiUtils;
import com.cloudbees.jenkins.plugins.bitbucket.server.events.BitbucketServerPullRequestEvent;
import com.cloudbees.jenkins.plugins.bitbucket.server.events.BitbucketServerPushEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.scm.SCM;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMNavigator;
import org.apache.commons.lang3.StringUtils;

public abstract class AbstractSCMHeadEvent<P> extends SCMHeadEvent<P> {

    AbstractSCMHeadEvent(Type type, P payload, String origin) {
        super(type, payload, origin);
    }

    @Override
    public boolean isMatch(@NonNull SCMNavigator navigator) {
        if (!(navigator instanceof BitbucketSCMNavigator)) {
            return false;
        }
        BitbucketSCMNavigator bbNav = (BitbucketSCMNavigator) navigator;
        if (!isProjectKeyMatch(bbNav.getProjectKey())) {
            return false;
        }

        if (!isServerURLMatch(bbNav.getServerUrl())) {
            return false;
        }
        return StringUtils.equalsIgnoreCase(bbNav.getRepoOwner(), getRepository().getOwnerName());
    }

    protected abstract BitbucketRepository getRepository();

    private boolean isProjectKeyMatch(String projectKey) {
        if (StringUtils.isBlank(projectKey)) {
            return true;
        }
        BitbucketRepository repository = getRepository();
        if (repository.getProject() != null) {
            return projectKey.equals(repository.getProject().getKey());
        }
        return true;
    }

    protected boolean isServerURLMatch(String serverURL) {
        if (serverURL == null || BitbucketApiUtils.isCloud(serverURL)) {
            // this is a Bitbucket cloud navigator
            if (getPayload() instanceof BitbucketServerPullRequestEvent || getPayload() instanceof BitbucketServerPushEvent) {
                return false;
            }
        } else {
            // this is a Bitbucket server navigator
            if (getPayload() instanceof BitbucketCloudPullRequestEvent || getPayload() instanceof BitbucketCloudPushEvent) {
                return false;
            }
            Map<String, List<BitbucketHref>> links = getRepository().getLinks();
            if (links != null && links.containsKey("self")) {
                boolean matches = false;
                for (BitbucketHref link: links.get("self")) {
                    try {
                        URI navUri = new URI(serverURL);
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

    @Override
    public boolean isMatch(@NonNull SCM scm) {
        return false;
    }
}
