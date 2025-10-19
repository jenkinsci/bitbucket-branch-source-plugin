/*
 * The MIT License
 *
 * Copyright (c) 2025, Nikolas Falco
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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.server;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticatedClient;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRequestException;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.client.Cache;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.HookEventType;
import com.cloudbees.jenkins.plugins.bitbucket.impl.client.ICheckedCallable;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketApiUtils;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.JsonParser;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.AbstractWebhookManager;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerPage;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketServerWebhook;
import com.cloudbees.jenkins.plugins.bitbucket.util.BitbucketCredentialsUtils;
import com.damnhandy.uri.template.UriTemplate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Objects;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.Secret;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.collections.CollectionUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;

@Extension
public class ServerWebhookManager extends AbstractWebhookManager<ServerWebhookConfiguration> {
    private static final String WEBHOOK_API = "/rest/api/1.0/projects/{owner}/repos/{repo}/webhooks{/id}{?start,limit}";
    private static final Logger logger = Logger.getLogger(ServerWebhookManager.class.getName());
    private static final Cache<String, List<BitbucketWebHook>> cachedRepositoryWebhooks = new Cache<>(3, HOURS);

    public static void clearCaches() {
        cachedRepositoryWebhooks.evictAll();
    }

    public static List<String> stats() {
        List<String> stats = new ArrayList<>();
        stats.add("Repositories webhooks: " + cachedRepositoryWebhooks.stats().toString());
        return stats;
    }

    // The list of events available in Bitbucket Data Center for the minimum supported version.
    private static final List<String> NATIVE_SERVER_EVENTS = Collections.unmodifiableList(Arrays.asList(
            HookEventType.SERVER_REFS_CHANGED.getKey(),
            HookEventType.SERVER_PULL_REQUEST_OPENED.getKey(),
            HookEventType.SERVER_PULL_REQUEST_MERGED.getKey(),
            HookEventType.SERVER_PULL_REQUEST_DECLINED.getKey(),
            HookEventType.SERVER_PULL_REQUEST_DELETED.getKey(),
            HookEventType.SERVER_PULL_REQUEST_MODIFIED.getKey(),
            HookEventType.SERVER_MIRROR_REPO_SYNCHRONIZED.getKey(),
            HookEventType.SERVER_PULL_REQUEST_FROM_REF_UPDATED.getKey()
    ));

    private String serverURL;

    @Override
    public void setCallbackURL(@NonNull String callbackURL, @NonNull BitbucketEndpoint endpoint) {
        this.serverURL = endpoint.getServerURL();
        this.callbackURL = UriTemplate.buildFromTemplate(callbackURL)
                .query("server_url")
                .build()
                .set("server_url", serverURL)
                .expand();
    }

    @Override
    public void apply(BitbucketWebhookConfiguration configuration) {
        super.apply(configuration);
        if (super.configuration.isEnableCache()) {
            cachedRepositoryWebhooks.setExpireDuration(this.configuration.getWebhooksCacheDuration(), MINUTES);
        }
    }

    @Override
    @NonNull
    public Collection<BitbucketWebHook> read(@NonNull BitbucketAuthenticatedClient client) throws IOException {
        String endpointJenkinsRootURL = getEndpointJenkinsRootURL();

        String url = UriTemplate.fromTemplate(WEBHOOK_API)
                .set("owner", client.getRepositoryOwner())
                .set("repo", client.getRepositoryName())
                .set("start", 0)
                .set("limit", 200)
                .expand();

        ICheckedCallable<List<BitbucketWebHook>, IOException> request = () -> {
            TypeReference<BitbucketServerPage<BitbucketServerWebhook>> type = new TypeReference<BitbucketServerPage<BitbucketServerWebhook>>(){};
            return JsonParser.toJava(client.get(url), type)
                    .getValues().stream()
                    .map(BitbucketWebHook.class::cast)
                    .filter(hook -> hook.getUrl().startsWith(endpointJenkinsRootURL))
                    .toList();
        };
        if (isCacheEnabled(client)) {
            try {
                String cacheKey = buildCacheKey(client);
                return cachedRepositoryWebhooks.get(cacheKey, request);
            } catch (ExecutionException e) {
                BitbucketRequestException bre = BitbucketApiUtils.unwrap(e);
                if (bre != null) {
                    throw bre;
                } else {
                    throw new IOException(e);
                }
            }
        } else {
            return request.call();
        }
    }

    @NonNull
    private BitbucketServerWebhook buildPayload() {
        BitbucketServerWebhook hook = new BitbucketServerWebhook();
        hook.setActive(true);
        hook.setDescription("Jenkins hook");
        hook.setEvents(NATIVE_SERVER_EVENTS);
        hook.setUrl(callbackURL);
        hook.setSslVerificationRequired(!configuration.isSkipCertVerification());
        if (configuration.isEnableHookSignature()) {
            String signatureCredentialsId = configuration.getHookSignatureCredentialsId();
            StringCredentials signatureSecret = BitbucketCredentialsUtils.lookupCredentials(Jenkins.get(), serverURL, signatureCredentialsId, StringCredentials.class);
            if (signatureSecret != null) {
                hook.setSecret(Secret.toString(signatureSecret.getSecret()));
            } else {
                throw new IllegalStateException("Credentials " + signatureCredentialsId + " not found on hook registration");
            }
        }
        return hook;
    }

    private void register(@NonNull BitbucketServerWebhook payload, @NonNull BitbucketAuthenticatedClient client) throws IOException {
        String url = UriTemplate.fromTemplate(WEBHOOK_API)
                .set("owner", client.getRepositoryOwner())
                .set("repo", client.getRepositoryName())
                .expand();
        client.post(url, JsonParser.toString(payload));
        if (isCacheEnabled(client)) {
            String cacheKey = buildCacheKey(client);
            cachedRepositoryWebhooks.evict(cacheKey );
        }
    }

    private boolean shouldUpdate(@NonNull BitbucketServerWebhook current, @NonNull BitbucketServerWebhook expected) {
        boolean update = false;
        if (!Objects.equal(current.getUrl(), expected.getUrl())) {
            current.setUrl(expected.getUrl());
            logger.info(() -> "Update webhook " + current.getUuid() + " callback URL");
            update = true;
        }

        if (!current.isActive()) {
            current.setActive(true);
            logger.info(() -> "Re-activate webhook " + current.getUuid());
            update = true;
        }

        if (current.isSslVerificationRequired() != expected.isSslVerificationRequired()) {
            current.setSslVerificationRequired(expected.isSslVerificationRequired());
            logger.info(() -> "Update webhook sslVerificationRequired " + expected.isSslVerificationRequired());
            update = true;
        }

        List<String> events = current.getEvents();
        List<String> expectedEvents = expected.getEvents();
        if (!events.containsAll(expectedEvents)) {
            Set<String> newEvents = new TreeSet<>(events);
            newEvents.addAll(expectedEvents);
            current.setEvents(new ArrayList<>(newEvents));
            logger.info(() -> "Update webhook " + current.getUuid() + " events because was missing: " + CollectionUtils.subtract(expectedEvents, events));
            update = true;
        }

        if (!Objects.equal(current.getSecret(), expected.getSecret())) {
            current.setSecret(expected.getSecret());
            logger.info(() -> "Update webhook " + current.getUuid() + " signature secret");
            update = true;
        }
        return update;
    }

    private void update(@NonNull BitbucketServerWebhook payload, @NonNull BitbucketAuthenticatedClient client) throws IOException {
        String url = UriTemplate
                .fromTemplate(WEBHOOK_API)
                .set("owner", client.getRepositoryOwner())
                .set("repo", client.getRepositoryName())
                .set("id", payload.getUuid())
                .expand();
        client.put(url, JsonParser.toString(payload));
        if (isCacheEnabled(client)) {
            String cacheKey = buildCacheKey(client);
            cachedRepositoryWebhooks.evict(cacheKey );
        }
    }

    @Override
    public void remove(@NonNull String webhookId, @NonNull BitbucketAuthenticatedClient client) throws IOException {
        String url = UriTemplate.fromTemplate(WEBHOOK_API)
                .set("owner", client.getRepositoryOwner())
                .set("repo", client.getRepositoryName())
                .set("id", webhookId)
                .expand();
        client.delete(url);
    }

    @Override
    public void register(@NonNull BitbucketAuthenticatedClient client) throws IOException {
        BitbucketServerWebhook existingHook = (BitbucketServerWebhook) read(client)
                .stream()
                .findFirst()
                .orElse(null);

        if (existingHook == null) {
            logger.log(Level.INFO, "Registering server hook for {0}/{1}", new Object[] { client.getRepositoryOwner(), client.getRepositoryName() });
            register(buildPayload(), client);
        } else if (shouldUpdate(existingHook, buildPayload())) {
            logger.log(Level.INFO, "Updating server hook for {0}/{1}", new Object[] { client.getRepositoryOwner(), client.getRepositoryName() });
            update(existingHook, client);
        }
    }

}
