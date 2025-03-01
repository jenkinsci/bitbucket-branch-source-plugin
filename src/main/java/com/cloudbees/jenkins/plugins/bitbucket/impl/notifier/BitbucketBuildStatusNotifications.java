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
package com.cloudbees.jenkins.plugins.bitbucket.impl.notifier;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSourceContext;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketTagSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketTagSCMRevision;
import com.cloudbees.jenkins.plugins.bitbucket.BranchDiscoveryTrait.ExcludeOriginPRBranchesSCMHeadFilter;
import com.cloudbees.jenkins.plugins.bitbucket.FirstCheckoutCompletedInvisibleAction;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMRevision;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketApiUtils;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;

/**
 * This class encapsulates all Bitbucket notifications logic.
 * {@link JobCompletedListener} sends a notification to Bitbucket after a build finishes.
 * Only builds derived from a job that was created as part of a multi-branch project will be processed by this listener.
 */
public final class BitbucketBuildStatusNotifications {

    private static String getRootURL(@NonNull Run<?, ?> build) {
        JenkinsLocationConfiguration cfg = JenkinsLocationConfiguration.get();

        if (cfg.getUrl() == null) {
            throw new IllegalStateException("Could not determine Jenkins URL.");
        }

        return DisplayURLProvider.get().getRunURL(build);
    }

    /**
     * Check if the build URL is compatible with Bitbucket API.
     * For example, Bitbucket Cloud API requires fully qualified or IP
     * Where we actively do not allow localhost
     * Throws an IllegalStateException if it is not valid, or return the url otherwise
     *
     * @param url the URL of the build to check
     * @param bitbucket the bitbucket client we are facing.
     */
    static String checkURL(@NonNull String url, BitbucketApi bitbucket) {
        try {
            URL anURL = new URL(url);
            if ("localhost".equals(anURL.getHost())) {
                throw new IllegalStateException("Jenkins URL cannot start with http://localhost");
            }
            if ("unconfigured-jenkins-location".equals(anURL.getHost())) {
                throw new IllegalStateException("Could not determine Jenkins URL.");
            }
            if (bitbucket instanceof BitbucketCloudApiClient && !anURL.getHost().contains(".")) {
                throw new IllegalStateException(
                    "Please use a fully qualified name or an IP address for Jenkins URL, this is required by Bitbucket cloud");
            }
            return url;
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Bad Jenkins URL");
        }
    }

    private static void createStatus(@NonNull Run<?, ?> build,
                                     @NonNull TaskListener listener,
                                     @NonNull BitbucketApi client,
                                     @NonNull String key,
                                     @NonNull String hash,
                                     @Nullable String refName) throws IOException, InterruptedException {

        final SCMSource source = SCMSource.SourceByItem.findSource(build.getParent());
        if (!(source instanceof BitbucketSCMSource)) {
            return;
        }

        String url;
        try {
            url = getRootURL(build);
            checkURL(url, client);
        } catch (IllegalStateException e) {
            listener.getLogger().println("Can not determine Jenkins root URL " +
                    "or Jenkins URL is not a valid URL regarding Bitbucket API. " +
                    "Commit status notifications are disabled until a root URL is " +
                    "configured in Jenkins global configuration. \n" +
                    "IllegalStateException: " + e.getMessage());
            return;
        }
        boolean isCloud = BitbucketApiUtils.isCloud(client);

        BitbucketSCMSourceContext context = new BitbucketSCMSourceContext(null, SCMHeadObserver.none())
                .withTraits(source.getTraits()); // NOSONAR
        final Result result = build.getResult();
        final String name = build.getFullDisplayName(); // use the build number as the display name of the status
        String buildDescription = build.getDescription();
        String statusDescription;
        BitbucketBuildStatus.Status state;
        if (Result.SUCCESS.equals(result)) {
            statusDescription = StringUtils.defaultIfBlank(buildDescription, "This commit looks good.");
            state = BitbucketBuildStatus.Status.SUCCESSFUL;
        } else if (Result.UNSTABLE.equals(result)) {
            statusDescription = StringUtils.defaultIfBlank(buildDescription, "This commit may have some failing tests.");
            if (context.sendSuccessNotificationForUnstableBuild()) {
                state = BitbucketBuildStatus.Status.SUCCESSFUL;
            } else {
                state = BitbucketBuildStatus.Status.FAILED;
            }
        } else if (Result.FAILURE.equals(result)) {
            statusDescription = StringUtils.defaultIfBlank(buildDescription, "There was a failure building this commit.");
            state = BitbucketBuildStatus.Status.FAILED;
        } else if (Result.NOT_BUILT.equals(result)) {
            statusDescription = StringUtils.defaultIfBlank(buildDescription, "This commit was not built (probably the build was skipped)");
            if (context.sendStopNotificationForNotBuildJobs()) {
                // Bitbucket Cloud and Server support different build states.
                state = isCloud ? BitbucketBuildStatus.Status.STOPPED : BitbucketBuildStatus.Status.CANCELLED;
            } else {
                state = BitbucketBuildStatus.Status.FAILED;
            }
        } else if (result != null) { // ABORTED etc.
            statusDescription = StringUtils.defaultIfBlank(buildDescription, "Something is wrong with the build of this commit.");
            if (context.sendStopNotificationForAbortBuild()) {
                // Bitbucket Cloud and Server support different build states.
                state = isCloud ? BitbucketBuildStatus.Status.STOPPED : BitbucketBuildStatus.Status.CANCELLED;
            } else {
                state = BitbucketBuildStatus.Status.FAILED;
            }
        } else {
            statusDescription = StringUtils.defaultIfBlank(buildDescription, "The build is in progress...");
            state = BitbucketBuildStatus.Status.INPROGRESS;
        }

        if (state != null) {
            BitbucketDefaulNotifier notifier = new BitbucketDefaulNotifier(client);
            String notificationKey = DigestUtils.md5Hex(key);
            if (context.useReadableNotificationIds() && !isCloud) {
                notificationKey = key.replace(' ', '_').toUpperCase();
            }
            BitbucketBuildStatus buildStatus = new BitbucketBuildStatus(hash, statusDescription, state, url, notificationKey, name, refName);
            buildStatus.setBuildDuration(build.getDuration());
            buildStatus.setBuildNumber(build.getNumber());
            // TODO testResults should be provided by an extension point that integrates JUnit or anything else plugin
            notifier.notifyBuildStatus(buildStatus);
            if (result != null) {
                listener.getLogger().println("[Bitbucket] Build result notified");
            }
        } else {
            listener.getLogger().println("[Bitbucket] Skip result notification");
        }
    }

