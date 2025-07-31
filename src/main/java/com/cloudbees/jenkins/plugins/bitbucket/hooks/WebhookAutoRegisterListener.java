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
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSourceContext;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.AbstractBitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;
import hudson.triggers.SafeTimerTask;
import hudson.util.DaemonThreadFactory;
import hudson.util.NamingThreadFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.SCMSourceOwners;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

/**
 * {@link SCMSourceOwner} item listener that traverse the list of {@link SCMSource} and register
 * a webhook for every {@link BitbucketSCMSource} found.
 */
@Extension
public class WebhookAutoRegisterListener extends ItemListener {

    private static final Logger LOGGER = Logger.getLogger(WebhookAutoRegisterListener.class.getName());
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(WebhookAutoRegisterListener.class);
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
        if (!(item instanceof SCMSourceOwner)) {
            return false;
        }
        for (SCMSource source : ((SCMSourceOwner) item).getSCMSources()) {
            if (source instanceof BitbucketSCMSource) {
                return true;
            }
        }
        return false;
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

    // synchronized just to avoid duplicated webhooks in case SCMSourceOwner is updated repeatedly and quickly
    private synchronized void registerHooks(SCMSourceOwner owner) throws IOException, InterruptedException {
        List<BitbucketSCMSource> sources = getBitbucketSCMSources(owner);
        if (sources.isEmpty()) {
            // don't spam logs if we are irrelevant
            LOGGER.log(Level.INFO, () -> "[ZD267879]** No BitbucketSCMSources found for scm source owner " + owner.getFullName()  + " url[" + owner.getUrl());
            return;
        }
        for (BitbucketSCMSource source : sources) {
            String rootUrl = source.getEndpointJenkinsRootURL();
            LOGGER.log(Level.INFO, () -> "[ZD267879]** Begin registerhooks process for source [" + source.getRepository() + "] with jenkins endpoint url [" + source.getEndpointJenkinsRootURL()+"]");
            if (!rootUrl.startsWith("http://localhost")
                    && !rootUrl.startsWith("http://unconfigured-jenkins-location")) {
                registerHook(source);
            } else {
                // only complain about being unable to register the hook if someone wants the hook registered.
                LOGGER.log(Level.INFO, () -> "[ZD267879]** unable to register hook for " + source.getRepository());
                switch (new BitbucketSCMSourceContext(null, SCMHeadObserver.none())
                        .withTraits(source.getTraits())
                        .webhookRegistration()) {
                    case DISABLE:
                        LOGGER.log(Level.INFO, () -> "[ZD267879]** not registering source repo [" + source.getRepository() + "] for controller endpoint [" + source.getEndpointJenkinsRootURL() + "] because it is disabled");
                        continue;
                    case SYSTEM:
                        AbstractBitbucketEndpoint endpoint = BitbucketEndpointConfiguration.get()
                                .findEndpoint(source.getServerUrl())
                                .orElse(null);
                        if (endpoint == null) {
                            LOGGER.log(Level.INFO, () -> "[ZD267879]** not registering source repo [" + source.getRepository() + "] for controller endpoint [" + source.getEndpointJenkinsRootURL() + "] because endpoint is null");
                        }
                        // make spotbugs happy
                        if (endpoint != null && !endpoint.isManageHooks()) {
                            LOGGER.log(Level.INFO, () -> "[ZD267879]** not registering source repo [" + source.getRepository() + "] for controller endpoint [" + source.getEndpointJenkinsRootURL() + "] because it is !isManagedWebhooks()");
                        }
                        if (endpoint == null || !endpoint.isManageHooks()) {
                            continue;
                        }
                        LOGGER.log(Level.INFO, () -> "[ZD267879]** BREAK1 [SYSTEM]");
                        break;
                    case ITEM:
                        LOGGER.log(Level.INFO, () -> "[ZD267879]** BREAK1 [ITEM]");
                        break;
                }
                LOGGER.log(Level.INFO, "[ZD267879]** Can not register hook. Jenkins root URL is not valid: {0}", rootUrl);
                // go on to try next source and its rootUrl
            }
            LOGGER.log(Level.INFO, () -> "[ZD267879]** finished processing source " + source.getRepository());

        }
        LOGGER.log(Level.INFO, () -> "[ZD267879]** finished registering hooks for owner " + owner.getFullName() + " url[" + owner.getUrl());
    }

    private void registerHook(BitbucketSCMSource source) throws IOException, InterruptedException {
        LOGGER.log(Level.INFO, () -> "[ZD267879]** Begin hook registration for source [" + source.getRepository() + "] with controller endpoint [" + source.getEndpointJenkinsRootURL() + "]");
        BitbucketApi bitbucket = bitbucketApiFor(source);
        if (bitbucket == null) {
            LOGGER.log(Level.INFO, () -> "[ZD267879]** Aborting hook registration for source [" + source.getRepository() + "] with controller endpoint [" + source.getEndpointJenkinsRootURL() + "] because unable to obtain bitbucket api client");
            return;
        }

        BitbucketWebHook existingHook;
        String hookReceiverURL = source.getEndpointJenkinsRootURL() + BitbucketSCMSourcePushHookReceiver.FULL_PATH;
        // Check for all hooks pointing to us
        existingHook = bitbucket.getWebHooks().stream()
                .filter(hook -> hook.getUrl() != null)
                .filter(hook -> hook.getUrl().startsWith(hookReceiverURL))
                .findFirst()
                .orElse(null);

        LOGGER.log(
                Level.INFO,
                () -> "[ZD267879] Beging resgisterHook for hookRecieverUrl: " + hookReceiverURL + "   existingHook: " + existingHook);

        WebhookConfiguration hookConfig = new BitbucketSCMSourceContext(null, SCMHeadObserver.none())
                .withTraits(source.getTraits())
                .webhookConfiguration();

        LOGGER.log(Level.INFO, () -> "[ZD267879] created WebhookConfiguration " + hookConfig.getHook(source).getUrl());

        if (existingHook == null) {
            LOGGER.log(Level.INFO, "Registering hook for {0}/{1}", new Object[] {
                source.getRepoOwner(), source.getRepository()
            });
            bitbucket.registerCommitWebHook(hookConfig.getHook(source));
        } else if (hookConfig.updateHook(existingHook, source)) {
            LOGGER.log(
                    Level.INFO, "Updating hook for {0}/{1}", new Object[] {source.getRepoOwner(), source.getRepository()
                    });
            bitbucket.updateCommitWebHook(existingHook);
        }
        LOGGER.log(Level.INFO, () -> "[ZD267879] completecd webhook registration for " + source.getRepository());
    }

