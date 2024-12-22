package com.cloudbees.jenkins.plugins.bitbucket;

import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.extensions.GitSCMExtension;
import java.net.URISyntaxException;
import java.util.List;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.GitClient;

/**
 * If specified commit hashes are not found in repository then fetch
 * specified branches from remote.
 */
//TODO be attention serialized in config.xml of the job as extension child of hudson.plugins.git.GitSCM. Provide a xml alias when move to package com.cloudbees.jenkins.plugins.bitbucket.impl.extension
public class FallbackToOtherRepositoryGitSCMExtension extends GitSCMExtension {

    private final String cloneLink;
    private final String remoteName;
    private final List<BranchWithHash> branchWithHashes;

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
}
