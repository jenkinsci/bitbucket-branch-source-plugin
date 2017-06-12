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

import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketServerWebhook;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketRepositoryHook;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;
import hudson.triggers.SafeTimerTask;
import hudson.util.DaemonThreadFactory;
import hudson.util.NamingThreadFactory;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.SCMSourceOwners;

/**
 * {@link SCMSourceOwner} item listener that traverse the list of {@link SCMSource} and register
 * a webhook for every {@link BitbucketSCMSource} found.
 */
@Extension
public class WebhookAutoRegisterListener extends ItemListener {

    private static final Logger LOGGER = Logger.getLogger(WebhookAutoRegisterListener.class.getName());
    private static final List<String> CLOUD_EVENTS = Collections.unmodifiableList(Arrays.asList(
            HookEventType.PUSH.getKey(),
            HookEventType.PULL_REQUEST_CREATED.getKey(),
            HookEventType.PULL_REQUEST_UPDATED.getKey(),
            HookEventType.PULL_REQUEST_MERGED.getKey(),
            HookEventType.PULL_REQUEST_DECLINED.getKey()
    ));

    private static ExecutorService executorService;

    @Override
    public void onCreated(Item item) {
        if (!isApplicable(item)) {
            return;
        }
        registerHooksAsync((SCMSourceOwner) item);
    }

    @Override
    public void onDeleted(Item item) {
        if (!isApplicable(item)) {
            return;
        }
        removeHooksAsync((SCMSourceOwner) item);
    }

    @Override
    public void onUpdated(Item item) {
        if (!isApplicable(item)) {
            return;
        }
        registerHooksAsync((SCMSourceOwner) item);
    }

    private boolean isApplicable(Item item) {
        return item instanceof SCMSourceOwner;
    }

    private void registerHooksAsync(final SCMSourceOwner owner) {
        getExecutorService().submit(new SafeTimerTask() {
            @Override
            public void doRun() {
                try {
                    registerHooks(owner);
                } catch (IOException | InterruptedException e) {
                    LOGGER.log(Level.WARNING, "Could not register hooks for " + owner.getFullName(), e);
                }
            }
        });
    }

    private void removeHooksAsync(final SCMSourceOwner owner) {
        getExecutorService().submit(new SafeTimerTask() {
            @Override
            public void doRun() {
                try {
                    removeHooks(owner);
                } catch (IOException | InterruptedException e) {
                    LOGGER.log(Level.WARNING, "Could not deregister hooks for " + owner.getFullName(), e);
                }
            }
        });
    }

    // synchronized just to avoid duplicated webhooks in case SCMSourceOwner is updated repeteadly and quickly
    private synchronized void registerHooks(SCMSourceOwner owner) throws IOException, InterruptedException {
        String rootUrl = Jenkins.getActiveInstance().getRootUrl();
        if (rootUrl != null && !rootUrl.startsWith("http://localhost")) {
            List<BitbucketSCMSource> sources = getBitucketSCMSources(owner);
            for (BitbucketSCMSource source : sources) {
                if (source.isAutoRegisterHook()) {
                    BitbucketApi bitbucket = source.buildBitbucketClient();
                    List<? extends BitbucketWebHook> existent = bitbucket.getWebHooks();
                    BitbucketWebHook existing = null;
                    for (BitbucketWebHook hook : existent) {
                        // Check if there is a hook pointing to us already
                        if (hook.getUrl().equals(Jenkins.getActiveInstance().getRootUrl() + BitbucketSCMSourcePushHookReceiver.FULL_PATH)) {
                            existing = hook;
                            break;
                        }
                    }
                    if (existing instanceof BitbucketRepositoryHook) {
                        if (!existing.getEvents().containsAll(CLOUD_EVENTS)) {
                            Set<String> events = new TreeSet<>(existing.getEvents());
                            events.addAll(CLOUD_EVENTS);
                            ((BitbucketRepositoryHook) existing).setEvents(new ArrayList<String>(events));
                            LOGGER.info(String.format("Updating hook for %s/%s", source.getRepoOwner(),
                                    source.getRepository()));
                            bitbucket.registerCommitWebHook(existing);
                        }
                    } else  if (existing == null) {
                            LOGGER.info(String.format("Registering hook for %s/%s", source.getRepoOwner(), source.getRepository()));
                            bitbucket.registerCommitWebHook(getHook(source));
                    }
                }
            }
        } else {
            LOGGER.warning(String.format("Can not register hook. Jenkins root URL is not valid: %s", rootUrl));
        }
    }

