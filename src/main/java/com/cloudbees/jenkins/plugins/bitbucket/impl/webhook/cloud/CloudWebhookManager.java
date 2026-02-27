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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.cloud;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticatedClient;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRequestException;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudPage;
import com.cloudbees.jenkins.plugins.bitbucket.client.Cache;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudWebhook;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.HookEventType;
import com.cloudbees.jenkins.plugins.bitbucket.impl.client.ICheckedCallable;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketApiUtils;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.JsonParser;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.AbstractWebhookManager;
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
import org.apache.commons.lang3.Strings;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;

@Extension
public class CloudWebhookManager extends AbstractWebhookManager<CloudWebhookConfiguration> {
    private static final String WEBHOOK_URL = "/2.0/repositories{/owner,repo}/hooks{/hook}{?page,pagelen}";
    private static final Logger logger = Logger.getLogger(CloudWebhookManager.class.getName());
    private static final Cache<String, List<BitbucketWebHook>> cachedRepositoryWebhooks = new Cache<>(3, HOURS);

    public static void clearCaches() {
        cachedRepositoryWebhooks.evictAll();
    }

    public static List<String> stats() {
        List<String> stats = new ArrayList<>();
        stats.add("Repositories webhooks: " + cachedRepositoryWebhooks.stats().toString());
        return stats;
    }

    // The list of events available in Bitbucket Cloud.
    private static final List<String> CLOUD_EVENTS = Collections.unmodifiableList(Arrays.asList(
            HookEventType.PUSH.getKey(),
            HookEventType.PULL_REQUEST_CREATED.getKey(),
            HookEventType.PULL_REQUEST_UPDATED.getKey(),
            HookEventType.PULL_REQUEST_MERGED.getKey(),
            HookEventType.PULL_REQUEST_DECLINED.getKey()
    ));

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
        String url = UriTemplate.fromTemplate(WEBHOOK_URL)
                .set("owner", client.getRepositoryOwner())
                .set("repo", client.getRepositoryName())
                .set("pagelen", 100)
                .expand();

        ICheckedCallable<List<BitbucketWebHook>, IOException> request = () -> {
            List<BitbucketWebHook> resources = new ArrayList<>();

            TypeReference<BitbucketCloudPage<BitbucketCloudWebhook>> type = new TypeReference<BitbucketCloudPage<BitbucketCloudWebhook>>(){};
            BitbucketCloudPage<BitbucketCloudWebhook> page = JsonParser.toJava(client.get(url), type);
            resources.addAll(page.getValues().stream()
                    .filter(hook -> isValidWebhook(hook.getUrl()))
                    .toList());
            while (!page.isLastPage()){
                String response = client.get(page.getNext());
                page = JsonParser.toJava(response, type);
                resources.addAll(page.getValues().stream()
                        .filter(hook -> isValidWebhook(hook.getUrl()))
                        .toList());
            }
            return resources;
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
    private BitbucketCloudWebhook buildPayload() {
        BitbucketCloudWebhook hook = new BitbucketCloudWebhook();
        hook.setEvents(CLOUD_EVENTS);
        hook.setActive(true);
        hook.setDescription("Jenkins hook");
        hook.setUrl(callbackURL);
        hook.setSkipCertVerification(configuration.isSkipCertVerification());
        if (configuration.isEnableHookSignature()) {
            String signatureCredentialsId = configuration.getHookSignatureCredentialsId();
            StringCredentials signatureSecret = BitbucketCredentialsUtils.lookupCredentials(Jenkins.get(), BitbucketCloudEndpoint.SERVER_URL, signatureCredentialsId, StringCredentials.class);
            if (signatureSecret != null) {
                hook.setSecret(Secret.toString(signatureSecret.getSecret()));
            } else {
                throw new IllegalStateException("Credentials " + signatureCredentialsId + " not found registering new webhook");
            }
        }
        return hook;
    }

    private void register(@NonNull BitbucketCloudWebhook payload, @NonNull BitbucketAuthenticatedClient client) throws IOException {
        String url = UriTemplate.fromTemplate(WEBHOOK_URL)
                .set("owner", client.getRepositoryOwner())
                .set("repo", client.getRepositoryName())
                .expand();
        client.post(url, JsonParser.toString(payload));
        if (isCacheEnabled(client)) {
            String cacheKey = buildCacheKey(client);
            cachedRepositoryWebhooks.evict(cacheKey);
        }
    }

    private boolean shouldUpdate(@NonNull BitbucketCloudWebhook current, @NonNull BitbucketCloudWebhook expected) {
        boolean update = false;
        if (!Strings.CI.equals(current.getUrl(), expected.getUrl())) {
            current.setUrl(expected.getUrl());
            logger.info(() -> "Update webhook " + current.getUuid() + " callback URL");
            update = true;
        }

        if (!current.isActive()) {
            current.setActive(true);
            logger.info(() -> "Re-activate webhook " + current.getUuid());
            update = true;
        }

        if (current.isSkipCertVerification() != expected.isSkipCertVerification()) {
            current.setSkipCertVerification(expected.isSkipCertVerification());
            logger.info(() -> "Update skipCertVerification to " + expected.isSkipCertVerification());
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

    private void update(@NonNull BitbucketCloudWebhook payload, @NonNull BitbucketAuthenticatedClient client) throws IOException {
        String url = UriTemplate.fromTemplate(WEBHOOK_URL)
                .set("owner", client.getRepositoryOwner())
                .set("repo", client.getRepositoryName())
                .set("hook", payload.getUuid())
                .expand();
        client.put(url, JsonParser.toString(payload));
        if (isCacheEnabled(client)) {
            String cacheKey = buildCacheKey(client);
            cachedRepositoryWebhooks.evict(cacheKey);
        }
    }

    @Override
    public void remove(@NonNull String webhookId, @NonNull BitbucketAuthenticatedClient client) throws IOException {
        String url = UriTemplate.fromTemplate(WEBHOOK_URL)
                .set("owner", client.getRepositoryOwner())
                .set("repo", client.getRepositoryName())
                .set("hook", webhookId)
                .expand();
        client.delete(url);
    }

    @Override
    public void register(@NonNull BitbucketAuthenticatedClient client) throws IOException {
        BitbucketCloudWebhook existingHook = (BitbucketCloudWebhook) read(client)
                .stream()
                .findFirst()
                .orElse(null);

        if (existingHook == null) {
            logger.log(Level.INFO, "Registering cloud hook for {0}/{1}", new Object[] { client.getRepositoryOwner(), client.getRepositoryName() });
            register(buildPayload(), client);
        } else if (shouldUpdate(existingHook, buildPayload())) {
            logger.log(Level.INFO, "Updating cloud hook for {0}/{1}", new Object[] { client.getRepositoryOwner(), client.getRepositoryName() });
            update(existingHook, client);
        }
    }

}
