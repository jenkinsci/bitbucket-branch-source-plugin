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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus;
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
import javax.annotation.CheckForNull;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;

/**
 * This class encapsulates all Bitbucket notifications logic.
 * {@link JobCompletedListener} sends a notification to Bitbucket after a build finishes.
 * Only builds derived from a job that was created as part of a multi branch project will be processed by this listener.
 */
public class BitbucketBuildStatusNotifications {

    private static void createStatus(@NonNull Run<?, ?> build, @NonNull TaskListener listener,
                                     @NonNull BitbucketApi bitbucket, @NonNull String hash)
            throws IOException, InterruptedException {
        JenkinsLocationConfiguration cfg = JenkinsLocationConfiguration.get();
        if (cfg == null || cfg.getUrl() == null) {
            listener.getLogger().println(
                    "Can not determine Jenkins root URL. Commit status notifications are disabled until a root URL is"
                            + " configured in Jenkins global configuration.");
            return;
        }
        String url;
        try {
            url = DisplayURLProvider.get().getRunURL(build);
        } catch (IllegalStateException e) {
            listener.getLogger().println(
                    "Can not determine Jenkins root URL. Commit status notifications are disabled until a root URL is"
                            + " configured in Jenkins global configuration.");
            return;
        }
        String key = build.getParent().getFullName(); // use the job full name as the key for the status
        String name = build.getFullDisplayName(); // use the build number as the display name of the status
        BitbucketBuildStatus status;
        Result result = build.getResult();
        if (Result.SUCCESS.equals(result)) {
            status = new BitbucketBuildStatus(hash, "This commit looks good", "SUCCESSFUL", url, key, name);
        } else if (Result.UNSTABLE.equals(result)) {
            status = new BitbucketBuildStatus(hash, "This commit has test failures", "FAILED", url, key, name);
        } else if (Result.FAILURE.equals(result)) {
            status = new BitbucketBuildStatus(hash, "There was a failure building this commit", "FAILED", url, key,
                    name);
        } else if (result != null) { // ABORTED etc.
            status = new BitbucketBuildStatus(hash, "Something is wrong with the build of this commit", "FAILED", url,
                    key, name);
        } else {
            status = new BitbucketBuildStatus(hash, "The tests have started...", "INPROGRESS", url, key, name);
        }
        if (!Result.NOT_BUILT.equals(result)) {
            new BitbucketChangesetCommentNotifier(bitbucket).buildStatus(status);
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
        if (new BitbucketSCMSourceContext(null, SCMHeadObserver.none())
                .withTraits(source.getTraits())
                .notificationsDisabled()) {
            return;
        }
        SCMRevision r = SCMRevisionAction.getRevision(build);  // TODO JENKINS-44648 getRevision(s, build)
        String hash = getHash(r);
        if (hash == null) {
            return;
        }
        if (r instanceof PullRequestSCMRevision) {
            listener.getLogger().println("[Bitbucket] Notifying pull request build result");
            createStatus(build, listener, source.buildBitbucketClient((PullRequestSCMHead) r.getHead()), hash);

        } else {
            listener.getLogger().println("[Bitbucket] Notifying commit build result");
            createStatus(build, listener, source.buildBitbucketClient(), hash);
        }
    }

    @CheckForNull
    private static String getHash(@CheckForNull SCMRevision revision) {
        if (revision instanceof PullRequestSCMRevision) {
            // unwrap
            revision = ((PullRequestSCMRevision) revision).getPull();
        }
        if (revision instanceof MercurialSCMSource.MercurialRevision) {
            return ((MercurialSCMSource.MercurialRevision) revision).getHash();
        } else if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl) {
            return ((AbstractGitSCMSource.SCMRevisionImpl) revision).getHash();
        }
        return null;
    }

    /**
     * Sends notifications to Bitbucket on Checkout (for the "In Progress" Status).
     */
    @Extension
    public static class JobCheckOutListener extends SCMListener {

        @Override
        public void onCheckout(Run<?, ?> build, SCM scm, FilePath workspace, TaskListener listener, File changelogFile,
                               SCMRevisionState pollingBaseline) throws Exception {
            try {
                sendNotifications(build, listener);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace(listener.error("Could not send notifications"));
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
