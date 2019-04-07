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
package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.BranchDiscoveryTrait.ExcludeOriginPRBranchesSCMHeadFilter;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.model.listeners.SCMListener;
import hudson.plugins.mercurial.MercurialSCMSource;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import javax.annotation.CheckForNull;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;

/**
 * This class encapsulates all Bitbucket notifications logic.
 * {@link JobCompletedListener} sends a notification to Bitbucket after a build finishes.
 * Only builds derived from a job that was created as part of a multi branch project will be processed by this listener.
 */
public class BitbucketBuildStatusNotifications {

    private static String getRootURL(@NonNull Run<?, ?> build) {
        JenkinsLocationConfiguration cfg = JenkinsLocationConfiguration.get();

        if (cfg == null || cfg.getUrl() == null) {
            throw new IllegalStateException("Could not determine Jenkins URL.");
        }

        String url = DisplayURLProvider.get().getRunURL(build);
        return checkURL(url);
    }

    /**
     * Check if the build URL is compatible with Bitbucket API.
     * For example, Bitbucket API doesn't accept simple hostnames as URLs host value
     * Throws an IllegalStateException if it is not valid, or return the url otherwise
     *
     * @param url the URL of the build to check
     * @return the url if it is valid
     */
    static String checkURL(@NonNull String url) {
        if (url.startsWith("http://unconfigured-jenkins-location/")) {
            throw new IllegalStateException("Could not determine Jenkins URL.");
        }
        try {
            URL u = new URL(url);
            if (!u.getHost().contains(".")) {
                throw new IllegalStateException("Please use a fully qualified name or an IP address for Jenkins URL");
            }
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Bad Jenkins URL");
        }
        return url;
    }

    private static void createStatus(@NonNull Run<?, ?> build, @NonNull TaskListener listener,
        @NonNull BitbucketApi bitbucket, @NonNull String key, @NonNull String hash)
            throws IOException, InterruptedException {

        String url;
        try {
            url = getRootURL(build);
        } catch (IllegalStateException e) {
            listener.getLogger().println("Can not determine Jenkins root URL " +
                    "or Jenkins URL is not a valid URL regarding Bitbucket API. " +
                    "Commit status notifications are disabled until a root URL is " +
                    "configured in Jenkins global configuration.");
            return;
        }

        String name = build.getFullDisplayName(); // use the build number as the display name of the status
        BitbucketBuildStatus status;
        Result result = build.getResult();
        String buildDescription = build.getDescription();
        String statusDescription;
        String state;
        if (Result.SUCCESS.equals(result)) {
            statusDescription = StringUtils.defaultIfBlank(buildDescription, "This commit looks good.");
            state = "SUCCESSFUL";
        } else if (Result.UNSTABLE.equals(result)) {
            statusDescription = StringUtils.defaultIfBlank(buildDescription, "This commit has test failures.");
            state = "FAILED";
        } else if (Result.FAILURE.equals(result)) {
            statusDescription = StringUtils.defaultIfBlank(buildDescription, "There was a failure building this commit.");
            state = "FAILED";
        } else if (Result.NOT_BUILT.equals(result)) {
            // Bitbucket Cloud and Server support different build states.
            state = (bitbucket instanceof BitbucketCloudApiClient) ? "STOPPED" : "SUCCESSFUL";
            statusDescription = StringUtils.defaultIfBlank(buildDescription, "This commit was not built (probably the build was skipped)");
        } else if (result != null) { // ABORTED etc.
            statusDescription = StringUtils.defaultIfBlank(buildDescription, "Something is wrong with the build of this commit.");
            state = "FAILED";
        } else {
            statusDescription = StringUtils.defaultIfBlank(buildDescription, "The build is in progress...");
            state = "INPROGRESS";
        }
        status = new BitbucketBuildStatus(hash, statusDescription, state, url, key, name);
        new BitbucketChangesetCommentNotifier(bitbucket).buildStatus(status);
        if (result != null) {
            listener.getLogger().println("[Bitbucket] Build result notified");
        }
    }