    private void removeHooks(SCMSourceOwner owner) throws IOException, InterruptedException {
        List<BitbucketSCMSource> sources = getBitbucketSCMSources(owner);
        for (BitbucketSCMSource source : sources) {
            BitbucketApi bitbucket = bitbucketApiFor(source);
            if (bitbucket != null) {
                List<? extends BitbucketWebHook> existent = bitbucket.getWebHooks();
                BitbucketWebHook hook = null;
                for (BitbucketWebHook h : existent) {
                    // Check if there is a hook pointing to us
                    if (h.getUrl()
                            .startsWith(source.getEndpointJenkinsRootURL()
                                    + BitbucketSCMSourcePushHookReceiver.FULL_PATH)) {
                        hook = h;
                        break;
                    }
                }
                if (hook != null && !isUsedSomewhereElse(owner, source.getRepoOwner(), source.getRepository())) {
                    LOGGER.log(Level.INFO, "Removing hook for {0}/{1}", new Object[] {
                        source.getRepoOwner(), source.getRepository()
                    });
                    bitbucket.removeCommitWebHook(hook);
                } else {
                    LOGGER.log(
                            Level.FINE,
                            "NOT removing hook for {0}/{1} because does not exists or its used in other project",
                            new Object[] {source.getRepoOwner(), source.getRepository()});
                }
            }
        }
    }

    private BitbucketApi bitbucketApiFor(BitbucketSCMSource source) {
        switch (new BitbucketSCMSourceContext(null, SCMHeadObserver.none())
                .withTraits(source.getTraits())
                .webhookRegistration()) {
            case DISABLE:
                LOGGER.log(Level.INFO, () -> "[ZD267879]** Aborting hook registration for source [" + source.getRepository() + "] with controller endpoint [" + source.getEndpointJenkinsRootURL() + "]");
                return null;
            case SYSTEM:
                AbstractBitbucketEndpoint endpoint = BitbucketEndpointConfiguration.get()
                        .findEndpoint(source.getServerUrl())
                        .orElse(null);
                // what would circumstances cause this block to execute?
                if (endpoint == null || !endpoint.isManageHooks()) {
                    LOGGER.log(Level.INFO, () -> "[ZD267879]** api endpoint will be  null [" + source.getRepository() + "] with controller endpoint [" + source.getEndpointJenkinsRootURL() + "]");
                }
                return endpoint == null || !endpoint.isManageHooks()
                        ? null
                        : BitbucketApiFactory.newInstance(
                                endpoint.getServerUrl(),
                                endpoint.authenticator(),
                                source.getRepoOwner(),
                                null,
                                source.getRepository());
            case ITEM:
                LOGGER.log(Level.INFO, () -> "[ZD267879]** ITEM switch case - building bitckucket client for  source [" + source.getRepository() + "] with controller endpoint [" + source.getEndpointJenkinsRootURL() + "]");
                return source.buildBitbucketClient();
            default:
                LOGGER.log(Level.INFO, () -> "[ZD267879]** endpoint will be null, DEFAULT switch case - for source [" + source.getRepository() + "] with controller endpoint [" + source.getEndpointJenkinsRootURL() + "]");
                return null;
        }
    }

    private boolean isUsedSomewhereElse(SCMSourceOwner owner, String repoOwner, String repoName) {
        Iterable<SCMSourceOwner> all = SCMSourceOwners.all();
        for (SCMSourceOwner other : all) {
            if (owner != other) {
                for (SCMSource otherSource : other.getSCMSources()) {
                    if (otherSource instanceof BitbucketSCMSource bbSCMSource
                            && StringUtils.equalsIgnoreCase(bbSCMSource.getRepoOwner(), repoOwner)
                            && bbSCMSource.getRepository().equals(repoName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private List<BitbucketSCMSource> getBitbucketSCMSources(SCMSourceOwner owner) {
        List<BitbucketSCMSource> sources = new ArrayList<>();
        for (SCMSource source : owner.getSCMSources()) {
            if (source instanceof BitbucketSCMSource) {
                sources.add((BitbucketSCMSource) source);
            }
        }
        return sources;
    }

    /**
     * We need a single thread executor to run webhooks operations in background but in order.
     * Registrations and removals need to be done in the same order as they were called by the item listener.
     */
    private static synchronized ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = Executors.newSingleThreadExecutor(
                    new NamingThreadFactory(new DaemonThreadFactory(), WebhookAutoRegisterListener.class.getName()));
        }
        return executorService;
    }
}
