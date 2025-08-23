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
package com.cloudbees.jenkins.plugins.bitbucket.test.util;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCloudWorkspace;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketTeam;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudAuthor;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudBranch;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudCommit;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketCloudPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketCloudPullRequestDestination;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketCloudPullRequestRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudWebhook;
import com.cloudbees.jenkins.plugins.bitbucket.filesystem.BitbucketSCMFile;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.BitbucketSCMSourcePushHookReceiver;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFile.Type;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BitbucketClientMockUtils {

    public static BitbucketCloudApiClient getAPIClientMock(boolean includePullRequests,
            boolean includeWebHooks) throws IOException, InterruptedException {
        BitbucketCloudApiClient client = mock(BitbucketCloudApiClient.class);

        // mock branches
        BitbucketCloudBranch branch1 = getBranch("branch1", "52fc8e220d77ec400f7fc96a91d2fd0bb1bc553a");
        BitbucketCloudBranch branch2 = getBranch("branch2", "707c59ce8292c927dddb6807fcf9c3c5e7c9b00f");

        // mock branch list
        List<BitbucketCloudBranch> branches = new ArrayList<>();
        branches.add(branch1);
        branches.add(branch2);
        // add branches
        when(client.getBranches()).thenReturn(branches);
        when(client.getBranch("branch1")).thenReturn(branch1);
        when(client.getBranch("branch2")).thenReturn(branch2);
        withMockGitRepos(client);

        if (includePullRequests) {
            when(client.getPullRequests()).thenReturn(Arrays.asList(getPullRequest()));
            when(client.resolveSourceFullHash(any(BitbucketCloudPullRequest.class)))
                    .thenReturn("e851558f77c098d21af6bb8cc54a423f7cf12147");

            when(client.resolveCommit("e851558f77c098d21af6bb8cc54a423f7cf12147"))
                .thenReturn(buildCommit("no message", "2018-09-13T15:29:23+00:00", "e851558f77c098d21af6bb8cc54a423f7cf12147", "amuniz <amuniz@mail.com"));
            when(client.resolveCommit("52fc8e220d77ec400f7fc96a91d2fd0bb1bc553a"))
                .thenReturn(buildCommit("initial commit", "2018-09-10T15:29:23+00:00", "52fc8e220d77ec400f7fc96a91d2fd0bb1bc553a", "amuniz <amuniz@mail.com>"));
        }

        // mock file exists
        when(client.getFile(any()))
            .then(new Answer<SCMFile>() {
                @Override
                public SCMFile answer(InvocationOnMock invocation) throws Throwable {
                    BitbucketSCMFile scmFile = invocation.getArgument(0);
                    Type type;
                    if ("markerfile.txt".equals(scmFile.getName())
                            && (scmFile.getHash().equals("e851558f77c098d21af6bb8cc54a423f7cf12147")
                                    || scmFile.getHash().equals("52fc8e220d77ec400f7fc96a91d2fd0bb1bc553a"))) {
                        type = Type.REGULAR_FILE;
                    } else {
                        type = Type.NONEXISTENT;
                    }
                    return new BitbucketSCMFile(scmFile, scmFile.getPath(), type, scmFile.getHash());
                }
            });

        // Team discovering mocks
        when(client.getTeam()).thenReturn(getTeam());
        when(client.getRepositories()).thenReturn(getRepositories());

        // Auto-registering hooks
        if (includeWebHooks) {
            when(client.getWebHooks()).thenReturn(Collections.emptyList())
                // Second call
                .thenReturn(getWebHooks());
        }
        when(client.isPrivate()).thenReturn(true);

        return client;
    }

    private static List<BitbucketCloudWebhook> getWebHooks() {
        BitbucketCloudWebhook hook = new BitbucketCloudWebhook();
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
                new BitbucketHref("http","https://bitbucket.org/amuniz/repo1.git"),
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
                new BitbucketHref("http", "https://bitbucket.org/amuniz/repo2.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/amuniz/repo2.git")
        ));
        r2.setLinks(links);
        BitbucketCloudRepository r3 = new BitbucketCloudRepository();
        // test mock hack to avoid a lot of harness code
        r3.setFullName("amuniz/test-repos");
        links = new HashMap<>();
        links.put("self", Collections.singletonList(
                new BitbucketHref("https://api.bitbucket.org/2.0/repositories/amuniz/test-repos")
        ));
        links.put("clone", Arrays.asList(
                new BitbucketHref("http", "https://bitbucket.org/amuniz/test-repos.git"),
                new BitbucketHref("ssh", "ssh://git@bitbucket.org/amuniz/test-repos.git")
        ));
        r3.setLinks(links);
        return Arrays.asList(r1, r2, r3);
    }

    private static BitbucketTeam getTeam() {
        BitbucketCloudWorkspace team = new BitbucketCloudWorkspace("myteam");
        return team;
    }

    private static void withMockGitRepos(BitbucketApi bitbucket) throws IOException, InterruptedException {
        BitbucketCloudRepository repo = new BitbucketCloudRepository();
        repo.setFullName("amuniz/test-repos");
        repo.setPrivate(true);
        HashMap<String, List<BitbucketHref>> links = new HashMap<>();
        links.put("self", Collections.singletonList(
            new BitbucketHref("https://api.bitbucket.org/2.0/repositories/amuniz/test-repos")
        ));
        links.put("clone", Arrays.asList(
            new BitbucketHref("http", "https://bitbucket.org/amuniz/test-repos.git"),
            new BitbucketHref("ssh", "ssh://git@bitbucket.org/amuniz/test-repos.git")
        ));
        repo.setLinks(links);
        when(bitbucket.getRepository()).thenReturn(repo);
    }

    private static BitbucketCloudBranch getBranch(String name, String hash) {
        return new BitbucketCloudBranch(name,hash,0);
    }

    private static BitbucketCloudPullRequest getPullRequest() {
        BitbucketCloudPullRequest pr = new BitbucketCloudPullRequest();

        BitbucketCloudBranch branch = new BitbucketCloudBranch("my-feature-branch", null, 0);
        BitbucketCloudCommit commit = buildCommit("no message", "2018-09-13T15:29:23+00:00", "e851558f77c098d21af6bb8cc54a423f7cf12147", "amuniz <amuniz@mail.com>");
        BitbucketCloudRepository repository = new BitbucketCloudRepository();
        repository.setFullName("otheruser/test-repos");

        pr.setSource(new BitbucketCloudPullRequestRepository(repository, branch, commit));

        commit = buildCommit("initial commit", "2018-09-10T15:29:23+00:00", "52fc8e220d77ec400f7fc96a91d2fd0bb1bc553a", "amuniz <amuniz@mail.com>");
        branch = new BitbucketCloudBranch("branch1", null, 0);
        repository = new BitbucketCloudRepository();
        repository.setFullName("amuniz/test-repos");
        pr.setDestination(new BitbucketCloudPullRequestDestination(repository, branch, commit));

        pr.setId("23");
        pr.setAuthor(new BitbucketCloudPullRequest.Author());
        pr.setLinks(new BitbucketCloudPullRequest.Links("https://bitbucket.org/amuniz/test-repos/pull-requests/23"));
        return pr;
    }

    private static BitbucketCloudCommit buildCommit(String message, String date, String hash, String authorName) {
        BitbucketCloudAuthor author = new BitbucketCloudAuthor(authorName);
        return new BitbucketCloudCommit(message, date, hash, author, author, Collections.emptyList());
    }

    public static TaskListener getTaskListenerMock() {
        TaskListener mockTaskListener = mock(TaskListener.class);
        when(mockTaskListener.getLogger()).thenReturn(System.out);
        return mockTaskListener;
    }

}
