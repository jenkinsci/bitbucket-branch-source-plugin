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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.server;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.EndpointType;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookClient;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudPage;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.BitbucketSCMSourcePushHookReceiver;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.HookEventType;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.WebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketCredentialsUtils;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.JsonParser;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.AbstractBitbucketWebhook;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.Messages;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketServerWebhook;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.damnhandy.uri.template.UriTemplate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Objects;
import edu.umd.cs.findbugs.annotations.CheckForNull;
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

public class ServerWebhook extends AbstractBitbucketWebhook {
    private static final Logger logger = Logger.getLogger(ServerWebhook.class.getName());
    private static final String WEBHOOK_API = "/rest/api/1.0/projects/{owner}/repos/{repo}/webhooks{/id}{?start,limit}";

    public ServerWebhook(boolean manageHooks, @CheckForNull String credentialsId) {
        super(manageHooks, credentialsId, false, null);
    }

    @DataBoundConstructor
    public ServerWebhook(boolean manageHooks, @CheckForNull String credentialsId,
                         boolean enableHookSignature, @CheckForNull String hookSignatureCredentialsId) {
        super(manageHooks, credentialsId, enableHookSignature, hookSignatureCredentialsId);
    }

    @Override
    public String getDisplayName() {
        return Messages.ServerWebhookImplementation_displayName();
    }

    @NonNull
    @Override
    public String getId() {
        return "NATIVE";
    }

    @Override
    @NonNull
    public Collection<BitbucketWebHook> retrieveHooks(@NonNull String serverURL, @NonNull BitbucketWebhookClient client) {
        String url = UriTemplate
                .fromTemplate(serverURL + WEBHOOK_API)
                .set("owner", getUserCentricOwner())
                .set("repo", repositoryName)
                .expand();

        List<BitbucketWebHook> resources = new ArrayList<>();

        TypeReference<BitbucketCloudPage<BitbucketServerWebhook>> type = new TypeReference<BitbucketCloudPage<BitbucketServerWebhook>>(){};
        BitbucketCloudPage<BitbucketServerWebhook> page = JsonParser.toJava(client.get(url), type);
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
    public void registerHook(@NonNull BitbucketWebHook payload, @NonNull BitbucketWebhookClient client) {
        String url = UriTemplate.fromTemplate(serverURL + WEBHOOK_API)
            .set("owner", getUserCentricOwner())
            .set("repo", repositoryName)
            .expand();
        client.post(url, JsonParser.toString(payload));
    }

    @Override
    public void updateHook(@NonNull BitbucketWebHook payload, @NonNull BitbucketWebhookClient client) {
        String url = UriTemplate.fromTemplate(serverURL + WEBHOOK_API)
                .set("owner", getUserCentricOwner())
                .set("repo", repositoryName)
                .set("id", payload.getUuid())
                .expand();
        client.put(url, JsonParser.toString(payload));
    }

    @Override
    public BitbucketWebHook buildPayload(WebhookConfiguration hookConfig, BitbucketSCMSource source) {
        String hookReceiverURL = getServerWebhookURL(source.getServerUrl());

        BitbucketServerWebhook hook = new BitbucketServerWebhook();
        hook.setActive(true);
        hook.setDescription("Jenkins hook");
        hook.setEvents(NATIVE_SERVER_EVENTS);
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
    public void removeHook(@NonNull BitbucketWebHook payload, @NonNull BitbucketWebhookClient client) {
        String url = UriTemplate.fromTemplate(serverURL + WEBHOOK_API)
            .set("owner", getUserCentricOwner())
            .set("repo", repositoryName)
            .set("id", payload.getUuid())
            .expand();
        client.delete(url);
    }

    private String getServerWebhookURL(@NonNull String serverURL) {
        return UriTemplate.buildFromTemplate(getEndpointJenkinsRootURL())
                .template(BitbucketSCMSourcePushHookReceiver.FULL_PATH)
                .query("server_url")
                .build()
                .set("server_url", serverURL)
                .expand();
    }

    /*
     * The list of events available in Bitbucket Data Center for the minimum supported version.
     */
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

    @Override
    public <T extends BitbucketWebHook> boolean shouldUpdate(T a, T b) {
        if (a.getClass() != b.getClass()) {
            return false;
        }
        if (a.getClass() != BitbucketServerWebhook.class) {
            return false;
        }
        boolean update = false;
        BitbucketServerWebhook current = (BitbucketServerWebhook) a;
        BitbucketServerWebhook expected = (BitbucketServerWebhook) b;

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

    @Symbol("serverWebhook")
    @Extension
    public static class DescriptorImpl extends AbstractBitbucketWebhookDescriptorImpl {

        @Override
        public String getDisplayName() {
            return "Native Data Center";
        }

        @Override
        public boolean isApplicable(@NonNull EndpointType type) {
            return type == EndpointType.SERVER;
        }

        /**
         * Stapler form completion.
         *
         * @param credentialsId selected credentials.
         * @param serverURL the server URL.
         * @return the available credentials.
         */
        @RequirePOST
        public ListBoxModel doFillCredentialsIdItems(@QueryParameter(fixEmpty = true) String credentialsId,
                                                     @QueryParameter(value = "serverURL", fixEmpty = true) String serverURL) {
            Jenkins jenkins = checkPermission();
            return BitbucketCredentialsUtils.listCredentials(jenkins, serverURL, credentialsId);
        }

        /**
         * Stapler form completion.
         *
         * @param hookSignatureCredentialsId selected hook signature credentials.
         * @param serverURL the server URL.
         * @return the available credentials.
         */
        @RequirePOST
        public ListBoxModel doFillHookSignatureCredentialsIdItems(@QueryParameter(fixEmpty = true) String hookSignatureCredentialsId,
                                                                  @QueryParameter(value = "serverURL", fixEmpty = true) String serverURL) {
            Jenkins jenkins = checkPermission();
            StandardListBoxModel result = new StandardListBoxModel();
            result.includeMatchingAs(ACL.SYSTEM2,
                    jenkins,
                    StringCredentials.class,
                    URIRequirementBuilder.fromUri(serverURL).build(),
                    CredentialsMatchers.always());
            if (hookSignatureCredentialsId != null) {
                result.includeCurrentValue(hookSignatureCredentialsId);
            }
            return result;
        }
    }

}
