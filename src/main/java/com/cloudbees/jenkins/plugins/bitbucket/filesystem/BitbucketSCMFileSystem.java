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
package com.cloudbees.jenkins.plugins.bitbucket.filesystem;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketTagSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.BranchSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketServerEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketApiUtils;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.DateUtils;
import com.cloudbees.jenkins.plugins.bitbucket.server.BitbucketServerVersion;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.security.ACL;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Inherited;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.apache.commons.lang3.StringUtils;

import static org.apache.commons.lang3.StringUtils.defaultString;

public class BitbucketSCMFileSystem extends SCMFileSystem {
    private final String ref;
    private final BitbucketApi api;

    protected BitbucketSCMFileSystem(BitbucketApi api, String ref, @CheckForNull SCMRevision rev) {
        super(rev);
        this.ref = ref;
        this.api = api;
    }

    /**
     * {@link Inherited}
     */
    @Override
    public long lastModified() throws IOException {
        return 0L; // api.getBranch(ref).getDateMillis() or api.getTag(ref).getDateMillis()
    }

    @NonNull
    @Override
    public SCMFile getRoot() {
        SCMRevision revision = getRevision();
        return new BitbucketSCMFile(api, ref, revision == null ? null : revision.toString());
    }

    @Override
    public void close() throws IOException {
        if (api != null) {
            api.close();
        }
    }

    @Override
    public boolean changesSince(@CheckForNull SCMRevision fromRevision, @NonNull OutputStream changeLogStream)
            throws UnsupportedOperationException, IOException, InterruptedException {
        SCMRevision currentRevision = getRevision();
        if (Objects.equals(currentRevision, fromRevision)) {
            // special case where somebody is asking one of two stupid questions:
            // 1. what has changed between the latest and the latest
            // 2. what has changed between the current revision and the current revision
            return false;
        }
        int count = 0;
        StringBuilder log = new StringBuilder(1024);
        String startHash = null;
        if (fromRevision instanceof AbstractGitSCMSource.SCMRevisionImpl gitRev) {
            startHash = gitRev.getHash();
        }
        /*
         * Simulate what the CliGitAPIImpl.ChangelogCommand execute does:
         * - git whatchanged --no-abbrev -M --format=commit %H%ntree %T%nparent %P%nauthor %aN <%aE> %ai%ncommitter %cN <%cE> %ci%n%n%w(0,4,4)%B -n 1024 8d0fa145 e43fdffe
         * so we need to format each commit with the same format
         * commit %H%ntree %T%nparent %P%nauthor %aN <%aE> %ai%ncommitter %cN <%cE> %ci%n%n%w(0,4,4)%B
         * @see org.jenkinsci.plugins.gitclient.new ChangelogCommand() {...}.RAW
         */
        for (BitbucketCommit commit : api.getCommits(startHash, ref)) {
            log.setLength(0);
            log.append("commit ").append(commit.getHash()).append('\n');
//            log.append("tree ").append(commit.getTree().getSha()).append('\n');
            log.append("parent ").append(StringUtils.join(commit.getParents(), " ")).append('\n');
            log.append("author ").append(commit.getAuthor()).append(' ').append(defaultString(DateUtils.formatToISO(commit.getAuthorDate()))).append('\n');
            log.append("committer ").append(commit.getAuthor()).append(' ').append(defaultString(DateUtils.formatToISO(commit.getCommitterDate()))).append('\n');
            log.append('\n');
            String msg = commit.getMessage();
            if (msg.endsWith("\r\n")) {
                msg = msg.substring(0, msg.length() - 2);
            } else if (msg.endsWith("\n")) {
                msg = msg.substring(0, msg.length() - 1);
            }
            msg = msg.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\n    ");
            log.append("    ").append(msg).append('\n');
/*
            String NULL_HASH = "0000000000000000000000000000000000000000";

            if (count == 0) {
                String fromHash = commit.getHash();
                String toHash = NULL_HASH;
                if (currentRevision instanceof SCMRevisionImpl gitRev) {
                    toHash = gitRev.getHash();
                } else if (currentRevision instanceof ChangeRequestSCMRevision<?> prRev
                        && prRev.getTarget() instanceof SCMRevisionImpl targetRev) {
                    toHash = targetRev.getHash();
                }
                toHash = StringUtils.rightPad(toHash, 40, '0');

                // in BB diff changes are not related to a specific commit so we put all of them into the most recent commit
                for (BitbucketCloudCommitDiffStat change : api.getCommitsChanges(startHash, ref)) {
                    log.append('\n').append(':');
                    switch (change.getStatus()) {
                    case added:
                        log.append("000000").append(' ').append("100644")
                            .append(' ')
                            .append(NULL_HASH).append(' ').append(toHash)
                            .append(' ')
                            .append('A').append("\t").append(change.getNewPath());
                        break;
                    case modified:
                        log.append("100644").append(' ').append("100644")
                            .append(' ')
                            .append(fromHash).append(' ').append(toHash)
                            .append(' ')
                            .append('M').append("\t").append(change.getNewPath());
                        break;
                    case removed:
                        log.append("100644").append(' ').append("000000")
                            .append(' ')
                            .append(fromHash).append(' ').append(NULL_HASH)
                            .append(' ')
                            .append('D').append("\t").append(change.getOldPath());
                        break;
                    case renamed:
                        log.append("100644").append(' ').append("100644")
                            .append(' ')
                            .append(fromHash).append(' ').append(toHash)
                            .append(' ')
                            .append('R').append("\t").append(change.getOldPath()).append(' ').append(change.getNewPath());
                        break;
                    }
                }
                log.append('\n');
            }
*/
            changeLogStream.write(log.toString().getBytes(StandardCharsets.UTF_8));
            changeLogStream.flush();
            count++;
            if (count >= GitSCM.MAX_CHANGELOG) {
                break;
            }
        }

        return count > 0;
    }

