/*
 * The MIT License
 *
 * Copyright (c) 2026, Nikolas Falco
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
package com.cloudbees.jenkins.plugins.bitbucket.impl.details;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketGitSCMRevision;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketLink;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.BranchSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMRevision;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@SuppressWarnings("rawtypes")
class BitbucketCommitDetailTest {

    private class BitbucketCommitImpl implements BitbucketCommit {

        private String hash;

        public BitbucketCommitImpl(String hash) {
            this.hash = hash;
        }

        @Override
        public String getAuthor() {
            return null;
        }

        @Override
        public String getMessage() {
            return null;
        }

        @Override
        public String getDate() {
            return null;
        }

        @Override
        public String getHash() {
            return hash;
        }

        @Override
        public long getDateMillis() {
            return 0;
        }

    }

    @Test
    void displayName_and_link_forSCMRevisionImpl() {
        Run run = mock(Run.class);
        Job job = mock(Job.class);
        when(run.getParent()).thenReturn(job);

        SCMRevisionAction action = mock(SCMRevisionAction.class);
        when(run.getAction(SCMRevisionAction.class)).thenReturn(action);

        AbstractGitSCMSource.SCMRevisionImpl revision = mock(AbstractGitSCMSource.SCMRevisionImpl.class);
        when(revision.getHash()).thenReturn("abcdef1234567890");
        when(action.getRevision()).thenReturn(revision);

        try (MockedStatic<SCMSource.SourceByItem> mocked = mockStatic(SCMSource.SourceByItem.class)) {
            BitbucketSCMSource instance = mock(BitbucketSCMSource.class);
            when(instance.getServerUrl()).thenReturn("https://bitbucket.org");
            when(instance.getRepoOwner()).thenReturn("amuniz");
            when(instance.getRepository()).thenReturn("test-repos");
            mocked.when(() -> SCMSource.SourceByItem.findSource(any())).thenReturn(instance);

            BitbucketCommitDetail detail = new BitbucketCommitDetail(run);

            assertThat(detail.getDisplayName()).isEqualTo("abcdef1234567890");
            assertThat(detail.getLink()).isEqualTo("https://bitbucket.org/amuniz/test-repos/commits/abcdef1234567890");
        }
    }

    @Test
    void displayName_and_link_forPRRevision() {
        Run run = mock(Run.class);
        Job job = mock(Job.class);
        when(run.getParent()).thenReturn(job);

        SCMRevisionAction action = mock(SCMRevisionAction.class);
        when(run.getAction(SCMRevisionAction.class)).thenReturn(action);

        PullRequestSCMRevision prRevision = mock(PullRequestSCMRevision.class);
        when(prRevision.getPull()).thenReturn(new BitbucketGitSCMRevision(new BranchSCMHead("test-repos"), new BitbucketCommitImpl("1234567deadbeef")));
        when(action.getRevision()).thenReturn(prRevision);

        BitbucketLink link = mock(BitbucketLink.class);
        when(link.getUrl()).thenReturn("https://bitbucket.org/amuniz/test-repos");
        when(job.getAction(BitbucketLink.class)).thenReturn(link);

        BitbucketCommitDetail detail = new BitbucketCommitDetail(run);

        try (MockedStatic<SCMSource.SourceByItem> mocked = mockStatic(SCMSource.SourceByItem.class)) {
            BitbucketSCMSource instance = mock(BitbucketSCMSource.class);
            when(instance.getServerUrl()).thenReturn("https://bitbucket.org");
            when(instance.getRepoOwner()).thenReturn("amuniz");
            when(instance.getRepository()).thenReturn("test-repos");
            mocked.when(() -> SCMSource.SourceByItem.findSource(any())).thenReturn(instance);

            assertThat(detail.getDisplayName()).isEqualTo("1234567deadbeef");
            assertThat(detail.getLink()).isEqualTo("https://bitbucket.org/amuniz/test-repos/commits/1234567deadbeef");
        }

    }

    @Test
    void returnsNull_whenNoRevisionAction() {
        Run<?, ?> run = mock(Run.class);
        when(run.getAction(SCMRevisionAction.class)).thenReturn(null);

        BitbucketCommitDetail detail = new BitbucketCommitDetail(run);

        assertThat(detail.getDisplayName()).isNull();
        assertThat(detail.getLink()).isNull();
    }
}
