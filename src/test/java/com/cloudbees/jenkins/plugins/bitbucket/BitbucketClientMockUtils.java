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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudBranch;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudCommit;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketPullRequestValue;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketPullRequestValue.Author;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketPullRequestValueRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketRepositoryHook;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudTeam;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.BitbucketSCMSourcePushHookReceiver;

import hudson.model.TaskListener;
import jenkins.model.Jenkins;

public class BitbucketClientMockUtils {

    public static BitbucketCloudApiClient getAPIClientMock(RepositoryType type, boolean includePullRequests, 
            boolean includeWebHooks) {
        BitbucketCloudApiClient bitbucket = mock(BitbucketCloudApiClient.class);
        // mock branch list
        List<BitbucketCloudBranch> branches = new ArrayList<BitbucketCloudBranch>();
        branches.add(getBranch("branch1", "52fc8e220d77ec400f7fc96a91d2fd0bb1bc553a"));
        branches.add(getBranch("branch2", "707c59ce8292c927dddb6807fcf9c3c5e7c9b00f"));
        // add branches
        when(bitbucket.getBranches()).thenReturn(branches);
        if (RepositoryType.MERCURIAL == type) {
            withMockMercurialRepos(bitbucket);
        } else {
            withMockGitRepos(bitbucket);
        }

        if (includePullRequests) {
            when(bitbucket.getPullRequests()).thenReturn(Arrays.asList(getPullRequest()));
            when(bitbucket.checkPathExists("my-feature-branch", "markerfile.txt")).thenReturn(true);
            when(bitbucket.resolveSourceFullHash(any(BitbucketPullRequestValue.class)))
                    .thenReturn("e851558f77c098d21af6bb8cc54a423f7cf12147");
        }

        // mock file exists
        when(bitbucket.checkPathExists("branch1", "markerfile.txt")).thenReturn(true);
        when(bitbucket.checkPathExists("branch2", "markerfile.txt")).thenReturn(false);

        // Team discovering mocks
        when(bitbucket.getTeam()).thenReturn(getTeam());
        when(bitbucket.getRepositories()).thenReturn(getRepositories());

        // Auto-registering hooks
        if (includeWebHooks) {
            when(bitbucket.getWebHooks()).thenReturn(Collections.EMPTY_LIST)
                // Second call
                .thenReturn(getWebHooks());
        }
        when(bitbucket.isPrivate()).thenReturn(true);

        return bitbucket;
    }

    public static BitbucketCloudApiClient getAPIClientMock(RepositoryType type, boolean includePullRequests) {
        return getAPIClientMock(type, includePullRequests, false);
    }

    private static List<BitbucketRepositoryHook> getWebHooks() {
        BitbucketRepositoryHook hook = new BitbucketRepositoryHook();
        hook.setUrl(Jenkins.getActiveInstance().getRootUrl() + BitbucketSCMSourcePushHookReceiver.FULL_PATH);
        return Arrays.asList(hook);
    }

    private static List<BitbucketCloudRepository> getRepositories() {
        BitbucketCloudRepository r1 = new BitbucketCloudRepository();
        r1.setFullName("myteam/repo1");
        BitbucketCloudRepository r2 = new BitbucketCloudRepository();
        r2.setFullName("myteam/repo2");
        BitbucketCloudRepository r3 = new BitbucketCloudRepository();
        // test mock hack to avoid a lot of harness code
        r3.setFullName("amuniz/test-repos");
        return Arrays.asList(r1, r2, r3);
    }

    private static BitbucketCloudTeam getTeam() {
        BitbucketCloudTeam t = new BitbucketCloudTeam();
        t.setName("myteam");
        t.setDisplayName("This is my team");
        return t;
    }

    private static void withMockGitRepos(BitbucketApi bitbucket) {
        BitbucketCloudRepository repo = new BitbucketCloudRepository();
        repo.setScm("git");
        repo.setFullName("amuniz/test-repos");
        repo.setPrivate(true);
        when(bitbucket.getRepository()).thenReturn(repo);
    }

    private static void withMockMercurialRepos(BitbucketApi bitbucket) {
        BitbucketCloudRepository repo = new BitbucketCloudRepository();
        repo.setScm("hg");
        repo.setFullName("amuniz/test-repos");
        repo.setPrivate(true);
        when(bitbucket.getRepository()).thenReturn(repo);
    }

    private static BitbucketCloudBranch getBranch(String name, String hash) {
        BitbucketCloudBranch b = new BitbucketCloudBranch();
        b.setName(name);
        b.setRawNode(hash);
        return b;
    }

    private static BitbucketPullRequestValue getPullRequest() {
        BitbucketPullRequestValue pr = new BitbucketPullRequestValue();
        BitbucketPullRequestValueRepository source = new BitbucketPullRequestValueRepository();

        BitbucketCloudBranch branch = new BitbucketCloudBranch();
        branch.setName("my-feature-branch");
        source.setBranch(branch);

        BitbucketCloudCommit commit = new BitbucketCloudCommit();
        commit.setHash("e851558f77c098d21af6bb8cc54a423f7cf12147");
        source.setCommit(commit);

        BitbucketCloudRepository repository = new BitbucketCloudRepository();
        repository.setFullName("otheruser/test-repos");
        source.setRepository(repository);

        pr.setSource(source);

        pr.setId("23");
        pr.setAuthor(new BitbucketPullRequestValue.Author("amuniz"));
        pr.setLinks(new BitbucketPullRequestValue.Links("https://bitbucket.org/amuniz/test-repos/pull-requests/23"));
        return pr;
    }

    public static TaskListener getTaskListenerMock() {
        TaskListener mockTaskListener = mock(TaskListener.class);
        when(mockTaskListener.getLogger()).thenReturn(System.out);
        return mockTaskListener;
    }

}