    private static @CheckForNull BitbucketSCMSource findBitbucketSCMSource(Run<?, ?> build) {
        SCMSource s = SCMSource.SourceByItem.findSource(build.getParent());
        return s instanceof BitbucketSCMSource scm ? scm : null;
    }

    private static void sendNotifications(BitbucketSCMSource source, Run<?, ?> build, TaskListener listener)
            throws IOException, InterruptedException {
        BitbucketSCMSourceContext sourceContext = new BitbucketSCMSourceContext(null,
            SCMHeadObserver.none()).withTraits(source.getTraits());
        if (sourceContext.notificationsDisabled()) {
            listener.getLogger().println("[Bitbucket] Notification is disabled by configuration");
            return;
        }
        SCMRevision rev = SCMRevisionAction.getRevision(source, build);
        if (rev == null) {
            return;
        }
        String hash = getHash(rev);
        if (hash == null) {
            return;
        }
        boolean shareBuildKeyBetweenBranchAndPR = sourceContext
            .filters().stream()
            .anyMatch(ExcludeOriginPRBranchesSCMHeadFilter.class::isInstance);

        String key;
        String refName;
        BitbucketApi bitbucket;
        if (rev instanceof PullRequestSCMRevision) {
            listener.getLogger().println("[Bitbucket] Notifying pull request build result");
            PullRequestSCMHead head = (PullRequestSCMHead) rev.getHead();
            key = getBuildKey(build, head.getOriginName(), shareBuildKeyBetweenBranchAndPR);
            if (BitbucketApiUtils.isCloud(source.getServerUrl())) {
                /*
                 * Poor documentation for bitbucket cloud at:
                 * https://community.atlassian.com/t5/Bitbucket-questions/Re-Builds-not-appearing-in-pull-requests/qaq-p/1805991/comment-id/65864#M65864
                 * that means refName null or valued with only head.getBranchName()
                 */
                bitbucket = source.buildBitbucketClient(head);
                refName = null;
            } else {
                // For  Bitbucket Server, notify the target of the PR. Head may point to a forked source repo that the
                // credentials don't have access to resulting in a 401 error.
                bitbucket = source.buildBitbucketClient();
                // For Bitbucket Server, refName should be "refs/heads/" + the name
                // of the source branch of the pull request, and the build status
                // should be posted to the repository of the pull request target.
                // If refName is null, then Bitbucket Server shows the build status
                // for all pull requests for the commit id. When refName is provided
                // the build status is only shown for the pull request that matches
                // the refName and commit id.
                refName = "refs/heads/" + head.getBranchName();
            }
        } else {
            listener.getLogger().println("[Bitbucket] Notifying commit build result");
            SCMHead head = rev.getHead();
            key = getBuildKey(build, head.getName(), shareBuildKeyBetweenBranchAndPR);
            bitbucket = source.buildBitbucketClient();
            if (BitbucketApiUtils.isCloud(bitbucket)) {
                refName = head.getName();
            } else {
                if (rev instanceof BitbucketTagSCMRevision || head instanceof BitbucketTagSCMHead) {
                    refName = "refs/tags/" + head.getName();
                } else {
                    refName = "refs/heads/" + head.getName();
                }
            }
        }
        createStatus(build, listener, bitbucket, key, hash, refName);
    }

    @CheckForNull
    private static String getHash(@CheckForNull SCMRevision revision) {
        if (revision instanceof PullRequestSCMRevision prRevision) {
            revision = prRevision.getPull();
        }
        if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl scmRevision) {
            return scmRevision.getHash();
        }
        return null;
    }

    private static String getBuildKey(@NonNull Run<?, ?> build, String branch, boolean shareBuildKeyBetweenBranchAndPR) {
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
    public static class JobCheckoutListener extends SCMListener {

        @Override
        public void onCheckout(Run<?, ?> build, SCM scm, FilePath workspace, TaskListener listener, File changelogFile,
                               SCMRevisionState pollingBaseline) throws Exception {
            BitbucketSCMSource source = findBitbucketSCMSource(build);
            if (source == null) {
                return;
            }

            SCMRevision r = SCMRevisionAction.getRevision(source, build);
            if (r == null) {
                return;
            }

            boolean hasCompletedCheckoutBefore =
                build.getAction(FirstCheckoutCompletedInvisibleAction.class) != null;

            if (!hasCompletedCheckoutBefore) {
                build.addAction(new FirstCheckoutCompletedInvisibleAction());

                try {
                    sendNotifications(source, build, listener);
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
            BitbucketSCMSource source = findBitbucketSCMSource(build);
            if (source == null) {
                return;
            }

            try {
                sendNotifications(source, build, listener);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace(listener.error("Could not send notifications"));
            }
        }
    }
}
