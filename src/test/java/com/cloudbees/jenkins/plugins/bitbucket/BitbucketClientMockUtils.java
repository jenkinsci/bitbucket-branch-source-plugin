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
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryProtocol;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudAuthor;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudBranch;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudCommit;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketPullRequestValue;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketPullRequestValueDestination;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketPullRequestValueRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudTeam;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketRepositoryHook;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.BitbucketSCMSourcePushHookReceiver;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import jenkins.model.Jenkins;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BitbucketClientMockUtils {

    public static BitbucketCloudApiClient getAPIClientMock(boolean includePullRequests,
            boolean includeWebHooks) throws IOException, InterruptedException {
        BitbucketCloudApiClient bitbucket = mock(BitbucketCloudApiClient.class);
        when(bitbucket.getRepositoryUri(any(BitbucketRepositoryProtocol.class), nullable(String.class),
            anyString(), anyString())).thenCallRealMethod();

        // mock branch list
        List<BitbucketCloudBranch> branches = new ArrayList<>();
        branches.add(getBranch("branch1", "52fc8e220d77ec400f7fc96a91d2fd0bb1bc553a"));
        branches.add(getBranch("branch2", "707c59ce8292c927dddb6807fcf9c3c5e7c9b00f"));
        // add branches
        when(bitbucket.getBranches()).thenReturn(branches);
        withMockGitRepos(bitbucket);

        if (includePullRequests) {
            when(bitbucket.getPullRequests()).thenReturn(Arrays.asList(getPullRequest()));
            when(bitbucket.checkPathExists("e851558f77c098d21af6bb8cc54a423f7cf12147", "markerfile.txt"))
                    .thenReturn(true);
            when(bitbucket.resolveSourceFullHash(any(BitbucketPullRequestValue.class)))
                    .thenReturn("e851558f77c098d21af6bb8cc54a423f7cf12147");

            BitbucketCloudAuthor author = new BitbucketCloudAuthor();
            author.setRaw("amuniz <amuniz@mail.com");
            when(bitbucket.resolveCommit("e851558f77c098d21af6bb8cc54a423f7cf12147"))
                .thenReturn(new BitbucketCloudCommit("no message", "2018-09-13T15:29:23+00:00", "e851558f77c098d21af6bb8cc54a423f7cf12147", author));
            when(bitbucket.resolveCommit("52fc8e220d77ec400f7fc96a91d2fd0bb1bc553a"))
                .thenReturn(new BitbucketCloudCommit("initial commit", "2018-09-10T15:29:23+00:00", "52fc8e220d77ec400f7fc96a91d2fd0bb1bc553a", author));
        }

        // mock file exists
        when(bitbucket.checkPathExists("52fc8e220d77ec400f7fc96a91d2fd0bb1bc553a", "markerfile.txt")).thenReturn(true);
        when(bitbucket.checkPathExists("707c59ce8292c927dddb6807fcf9c3c5e7c9b00f", "markerfile.txt")).thenReturn(false);

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

    private static List<BitbucketRepositoryHook> getWebHooks() {
        BitbucketRepositoryHook hook = new BitbucketRepositoryHook();
        hook.setUrl(Jenkins.get().getRootUrl() + BitbucketSCMSourcePushHookReceiver.FULL_PATH);
        return Collections.singletonList(hook);
    }

    private static List<BitbucketCloudRepository> getRepositories() {
        BitbucketCloudRepository r1 = new BitbucketCloudRepository();
        r1.setFullName("myteam/repo1");
        HashMap<String, List<BitbucketHref>> links = new HashMap<>();
        links.put("self", Collections.singletonList(
                new BitbucketHref("https://api.bitbucket.org/2.0/repositories/amuniz/repo1")
        ));
        links.put("clone", Arrays.asList(
                new BitbucketHref("https","https://bitbucket.org/amuniz/repo1.git"),
                new BitbucketHref("ssh","ssh://git@bitbucket.org/amuniz/repo1.git")
        ));
        r1.setLinks(links);
        BitbucketCloudRepository r2 = new BitbucketCloudRepository();
        r2.setFullName("myteam/repo2");
        links = new HashMap<>();
        links.put("self", Collections.singletonList(
                new BitbucketHref("https://api.bitbucket.org/2.0/repositories/amuniz/repo2")
        ));
        links.put("clone", Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/amuniz/repo2.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/amuniz/repo2.git")
        ));
        r1.setLinks(links);
        BitbucketCloudRepository r3 = new BitbucketCloudRepository();
        // test mock hack to avoid a lot of harness code
        r3.setFullName("amuniz/test-repos");
        links = new HashMap<>();
        links.put("self", Collections.singletonList(
                new BitbucketHref("https://api.bitbucket.org/2.0/repositories/amuniz/test-repos")
        ));
        links.put("clone", Arrays.asList(
                new BitbucketHref("https", "https://bitbucket.org/amuniz/test-repos.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/amuniz/test-repos.git")
        ));
        r1.setLinks(links);
        return Arrays.asList(r1, r2, r3);
    }

    private static BitbucketCloudTeam getTeam() {
        BitbucketCloudTeam t = new BitbucketCloudTeam();
        t.setName("myteam");
        t.setDisplayName("This is my team");
        return t;
    }

    private static void withMockGitRepos(BitbucketApi bitbucket) throws IOException, InterruptedException {
        BitbucketCloudRepository repo = new BitbucketCloudRepository();
        repo.setScm("git");
        repo.setFullName("amuniz/test-repos");
        repo.setPrivate(true);
        when(bitbucket.getRepository()).thenReturn(repo);
    }

    private static BitbucketCloudBranch getBranch(String name, String hash) {
        return new BitbucketCloudBranch(name,hash,0);
    }

    private static BitbucketPullRequestValue getPullRequest() {
        BitbucketPullRequestValue pr = new BitbucketPullRequestValue();

        BitbucketCloudBranch branch = new BitbucketCloudBranch("my-feature-branch", null, 0);
        BitbucketCloudAuthor author = new BitbucketCloudAuthor();
        author.setRaw("amuniz <amuniz@mail.com>");
        BitbucketCloudCommit commit = new BitbucketCloudCommit("no message", "2018-09-13T15:29:23+00:00", "e851558f77c098d21af6bb8cc54a423f7cf12147", author);
        BitbucketCloudRepository repository = new BitbucketCloudRepository();
        repository.setFullName("otheruser/test-repos");

        pr.setSource(new BitbucketPullRequestValueRepository(repository, branch, commit));

        commit = new BitbucketCloudCommit("initial commit", "2018-09-10T15:29:23+00:00", "52fc8e220d77ec400f7fc96a91d2fd0bb1bc553a", author);
        branch = new BitbucketCloudBranch("branch1", null, 0);
        repository = new BitbucketCloudRepository();
        repository.setFullName("amuniz/test-repos");
        pr.setDestination(new BitbucketPullRequestValueDestination(repository, branch, commit));

        pr.setId("23");
        pr.setAuthor(new BitbucketPullRequestValue.Author());
        pr.setLinks(new BitbucketPullRequestValue.Links("https://bitbucket.org/amuniz/test-repos/pull-requests/23"));
        return pr;
    }

    public static TaskListener getTaskListenerMock() {
        TaskListener mockTaskListener = mock(TaskListener.class);
        when(mockTaskListener.getLogger()).thenReturn(System.out);
        return mockTaskListener;
    }

}
