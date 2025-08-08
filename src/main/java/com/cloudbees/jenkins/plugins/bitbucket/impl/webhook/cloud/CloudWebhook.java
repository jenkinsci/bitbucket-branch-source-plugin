/*
 * The MIT License
 *
 * Copyright (c) 2025, Falco Nikolas
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

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.EndpointType;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookClient;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudPage;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudHook;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.BitbucketSCMSourcePushHookReceiver;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.HookEventType;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.WebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketCredentialsUtils;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.JsonParser;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.AbstractBitbucketWebhook;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.Messages;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.damnhandy.uri.template.UriTemplate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Objects;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

public class CloudWebhook extends AbstractBitbucketWebhook {
    private static final String WEBHOOK_URL = "https://api.bitbucket.org/2.0/repositories{/owner,repo}/hooks";
    private static final Logger logger = Logger.getLogger(CloudWebhook.class.getName());

    public CloudWebhook(boolean manageHooks, String credentialsId) {
        this(manageHooks, credentialsId, false, null);
    }

    @DataBoundConstructor
    public CloudWebhook(boolean manageHooks, String credentialsId, boolean enableHookSignature, String hookSignatureCredentialsId) {
        super(manageHooks, credentialsId, enableHookSignature, hookSignatureCredentialsId);
    }

    @Override
    public String getDisplayName() {
        return Messages.CloudWebhookImplementation_displayName();
    }

    @NonNull
    @Override
    public String getId() {
        return "CLOUD";
    }

    @Override
    @NonNull
    public Collection<BitbucketWebHook> retrieveHooks(@NonNull String serverURL, @NonNull BitbucketWebhookClient client) {
        String url = UriTemplate.fromTemplate(WEBHOOK_URL + "{?page,pagelen}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("pagelen", 100)
                .expand();

        List<BitbucketWebHook> resources = new ArrayList<>();

        TypeReference<BitbucketCloudPage<BitbucketCloudHook>> type = new TypeReference<BitbucketCloudPage<BitbucketCloudHook>>(){};
        BitbucketCloudPage<BitbucketCloudHook> page = JsonParser.toJava(client.get(url), type);
        resources.addAll(page.getValues().stream()
                .filter(hook -> StringUtils.startsWith(hook.getUrl(), serverURL))
                .toList());
        while (!page.isLastPage()){
            String response = client.get(page.getNext());
            page = JsonParser.toJava(response, type);
            resources.addAll(page.getValues().stream()
                    .filter(hook -> StringUtils.startsWith(hook.getUrl(), serverURL))
                    .toList());
        }
        return resources;
    }

    @Override
    @NonNull
    public BitbucketWebHook buildPayload(@NonNull WebhookConfiguration hookConfig, @NonNull BitbucketSCMSource source) {
        String hookReceiverURL = getEndpointJenkinsRootURL() + BitbucketSCMSourcePushHookReceiver.FULL_PATH;

        BitbucketCloudHook hook = new BitbucketCloudHook();
        hook.setEvents(CLOUD_EVENTS);
        hook.setActive(true);
        hook.setDescription("Jenkins hook");
        hook.setUrl(hookReceiverURL);
        if (isEnableHookSignature()) {
            String hookSignatureCredentialsId = getHookSignatureCredentialsId();
            StringCredentials signatureSecret = BitbucketCredentialsUtils.lookupCredentials(Jenkins.get(), source.getServerUrl(), hookSignatureCredentialsId, StringCredentials.class);
            if (signatureSecret != null) {
                hook.setSecret(Secret.toString(signatureSecret.getSecret()));
            } else {
                throw new IllegalStateException("Credentials " + hookSignatureCredentialsId + " not found on hook registration");
            }
        }
        return hook;
    }

    @Override
    public void registerHook(@NonNull BitbucketWebHook payload, @NonNull BitbucketWebhookClient client) {
        String url = UriTemplate.fromTemplate(WEBHOOK_URL)
                .set("owner", owner)
                .set("repo", repositoryName)
                .expand();
        client.post(url, JsonParser.toString(payload));
    }

    @Override
    public boolean shouldUpdate(@NonNull BitbucketWebHook a, @NonNull BitbucketWebHook b) {
        if (a.getClass() != b.getClass()) {
            return false;
        }
        if (a.getClass() != BitbucketCloudHook.class) {
            return false;
        }
        boolean update = false;
        BitbucketCloudHook current = (BitbucketCloudHook) a;
        BitbucketCloudHook expected = (BitbucketCloudHook) b;
        if (!StringUtils.equals(current.getUrl(), expected.getUrl())) {
            current.setUrl(expected.getUrl());
            logger.info(() -> "Update callback webhook URL");
            update = true;
        }

        List<String> events = current.getEvents();
        List<String> expectedEvents = expected.getEvents();
        if (!events.containsAll(expectedEvents)) {
            Set<String> newEvents = new TreeSet<>(events);
            newEvents.addAll(expectedEvents);
            current.setEvents(new ArrayList<>(newEvents));
            logger.info(() -> "Update webhook because the following events was missing: " + CollectionUtils.subtract(expectedEvents, events));
            update = true;
        }

        if (!Objects.equal(current.getSecret(), expected.getSecret())) {
            current.setSecret(expected.getSecret());
            update = true;
        }
        return update;
    }

    @Override
    public void updateHook(@NonNull BitbucketWebHook payload, @NonNull BitbucketWebhookClient client) {
        String url = UriTemplate
                .fromTemplate(WEBHOOK_URL + "/{hook}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("hook", payload.getUuid())
                .expand();
        client.put(url, JsonParser.toString(payload));
    }

    @Override
    public void removeHook(@NonNull BitbucketWebHook payload, @NonNull BitbucketWebhookClient client) {
        String url = UriTemplate
                .fromTemplate(WEBHOOK_URL + "/{hook}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("hook", payload.getUuid())
                .expand();
        client.put(url, JsonParser.toString(payload));
    }

    /*
     * The list of events available in Bitbucket Cloud.
     */
    private static final List<String> CLOUD_EVENTS = Collections.unmodifiableList(Arrays.asList(
            HookEventType.PUSH.getKey(),
            HookEventType.PULL_REQUEST_CREATED.getKey(),
            HookEventType.PULL_REQUEST_UPDATED.getKey(),
            HookEventType.PULL_REQUEST_MERGED.getKey(),
            HookEventType.PULL_REQUEST_DECLINED.getKey()
    ));

    @Symbol("cloudWebhook")
    @Extension
    public static class DescriptorImpl extends AbstractBitbucketWebhookDescriptorImpl {

        @Override
        public String getDisplayName() {
            return "Native Cloud";
        }

        @Override
        public boolean isApplicable(EndpointType type) {
            return type == EndpointType.CLOUD;
        }

        /**
         * Stapler form completion.
         *
         * @param credentialsId selected credentials.
         * @param serverURL the server URL.
         * @return the available credentials.
         */
        @RequirePOST
        public ListBoxModel doFillCredentialsIdItems(@QueryParameter(fixEmpty = true) String credentialsId) {
            Jenkins jenkins = checkPermission();
            return BitbucketCredentialsUtils.listCredentials(jenkins, BitbucketCloudEndpoint.SERVER_URL, credentialsId);
        }

        /**
         * Stapler form completion.
         *
         * @param hookSignatureCredentialsId selected hook signature credentials.
         * @param serverURL the server URL.
         * @return the available credentials.
         */
        @RequirePOST
        public ListBoxModel doFillHookSignatureCredentialsIdItems(@QueryParameter(fixEmpty = true) String hookSignatureCredentialsId) {
            Jenkins jenkins = checkPermission();
            StandardListBoxModel result = new StandardListBoxModel();
            result.includeMatchingAs(ACL.SYSTEM2,
                    jenkins,
                    StringCredentials.class,
                    URIRequirementBuilder.fromUri(BitbucketCloudEndpoint.SERVER_URL).build(),
                    CredentialsMatchers.always());
            if (hookSignatureCredentialsId != null) {
                result.includeCurrentValue(hookSignatureCredentialsId);
            }
            return result;
        }
    }

}
