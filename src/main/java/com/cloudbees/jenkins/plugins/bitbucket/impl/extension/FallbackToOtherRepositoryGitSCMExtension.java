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
package com.cloudbees.jenkins.plugins.bitbucket.impl.extension;

import com.cloudbees.jenkins.plugins.bitbucket.BranchWithHash;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import java.net.URISyntaxException;
import java.util.List;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * If specified commit hashes are not found in repository then fetch
 * specified branches from remote.
 */
public class FallbackToOtherRepositoryGitSCMExtension extends GitSCMExtension {

    private final String cloneLink;
    private final String remoteName;
    private final List<BranchWithHash> branchWithHashes;

    @DataBoundConstructor
    public FallbackToOtherRepositoryGitSCMExtension(
        String cloneLink,
        String remoteName,
        List<BranchWithHash> branchWithHashes
    ) {
        this.cloneLink = cloneLink;
        this.remoteName = remoteName;
        this.branchWithHashes = branchWithHashes;
    }

    @Override
    public Revision decorateRevisionToBuild(
        GitSCM scm,
        Run<?, ?> build,
        GitClient git,
        TaskListener listener,
        Revision marked,
        Revision rev
    ) throws InterruptedException {
        List<RefSpec> refSpecs = branchWithHashes.stream()
            .filter(branchWithHash -> !commitExists(git, branchWithHash.getHash()))
            .map(branchWithHash -> {
                String branch = branchWithHash.getBranch();
                return new RefSpec("+refs/heads/" + branch + ":refs/remotes/" + remoteName + "/" + branch);
            })
            .toList();

        if (!refSpecs.isEmpty()) {
            FetchCommand fetchCommand = git.fetch_();
            URIish remote;
            try {
                remote = new URIish(cloneLink);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            fetchCommand.from(remote, refSpecs).execute();
        }
        return rev;
    }

    private static boolean commitExists(GitClient git, String sha1) {
        try {
            git.revParse(sha1);
            return true;
        } catch (GitException ignored) {
            return false;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public String getCloneLink() {
        return cloneLink;
    }

    public String getRemoteName() {
        return remoteName;
    }

    public List<BranchWithHash> getBranchWithHashes() {
        return branchWithHashes;
    }

    @Extension
    // No @Symbol because Pipeline users should not configure this in other ways than this plugin provides
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Additional refSpecs to fetch in case of clone links.";
        }
    }
}