    @Extension
    public static class BuilderImpl extends SCMFileSystem.Builder {

        @Override
        public boolean supports(SCM source) {
            return false;
        }

        @Override
        public boolean supports(SCMSource source) {
            return source instanceof BitbucketSCMSource;
        }

        @Override
        protected boolean supportsDescriptor(@SuppressWarnings("rawtypes") SCMDescriptor scmDescriptor) {
            return false;
        }

        @Override
        protected boolean supportsDescriptor(SCMSourceDescriptor scmSourceDescriptor) {
            return scmSourceDescriptor instanceof BitbucketSCMSource.DescriptorImpl;
        }

        @Override
        public SCMFileSystem build(@NonNull Item owner, @NonNull SCM scm, @CheckForNull SCMRevision rev) {
            return null;
        }

        private static StandardCredentials lookupScanCredentials(@CheckForNull Item context,
                                                                 @CheckForNull String scanCredentialsId,
                                                                 String serverURL) {
            scanCredentialsId = Util.fixEmpty(scanCredentialsId);
            if (scanCredentialsId == null) {
                return null;
            } else {
                return CredentialsMatchers.firstOrNull(
                        CredentialsProvider.lookupCredentialsInItem(
                                StandardCredentials.class,
                                context,
                                context instanceof Queue.Task task
                                        ? task.getDefaultAuthentication2()
                                        : ACL.SYSTEM2,
                                URIRequirementBuilder.fromUri(serverURL).build()
                        ),
                        CredentialsMatchers.allOf(
                                CredentialsMatchers.withId(scanCredentialsId),
                                AuthenticationTokens.matcher(BitbucketAuthenticator.authenticationContext(serverURL))
                        )
                );
            }
        }

        @Override
        public SCMFileSystem build(@NonNull SCMSource source, @NonNull SCMHead head, @CheckForNull SCMRevision rev)
                throws IOException, InterruptedException {
            BitbucketSCMSource src = (BitbucketSCMSource) source;

            String credentialsId = src.getCredentialsId();
            String owner = src.getRepoOwner();
            String repository = src.getRepository();
            String serverURL = src.getServerUrl();
            StandardCredentials credentials = lookupScanCredentials(src.getOwner(), credentialsId, serverURL);

            BitbucketAuthenticator authenticator = AuthenticationTokens.convert(BitbucketAuthenticator.authenticationContext(serverURL), credentials);

            String ref = null;

            if (head instanceof BranchSCMHead) {
                ref = head.getName();
            } else if (head instanceof PullRequestSCMHead prHead) {
                // working on a pull request - can be either "HEAD" or "MERGE"
                if (prHead.getRepository() == null) { // check access to repository (might be forked)
                    return null;
                }

                if (BitbucketApiUtils.isCloud(serverURL)) {
                    // support lightweight checkout for branches with same owner and repository
                    if (prHead.getCheckoutStrategy() == ChangeRequestCheckoutStrategy.HEAD &&
                        StringUtils.equalsIgnoreCase(prHead.getRepoOwner(), src.getRepoOwner()) &&
                        prHead.getRepository().equals(src.getRepository())) {
                        ref = prHead.getOriginName();
                    } else {
                        // Bitbucket cloud does not support refs for pull requests
                        // Makes lightweight checkout for forks and merge strategy improbable
                        // TODO waiting for cloud support: https://bitbucket.org/site/master/issues/5814/refify-pull-requests-by-making-them-a-ref
                        return null;
                    }
                } else if (prHead.getCheckoutStrategy() == ChangeRequestCheckoutStrategy.HEAD) {
                    ref = "pull-requests/" + prHead.getId() + "/from";
                } else if (prHead.getCheckoutStrategy() == ChangeRequestCheckoutStrategy.MERGE) {
                    // Bitbucket server v7 doesn't have the `merge` ref for PRs
                    // We don't return `ref` when working with v7
                    // so that pipeline falls back to heavyweight checkout properly
                    boolean ligthCheckout = BitbucketServerEndpoint.findServerVersion(serverURL) != BitbucketServerVersion.VERSION_7;
                    if (ligthCheckout) {
                        ref = "pull-requests/" + prHead.getId() + "/merge";
                    } else {
                        // returning null to fall back to heavyweight checkout
                        return null;
                    }
                }
            } else if (head instanceof BitbucketTagSCMHead) {
                ref = "tags/" + head.getName();
            } else {
                return null;
            }

            return new BitbucketSCMFileSystem(BitbucketApiFactory.newInstance(serverURL, authenticator, owner, null, repository), ref, rev);
        }
    }
}
