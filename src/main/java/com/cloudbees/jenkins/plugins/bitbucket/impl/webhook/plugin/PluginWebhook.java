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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.plugin;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.EndpointType;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhook;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookClient;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookDescriptor;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.BitbucketSCMSourcePushHookReceiver;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.WebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketCredentialsUtils;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.JsonParser;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.Messages;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.server.ServerWebhook;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketPluginWebhook;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.damnhandy.uri.template.UriTemplate;
import com.google.common.base.Objects;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.stream.Stream;
import jenkins.model.Jenkins;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import static hudson.Util.fixEmptyAndTrim;

@Deprecated(since = "937.0.0")
// https://help.moveworkforward.com/BPW/atlassian-bitbucket-post-webhook-api
// https://help.moveworkforward.com/BPW/how-to-get-configurations-using-post-webhooks-for-
public class PluginWebhook implements BitbucketWebhook {
    private static final Logger logger = Logger.getLogger(ServerWebhook.class.getName());
    private static final String WEBHOOK_API = "/rest/webhook/1.0/projects/{owner}/repos/{repo}/configurations";

    /**
     * {@code true} if and only if Jenkins is supposed to auto-manage hooks for
     * this end-point.
     */
    private boolean manageHooks;

    /**
     * The {@link StandardCredentials#getId()} of the credentials to use for
     * auto-management of hooks.
     */
    @CheckForNull
    private String credentialsId;

    /**
     * Jenkins Server Root URL to be used by that Bitbucket endpoint.
     * The global setting from Jenkins.get().getRootUrl()
     * will be used if this field is null or equals an empty string.
     * This variable is bound to the UI, so an empty value is saved
     * and returned by getter as such.
     */
    private String endpointJenkinsRootURL;

    @DataBoundConstructor
    public PluginWebhook(boolean manageHooks, @CheckForNull String credentialsId) {
        this.manageHooks = manageHooks && StringUtils.isNotBlank(credentialsId);
        this.credentialsId = manageHooks ? fixEmptyAndTrim(credentialsId) : null;
    }

    /**
     * Returns {@code true} if and only if Jenkins is supposed to auto-manage hooks for this end-point.
     *
     * @return {@code true} if and only if Jenkins is supposed to auto-manage hooks for this end-point.
     */
    @Override
    public final boolean isManageHooks() {
        return manageHooks;
    }

    /**
     * Returns the {@link StandardUsernamePasswordCredentials#getId()} of the credentials to use for auto-management
     * of hooks.
     *
     * @return the {@link StandardUsernamePasswordCredentials#getId()} of the credentials to use for auto-management
     * of hooks.
     */
    @Override
    @CheckForNull
    public final String getCredentialsId() {
        return credentialsId;
    }

    public String getEndpointJenkinsRootURL() {
        return endpointJenkinsRootURL;
    }

    @DataBoundSetter
    public void setEndpointJenkinsRootURL(@CheckForNull String endpointJenkinsRootURL) {
        this.endpointJenkinsRootURL = fixEmptyAndTrim(endpointJenkinsRootURL);
    }

    @Override
    public String getDisplayName() {
        return Messages.ServerWebhookImplementation_displayName();
    }

    @NonNull
    @Override
    public String getId() {
        return "PLUGIN";
    }

    @Override
    @NonNull
    public Collection<BitbucketWebHook> retrieveHooks(@NonNull String serverURL, @NonNull BitbucketWebhookClient client) {
        String url = UriTemplate
                .fromTemplate(serverURL + WEBHOOK_API)
                .set("owner", getUserCentricOwner())
                .set("repo", repositoryName)
                .expand();

        BitbucketPluginWebhook[] hooks = JsonParser.toJava(client.get(url), BitbucketPluginWebhook[].class);
        return Stream.of(hooks)
                .filter(hook -> StringUtils.startsWith(hook.getUrl(), serverURL))
                .map(hook -> (BitbucketWebHook) hook)
                .toList();
    }

    @Override
    public void registerHook(@NonNull BitbucketWebHook payload, @NonNull BitbucketWebhookClient client) {
        // API documentation at https://help.moveworkforward.com/BPW/how-to-manage-configurations-using-post-webhooks-f#HowtomanageconfigurationsusingPostWebhooksforBitbucketAPIs?-Createpostwebhook
        String url = UriTemplate.fromTemplate(serverURL + WEBHOOK_API)
            .set("owner", getUserCentricOwner())
            .set("repo", repositoryName)
            .expand();
        client.post(url, JsonParser.toString(payload));
    }

