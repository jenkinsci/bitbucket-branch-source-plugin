/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint;

import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpointDescriptor;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.EndpointType;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.damnhandy.uri.template.UriTemplate;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.FormValidation;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.verb.POST;

/**
 * Represents <a href="https://bitbucket.org">Bitbucket Cloud</a>.
 *
 * @since 2.2.0
 */
public class BitbucketCloudEndpoint extends AbstractBitbucketEndpoint {

    /**
     * The URL of Bitbucket Cloud.
     */
    public static final String SERVER_URL = "https://bitbucket.org";

    /**
     * {@code true} if caching should be used to reduce requests to Bitbucket.
     */
    private final boolean enableCache;

    /**
     * How long, in minutes, to cache the team response.
     */
    private final int teamCacheDuration;

    /**
     * How long, in minutes, to cache the repositories response.
     */
    private final int repositoriesCacheDuration;

    /**
     * Default constructor.
     */
    public BitbucketCloudEndpoint() {
        this(false, 0, 0, false, null, false, null);
    }

    @Deprecated(since = "936.3.1")
    public BitbucketCloudEndpoint(boolean enableCache, int teamCacheDuration, int repositoriesCacheDuration,
                                  boolean manageHooks, @CheckForNull String credentialsId) {
        this(enableCache, teamCacheDuration, repositoriesCacheDuration, manageHooks, credentialsId, false, null);
    }

    /**
     * Constructor.
     *
     * @param enableCache {@code true} if caching should be used to reduce
     *        requests to Bitbucket.
     * @param teamCacheDuration How long, in minutes, to cache the team
     *        response.
     * @param repositoriesCacheDuration How long, in minutes, to cache the
     *        repositories response.
     * @param manageHooks {@code true} if and only if Jenkins is supposed to
     *        auto-manage hooks for this end-point.
     * @param credentialsId The {@link StandardCredentials#getId()} of the
     *        credentials to use for auto-management of hooks.
     * @param enableHookSignature {@code true} hooks that comes Bitbucket Data
     *        Center are signed.
     * @param hookSignatureCredentialsId The {@link StringCredentials#getId()} of the
     *        credentials to use for verify the signature of payload.
     */
    @DataBoundConstructor
    public BitbucketCloudEndpoint(boolean enableCache, int teamCacheDuration, int repositoriesCacheDuration,
                                  boolean manageHooks, @CheckForNull String credentialsId,
                                  boolean enableHookSignature, @CheckForNull String hookSignatureCredentialsId) {
        super(manageHooks, credentialsId, enableHookSignature, hookSignatureCredentialsId);
        this.enableCache = enableCache;
        this.teamCacheDuration = teamCacheDuration;
        this.repositoriesCacheDuration = repositoriesCacheDuration;
    }

    public boolean isEnableCache() {
        return enableCache;
    }

    public int getTeamCacheDuration() {
        return teamCacheDuration;
    }

    public int getRepositoriesCacheDuration() {
        return repositoriesCacheDuration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return Messages.BitbucketCloudEndpoint_displayName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    @Deprecated(since = "936.4.0", forRemoval = true)
    public String getServerUrl() {
        return SERVER_URL;
    }

    @Override
    public String getServerURL() {
        return getServerUrl();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getRepositoryUrl(@NonNull String repoOwner, @NonNull String repository) {
        UriTemplate template = UriTemplate
                .fromTemplate(SERVER_URL + "{/owner,repo}")
                .set("owner", repoOwner)
                .set("repo", repository);
        return template.expand();
    }

    @Override
    public EndpointType getType() {
        return EndpointType.CLOUD;
    }

    /**
     * Our descriptor.
     */
    @Extension
    public static class DescriptorImpl extends BitbucketEndpointDescriptor {
        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.BitbucketCloudEndpoint_displayName();
        }

        public FormValidation doShowStats() {
            Jenkins.get().checkPermission(Jenkins.MANAGE);
            List<String> stats = BitbucketCloudApiClient.stats();
            StringBuilder builder = new StringBuilder();
            for (String stat : stats) {
                builder.append(stat).append("<br>");
            }
            return FormValidation.okWithMarkup(builder.toString());
        }

        @POST
        public FormValidation doClear() {
            Jenkins.get().checkPermission(Jenkins.MANAGE);
            BitbucketCloudApiClient.clearCaches();
            return FormValidation.ok("Caches cleared");
        }
    }

    private Object readResolve() {
        if (getBitbucketJenkinsRootUrl() != null) {
            setBitbucketJenkinsRootUrl(getBitbucketJenkinsRootUrl());
        }
        return this;
    }

}
