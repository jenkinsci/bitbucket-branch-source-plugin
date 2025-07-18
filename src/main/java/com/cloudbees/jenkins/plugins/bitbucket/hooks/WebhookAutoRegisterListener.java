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
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpointProvider;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;
import hudson.triggers.SafeTimerTask;
import hudson.util.DaemonThreadFactory;
import hudson.util.NamingThreadFactory;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.SCMSourceOwners;
import org.apache.commons.lang3.StringUtils;

/**
 * {@link SCMSourceOwner} item listener that traverse the list of {@link SCMSource} and register
 * a webhook for every {@link BitbucketSCMSource} found.
 */
@Extension
public class WebhookAutoRegisterListener extends ItemListener {

    private static final Logger logger = Logger.getLogger(WebhookAutoRegisterListener.class.getName());
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
                } catch (IOException e) {
                    logger.log(Level.WARNING, e, () -> "Could not register hooks for " + owner.getFullName());
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
                } catch (IOException e) {
                    logger.log(Level.WARNING, e, () -> "Could not deregister hooks for " + owner.getFullName());
                }
            }
        });
    }

    // synchronized just to avoid duplicated webhooks in case SCMSourceOwner is updated repeatedly and quickly
    private synchronized void registerHooks(SCMSourceOwner owner) throws IOException {
        List<BitbucketSCMSource> sources = getBitbucketSCMSources(owner);
        if (sources.isEmpty()) {
            // don't spam logs if we are irrelevant
            return;
        }
        for (BitbucketSCMSource source : sources) {
            String rootUrl = BitbucketEndpointProvider.lookupEndpointJenkinsRootURL(source.getServerUrl());
            if (!rootUrl.startsWith("http://localhost") && !rootUrl.startsWith("http://unconfigured-jenkins-location")) {
                registerHook(source);
            } else {
                // only complain about being unable to register the hook if someone wants the hook registered.
                switch (new BitbucketSCMSourceContext(null, SCMHeadObserver.none())
                        .withTraits(source.getTraits())
                        .webhookRegistration()) {
                    case DISABLE:
                        continue;
                    case SYSTEM:
                        BitbucketEndpoint endpoint = BitbucketEndpointProvider
                            .lookupEndpoint(source.getServerUrl())
                            .orElse(null);
                        if (endpoint == null || !endpoint.isManageHooks()) {
                            continue;
                        }
                        break;
                    case ITEM:
                        break;
                }
                logger.log(Level.WARNING, "Can not register hook. Jenkins root URL is not valid: {0}", rootUrl);
                // go on to try next source and its rootUrl
            }
        }
    }

    /* for test purpose */ void registerHook(BitbucketSCMSource source) throws IOException {
        BitbucketApi bitbucket = getClientBySource(source);
        if (bitbucket == null) {
            return;
        }

        BitbucketWebHook existingHook;
        String hookReceiverURL = getHookReceiverURL(source.getServerUrl());
        // Check for all hooks pointing to us
        existingHook = bitbucket.getWebHooks().stream()
                .filter(hook -> hook.getUrl() != null)
                .filter(hook -> hook.getUrl().startsWith(hookReceiverURL))
                .findFirst()
                .orElse(null);

        WebhookConfiguration hookConfig = new BitbucketSCMSourceContext(null, SCMHeadObserver.none())
            .withTraits(source.getTraits())
            .webhookConfiguration();
        if (existingHook == null) {
            logger.log(Level.INFO, "Registering hook for {0}/{1}", new Object[] { source.getRepoOwner(), source.getRepository() });
            bitbucket.registerCommitWebHook(hookConfig.getHook(source));
        } else if (hookConfig.updateHook(existingHook, source)) {
            logger.log(Level.INFO, "Updating hook for {0}/{1}", new Object[] { source.getRepoOwner(), source.getRepository() });
            bitbucket.updateCommitWebHook(existingHook);
        }
    }

    private String getHookReceiverURL(String endpointURL) {
        return BitbucketEndpointProvider.lookupEndpointJenkinsRootURL(endpointURL) + BitbucketSCMSourcePushHookReceiver.FULL_PATH;
    }

    private void removeHooks(SCMSourceOwner owner) throws IOException {
        List<BitbucketSCMSource> sources = getBitbucketSCMSources(owner);
        for (BitbucketSCMSource source : sources) {
            BitbucketApi bitbucket = getClientBySource(source);
            if (bitbucket != null) {
                List<? extends BitbucketWebHook> existent = bitbucket.getWebHooks();
                BitbucketWebHook hook = null;
                for (BitbucketWebHook h : existent) {
                    // Check if there is a hook pointing to us
                    if (h.getUrl().startsWith(getHookReceiverURL(source.getServerUrl()))) {
                        hook = h;
                        break;
                    }
                }
                if (hook != null && !isUsedSomewhereElse(owner, source.getRepoOwner(), source.getRepository())) {
                    logger.log(Level.INFO, "Removing hook for {0}/{1}",
                            new Object[] { source.getRepoOwner(), source.getRepository() });
                    bitbucket.removeCommitWebHook(hook);
                } else {
                    logger.log(Level.FINE, "NOT removing hook for {0}/{1} because does not exists or its used in other project",
                            new Object[] { source.getRepoOwner(), source.getRepository() });
                }
            }
        }
    }

    @CheckForNull
    private BitbucketApi getClientBySource(@NonNull BitbucketSCMSource source) {
        switch (new BitbucketSCMSourceContext(null, SCMHeadObserver.none())
                .withTraits(source.getTraits())
                .webhookRegistration()) {
            case DISABLE:
                return null;
            case SYSTEM:
                BitbucketEndpoint endpoint = BitbucketEndpointProvider
                    .lookupEndpoint(source.getServerUrl())
                    .orElse(null);
                return endpoint == null || !endpoint.isManageHooks()
                        ? null
                        : BitbucketApiFactory.newInstance(
                                endpoint.getServerURL(),
                                AuthenticationTokens.convert(BitbucketAuthenticator.authenticationContext(endpoint.getServerURL()), endpoint.credentials()),
                                source.getRepoOwner(),
                                null,
                                source.getRepository()
                        );
            case ITEM:
                return source.buildBitbucketClient();
            default:
                return null;
        }
    }

    private boolean isUsedSomewhereElse(SCMSourceOwner owner, String repoOwner, String repoName) {
        Iterable<SCMSourceOwner> all = SCMSourceOwners.all();
        for (SCMSourceOwner other : all) {
            if (owner != other) {
                for(SCMSource otherSource : other.getSCMSources()) {
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
        return owner.getSCMSources().stream()
            .filter(BitbucketSCMSource.class::isInstance)
            .map(BitbucketSCMSource.class::cast)
            .toList();
    }

    /**
     * We need a single thread executor to run webhooks operations in background but in order.
     * Registrations and removals need to be done in the same order as they were called by the item listener.
     */
    private static synchronized ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = Executors.newSingleThreadExecutor(new NamingThreadFactory(new DaemonThreadFactory(), WebhookAutoRegisterListener.class.getName()));
        }
        return executorService;
    }

}