    @Override
    public void updateHook(@NonNull BitbucketWebHook payload, @NonNull BitbucketWebhookClient client) {
        // API documentation at https://help.moveworkforward.com/BPW/how-to-manage-configurations-using-post-webhooks-f#HowtomanageconfigurationsusingPostWebhooksforBitbucketAPIs?-UpdateapostwebhookbyID
        String url = UriTemplate.fromTemplate(serverURL + WEBHOOK_API + "/{id}")
                .set("owner", getUserCentricOwner())
                .set("repo", repositoryName)
                .set("id", payload.getUuid())
                .expand();
        client.put(url, JsonParser.toString(payload));
    }

    @Override
    public BitbucketWebHook buildPayload(WebhookConfiguration hookConfig, BitbucketSCMSource source) {
        String hookReceiverURL = getServerWebhookURL(source.getServerUrl());

        BitbucketPluginWebhook hook = new BitbucketPluginWebhook();
        hook.setActive(true);
        hook.setDescription("Jenkins hook");
        hook.setUrl(hookReceiverURL);
        hook.setEvents(PLUGIN_SERVER_EVENTS);
        hook.setCommittersToIgnore(Util.fixEmptyAndTrim(hookConfig.getCommittersToIgnore()));
        return hook;
    }

    @Override
    public void removeHook(@NonNull BitbucketWebHook payload, @NonNull BitbucketWebhookClient client) {
        String url = UriTemplate.fromTemplate(serverURL + WEBHOOK_API + "/{id}")
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

    // See https://help.moveworkforward.com/BPW/how-to-manage-configurations-using-post-webhooks-f#HowtomanageconfigurationsusingPostWebhooksforBitbucketAPIs?-Possibleeventtypes
    private static final List<String> PLUGIN_SERVER_EVENTS = Collections.unmodifiableList(Arrays.asList(
            "ABSTRACT_REPOSITORY_REFS_CHANGED", // push event
            "BRANCH_CREATED",
            "BRANCH_DELETED",
            "PULL_REQUEST_DECLINED",
            "PULL_REQUEST_DELETED",
            "PULL_REQUEST_MERGED",
            "PULL_REQUEST_OPENED",
            "PULL_REQUEST_REOPENED",
            "PULL_REQUEST_UPDATED",
            "REPOSITORY_MIRROR_SYNCHRONIZED", // not supported by the hookprocessor
            "TAG_CREATED"));

    @Override
    public <T extends BitbucketWebHook> boolean shouldUpdate(T a, T b) {
        if (a.getClass() != b.getClass()) {
            return false;
        }
        if (a.getClass() != BitbucketPluginWebhook.class) {
            return false;
        }
        boolean update = false;
        BitbucketPluginWebhook current = (BitbucketPluginWebhook) a;
        BitbucketPluginWebhook expected = (BitbucketPluginWebhook) b;

        if (!Objects.equal(fixEmptyAndTrim(current.getCommittersToIgnore()), fixEmptyAndTrim(expected.getCommittersToIgnore()))) {
            current.setCommittersToIgnore(expected.getCommittersToIgnore());
            update = true;
        }

        if (!current.isActive()) {
            current.setActive(true);
            update = true;
        }

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
        return update;
    }

    @Symbol("pluginWebhook")
    @Extension
    public static class DescriptorImpl extends BitbucketWebhookDescriptor {

        @Override
        public String getDisplayName() {
            return "Post Webhooks for Bitbucket";
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

        @Restricted(NoExternalUse.class)
        @RequirePOST
        public static FormValidation doCheckEndpointJenkinsRootURL(@QueryParameter String value) {
            checkPermission();
            String url = fixEmptyAndTrim(value);
            if (url == null) {
                return FormValidation.ok();
            }
            try {
                new URL(url);
            } catch (MalformedURLException e) {
                return FormValidation.error("Invalid URL: " + e.getMessage());
            }
            return FormValidation.ok();
        }

        private static Jenkins checkPermission() {
            Jenkins jenkins = Jenkins.get();
            jenkins.checkPermission(Jenkins.MANAGE);
            return jenkins;
        }

    }

}