    private static void sendNotifications(Run<?, ?> build, TaskListener listener)
            throws IOException, InterruptedException {
        final SCMSource s = SCMSource.SourceByItem.findSource(build.getParent());
        if (!(s instanceof BitbucketSCMSource)) {
            return;
        }
        BitbucketSCMSource source = (BitbucketSCMSource) s;
        BitbucketSCMSourceContext sourceContext = new BitbucketSCMSourceContext(null,
            SCMHeadObserver.none());
        if (sourceContext.withTraits(source.getTraits())
                .notificationsDisabled()) {
            return;
        }
        SCMRevision r = SCMRevisionAction.getRevision(s, build);
        if (r == null) {
            return;
        }
        String hash = getHash(r);
        if (hash == null) {
            return;
        }
        boolean shareBuildKeyBetweenBranchAndPR = sourceContext
            .withTraits(source.getTraits())
            .filters().stream()
            .anyMatch(filter -> filter instanceof ExcludeOriginPRBranchesSCMHeadFilter);

        String key;
        BitbucketApi bitbucket;
        if (r instanceof PullRequestSCMRevision) {
            listener.getLogger().println("[Bitbucket] Notifying pull request build result");
            PullRequestSCMHead head = (PullRequestSCMHead) r.getHead();
            key = getBuildKey(build, head.getOriginName(), shareBuildKeyBetweenBranchAndPR);
            bitbucket = source.buildBitbucketClient(head);
        } else {
            listener.getLogger().println("[Bitbucket] Notifying commit build result");
            key = getBuildKey(build, r.getHead().getName(), shareBuildKeyBetweenBranchAndPR);
            bitbucket = source.buildBitbucketClient();
        }
        createStatus(build, listener, bitbucket, key, hash);
    }

    @CheckForNull
    private static String getHash(@CheckForNull SCMRevision revision) {
        if (revision instanceof PullRequestSCMRevision) {
            // unwrap
            revision = ((PullRequestSCMRevision) revision).getPull();
        }
        if (revision instanceof BitbucketSCMSource.MercurialRevision) {
            return ((BitbucketSCMSource.MercurialRevision) revision).getHash();
        } else if (revision instanceof MercurialSCMSource.MercurialRevision) {
            return ((MercurialSCMSource.MercurialRevision) revision).getHash();
        } else if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl) {
            return ((AbstractGitSCMSource.SCMRevisionImpl) revision).getHash();
        }
        return null;
    }

    private static String getBuildKey(@NonNull Run<?, ?> build, String branch,
        boolean shareBuildKeyBetweenBranchAndPR) {

        // When the ExcludeOriginPRBranchesSCMHeadFilter filter is active, we want the
        // build status key to be the same between the branch project and the PR project.
        // This is to avoid having two build statuses when a branch goes into PR and
        // it was already built at least once as a branch.
        // So the key we use is the branch name.
        String key;
        if (shareBuildKeyBetweenBranchAndPR) {
            String folderName = build.getParent().getParent().getFullName();
            key = String.format("%s/%s", folderName, branch);
        } else {
            key = build.getParent().getFullName(); // use the job full name as the key for the status
        }

        return key;
    }

    /**
     * Sends notifications to Bitbucket on Checkout (for the "In Progress" Status).
     */
    @Extension
    public static class JobCheckOutListener extends SCMListener {

        @Override
        public void onCheckout(Run<?, ?> build, SCM scm, FilePath workspace, TaskListener listener, File changelogFile,
                               SCMRevisionState pollingBaseline) throws Exception {

            boolean hasCompletedCheckoutBefore =
                build.getAction(FirstCheckoutCompletedInvisibleAction.class) != null;

            if (!hasCompletedCheckoutBefore) {
                build.addAction(new FirstCheckoutCompletedInvisibleAction());

                try {
                    sendNotifications(build, listener);
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace(listener.error("Could not send notifications"));
                }
            }
        }
    }

    /**
     * Sends notifications to Bitbucket on Run completed.
     */
    @Extension
    public static class JobCompletedListener extends RunListener<Run<?, ?>> {

        @Override
        public void onCompleted(Run<?, ?> build, TaskListener listener) {
            try {
                sendNotifications(build, listener);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace(listener.error("Could not send notifications"));
            }
        }
    }
}