    private void removeHooks(SCMSourceOwner owner) throws IOException, InterruptedException {
        List<BitbucketSCMSource> sources = getBitucketSCMSources(owner);
        for (BitbucketSCMSource source : sources) {
            if (source.isAutoRegisterHook()) {
                BitbucketApi bitbucket = source.buildBitbucketClient();
                List<? extends BitbucketWebHook> existent = bitbucket.getWebHooks();
                BitbucketWebHook hook = null;
                for (BitbucketWebHook h : existent) {
                    // Check if there is a hook pointing to us
                    if (h.getUrl().equals(Jenkins.getActiveInstance().getRootUrl() + BitbucketSCMSourcePushHookReceiver.FULL_PATH)) {
                        hook = h;
                        break;
                    }
                }
                if (hook != null && !isUsedSomewhereElse(owner, source.getRepoOwner(), source.getRepository())) {
                    LOGGER.info(String.format("Removing hook for %s/%s", source.getRepoOwner(), source.getRepository()));
                    bitbucket.removeCommitWebHook(hook);
                } else {
                    LOGGER.log(Level.FINE, String.format("NOT removing hook for %s/%s because does not exists or its used in other project",
                            source.getRepoOwner(), source.getRepository()));
                }
            }
        }
    }

    private boolean isUsedSomewhereElse(SCMSourceOwner owner, String repoOwner, String repoName) {
        Iterable<SCMSourceOwner> all = SCMSourceOwners.all();
        for (SCMSourceOwner other : all) {
            if (owner != other) {
                for(SCMSource otherSource : other.getSCMSources()) {
                    if (otherSource instanceof BitbucketSCMSource
                            && ((BitbucketSCMSource) otherSource).getRepoOwner().equals(repoOwner)
                            && ((BitbucketSCMSource) otherSource).getRepository().equals(repoName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private List<BitbucketSCMSource> getBitucketSCMSources(SCMSourceOwner owner) {
        List<BitbucketSCMSource> sources = new ArrayList<BitbucketSCMSource>();
        for (SCMSource source : owner.getSCMSources()) {
            if (source instanceof BitbucketSCMSource) {
                sources.add((BitbucketSCMSource) source);
            }
        }
        return sources;
    }

    private BitbucketWebHook getHook(BitbucketSCMSource owner) {
        if (owner.getBitbucketServerUrl() == null) {
            BitbucketRepositoryHook hook = new BitbucketRepositoryHook();
            hook.setActive(true);
            hook.setDescription("Jenkins hook");
            hook.setUrl(Jenkins.getActiveInstance().getRootUrl() + BitbucketSCMSourcePushHookReceiver.FULL_PATH);
            hook.setEvents(CLOUD_EVENTS);
            return hook;
        } else {
            BitbucketServerWebhook hook = new BitbucketServerWebhook();
            hook.setActive(true);
            hook.setDescription("Jenkins hook");
            hook.setUrl(Jenkins.getActiveInstance().getRootUrl() + BitbucketSCMSourcePushHookReceiver.FULL_PATH);
            return hook;
        }
    }

    /**
     * We need a single thread executor to run webhooks operations in background but in order.
     * Registrations and removals need to be done in the same order than they were called by the item listener.
     */
    private static synchronized ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = Executors.newSingleThreadExecutor(new NamingThreadFactory(new DaemonThreadFactory(), WebhookAutoRegisterListener.class.getName()));
        }
        return executorService;
    }

}
