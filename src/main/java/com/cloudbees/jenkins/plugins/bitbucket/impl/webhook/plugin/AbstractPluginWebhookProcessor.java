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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.plugin;

import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.AbstractWebhookProcessor;
import hudson.PluginWrapper;
import java.util.Map;
import jenkins.model.Jenkins;
import org.apache.commons.collections4.MultiValuedMap;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Abstract hook processor.
 *
 * Add new hook processors by extending this class and implement {@link #process(String, String, Map, BitbucketEndpoint)},
 * extract details from the hook payload and then fire an {@link jenkins.scm.api.SCMEvent} to dispatch it to the SCM API.
 */
@Deprecated(since = "937.0.0")
@Restricted(NoExternalUse.class)
public abstract class AbstractPluginWebhookProcessor extends AbstractWebhookProcessor {

    @Deprecated
    @Override
    public boolean canHandle(Map<String, String> headers, MultiValuedMap<String, String> parameters) {
        PluginWrapper replacementPlugin = Jenkins.get().getPluginManager().getPlugin("bitbucket-webhooks");
        if (replacementPlugin != null && replacementPlugin.isActive()) {
            logger.warning("bitbucket-webhooks plugin found, skipping this deprecated implementation.");
            return false;
        }
        return true;
    }
}
