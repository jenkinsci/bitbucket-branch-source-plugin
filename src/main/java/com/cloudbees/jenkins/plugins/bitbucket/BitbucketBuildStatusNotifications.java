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
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.model.listeners.SCMListener;
import hudson.plugins.git.Revision;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.mercurial.MercurialTagAction;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import java.io.File;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;

/**
 * This class encapsulates all Bitbucket notifications logic.
 * {@link JobCompletedListener} sends a notification to Bitbucket after a build finishes.
 * Only builds derived from a job that was created as part of a multi branch project will be processed by this listener.
 *
 * The way the notification is sent is defined by the implementation of {@link BitbucketNotifier} returned by {@link #getNotifier(BitbucketApi)}.
 *
 */
public class BitbucketBuildStatusNotifications {

    private static void createBuildCommitStatus(@NonNull Run<?,?> build, @NonNull TaskListener listener, @NonNull BitbucketApi bitbucket) {
        String revision = extractRevision(build);
        if (revision != null) {
            Result result = build.getResult();
            String url;
            try {
                url = DisplayURLProvider.get().getRunURL(build);
            } catch (IllegalStateException e) {
                listener.getLogger().println("Can not determine Jenkins root URL. Commit status notifications are disabled until a root URL is configured in Jenkins global configuration.");
                return;
            }
            BitbucketBuildStatus status = null;
            if (Result.SUCCESS.equals(result)) {
                status = new BitbucketBuildStatus(revision, "This commit looks good", "SUCCESSFUL", url, build.getParent().getName(), build.getDisplayName());
            } else if (Result.UNSTABLE.equals(result)) {
                status = new BitbucketBuildStatus(revision, "This commit has test failures", "FAILED", url, build.getParent().getName(), build.getDisplayName());
            } else if (Result.FAILURE.equals(result)) {
                status = new BitbucketBuildStatus(revision, "There was a failure building this commit", "FAILED", url, build.getParent().getName(), build.getDisplayName());
            } else if (result != null) { // ABORTED etc.
                status = new BitbucketBuildStatus(revision, "Something is wrong with the build of this commit", "FAILED", url, build.getParent().getName(), build.getDisplayName());
            } else {
                status = new BitbucketBuildStatus(revision, "The tests have started...", "INPROGRESS", url, build.getParent().getName(), build.getDisplayName());
            }
            if (status != null) {
                getNotifier(bitbucket).buildStatus(status);
            }
            if (result != null) {
                listener.getLogger().println("[Bitbucket] Build result notified");
            }
        }
    }

    @CheckForNull
    private static String extractRevision(Run<?, ?> build) {
        String revision = null;
        BuildData gitBuildData = build.getAction(BuildData.class);
        if (gitBuildData != null) {
            Revision lastBuiltRevision = gitBuildData.getLastBuiltRevision();
            if (lastBuiltRevision != null) {
                revision = lastBuiltRevision.getSha1String();
            }
        } else {
            MercurialTagAction action = build.getAction(MercurialTagAction.class);
            if (action != null) {
                revision = action.getId();
            }
        }
        return revision;
    }

    private static void createPullRequestCommitStatus(Run<?,?> build, TaskListener listener, BitbucketApi bitbucket) {
        createBuildCommitStatus(build, listener, bitbucket);
    }

    private static BitbucketNotifier getNotifier(BitbucketApi bitbucket) {
        return new BitbucketChangesetCommentNotifier(bitbucket);
    }

    @CheckForNull
    private static BitbucketApi buildBitbucketClientForBuild(Run<?,?> build, BitbucketSCMSource source) {
        Job<?, ?> job = build.getParent();
        StandardUsernamePasswordCredentials creds = source.getScanCredentials();
        SCMHead _head = SCMHead.HeadByItem.findHead(job);
        if (_head instanceof SCMHeadWithOwnerAndRepo) {
            SCMHeadWithOwnerAndRepo head = (SCMHeadWithOwnerAndRepo) _head;
            return new BitbucketApiConnector(source.getBitbucketServerUrl()).create(head.getRepoOwner(), head.getRepoName(), creds);
        }
        return null;
    }

    @CheckForNull
    private static BitbucketSCMSource lookUpSCMSource(Run<?, ?> build) {
        ItemGroup<?> multiBranchProject = build.getParent().getParent();
        if (multiBranchProject instanceof SCMSourceOwner) {
            SCMSourceOwner scmSourceOwner = (SCMSourceOwner) multiBranchProject;
            BitbucketSCMSource source = lookUpBitbucketSCMSource(scmSourceOwner);
            if (source != null) {
                return source;
            }
        }
        return null;
    }



    /**
     * It is possible having more than one SCMSource in our MultiBranch project.
     * TODO: Does it make sense having more than one of the same type?
     *
     * @param scmSourceOwner An {@link Item} that owns {@link SCMSource} instances.
     * @return A source or null
     */
    @CheckForNull
    private static BitbucketSCMSource lookUpBitbucketSCMSource(final SCMSourceOwner scmSourceOwner) {
        for (SCMSource scmSource : scmSourceOwner.getSCMSources()) {
            if (scmSource instanceof BitbucketSCMSource) {
                return (BitbucketSCMSource) scmSource;
            }
        }
        return null;
    }
    
    private static void sendNotifications(Run<?, ?> build, TaskListener listener) {
        BitbucketSCMSource source = lookUpSCMSource(build);
        if (source != null && extractRevision(build) != null) {
            BitbucketApi bitbucket = buildBitbucketClientForBuild(build, source);
            if (bitbucket != null) {
                if (source.getRepoOwner().equals(bitbucket.getOwner()) &&
                        source.getRepository().equals(bitbucket.getRepositoryName())) {
                    listener.getLogger().println("[Bitbucket] Notifying commit build result");
                    createBuildCommitStatus(build, listener, bitbucket);
                } else {
                    listener.getLogger().println("[Bitbucket] Notifying pull request build result");
                    createPullRequestCommitStatus(build, listener, bitbucket);
                }
            } else {
                listener.getLogger().println("[Bitbucket] Can not get connection information from the source. Skipping notification...");
            }
        }
    }

    /**
     * Sends notifications to Bitbucket on Checkout (for the "In Progress" Status).
     */
    @Extension
    public static class JobCheckOutListener extends SCMListener {

        @Override
        public void onCheckout(Run<?, ?> build, SCM scm, FilePath workspace, TaskListener listener, File changelogFile, SCMRevisionState pollingBaseline) throws Exception {
            sendNotifications(build, listener);
        }
    }

    /**
     * Sends notifications to Bitbucket on Run completed.
     */
    @Extension
    public static class JobCompletedListener extends RunListener<Run<?,?>> {

        @Override 
        public void onCompleted(Run<?, ?> build, TaskListener listener) {
            sendNotifications(build, listener);
        }
    }
}
