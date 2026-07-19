/*
 * The MIT License
 *
 * Copyright (c) 2024, CloudBees, Inc.
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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketMockApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudBranch;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.test.util.BitbucketClientMockUtils;
import com.cloudbees.jenkins.plugins.bitbucket.trait.BranchDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.TagDiscoveryTrait;
import hudson.model.TaskListener;
import java.util.Arrays;
import jenkins.plugins.git.AbstractGitSCMSource.SCMRevisionImpl;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceOwner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WithJenkins
class BitbucketSCMSourceRetrieveByNameTest {

    @SuppressWarnings("unused")
    private static JenkinsRule rule;

    @BeforeAll
    static void init(JenkinsRule rule) {
        BitbucketSCMSourceRetrieveByNameTest.rule = rule;
    }

    @BeforeEach
    void clearMockFactory() {
        BitbucketMockApiFactory.clear();
    }

    @Test
    void retrieveByBranchName() throws Exception {
        BitbucketCloudApiClient client = BitbucketClientMockUtils.getAPIClientMock(false, false);
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, client);

        BitbucketSCMSource source = createSource();

        SCMRevision revision = source.retrieve("branch1",
                BitbucketClientMockUtils.getTaskListenerMock(), null);

        assertThat(revision).isNotNull();
        assertThat(revision).isInstanceOf(BitbucketGitSCMRevision.class);
        assertThat(revision.getHead()).isInstanceOf(BranchSCMHead.class);
        assertThat(revision.getHead().getName()).isEqualTo("branch1");
        assertThat(((SCMRevisionImpl) revision).getHash())
                .isEqualTo("52fc8e220d77ec400f7fc96a91d2fd0bb1bc553a");
    }

    @Test
    void retrieveByTagName() throws Exception {
        BitbucketCloudApiClient client = BitbucketClientMockUtils.getAPIClientMock(false, false);

        // Tag lookup: branch lookup returns null, then tag lookup returns a tag
        when(client.getBranch("v1.0")).thenReturn(null);
        when(client.getTag("v1.0")).thenReturn(
                new BitbucketCloudBranch("v1.0", "aabbccdd001122334455667788990011aabbccdd", 1609459200000L));

        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, client);

        BitbucketSCMSource source = createSource();

        SCMRevision revision = source.retrieve("v1.0",
                BitbucketClientMockUtils.getTaskListenerMock(), null);

        assertThat(revision).isNotNull();
        assertThat(revision).isInstanceOf(BitbucketTagSCMRevision.class);
        assertThat(revision.getHead()).isInstanceOf(BitbucketTagSCMHead.class);
        assertThat(revision.getHead().getName()).isEqualTo("v1.0");
        assertThat(((SCMRevisionImpl) revision).getHash())
                .isEqualTo("aabbccdd001122334455667788990011aabbccdd");
    }

    @Test
    void retrieveByCommitHash() throws Exception {
        BitbucketCloudApiClient client = BitbucketClientMockUtils.getAPIClientMock(true, false);

        String commitHash = "e851558f77c098d21af6bb8cc54a423f7cf12147";

        // Commit hash lookup: branch and tag lookups return null
        when(client.getBranch(commitHash)).thenReturn(null);
        when(client.getTag(commitHash)).thenReturn(null);

        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, client);

        BitbucketSCMSource source = createSource();

        SCMRevision revision = source.retrieve(commitHash,
                BitbucketClientMockUtils.getTaskListenerMock(), null);

        assertThat(revision).isNotNull();
        assertThat(revision).isInstanceOf(BitbucketGitSCMRevision.class);
        assertThat(revision.getHead()).isInstanceOf(BranchSCMHead.class);
        assertThat(revision.getHead().getName()).isEqualTo(commitHash);
        assertThat(((SCMRevisionImpl) revision).getHash()).isEqualTo(commitHash);
    }

    @Test
    void retrieveReturnsNullWhenNothingMatches() throws Exception {
        BitbucketCloudApiClient client = BitbucketClientMockUtils.getAPIClientMock(false, false);

        String nonexistent = "nonexistent";

        // Nothing matches
        when(client.getBranch(nonexistent)).thenReturn(null);
        when(client.getTag(nonexistent)).thenReturn(null);
        when(client.resolveCommit(nonexistent)).thenReturn(null);

        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, client);

        BitbucketSCMSource source = createSource();

        SCMRevision revision = source.retrieve(nonexistent,
                BitbucketClientMockUtils.getTaskListenerMock(), null);

        assertThat(revision).isNull();
    }

    private BitbucketSCMSource createSource() {
        BitbucketSCMSource source = new BitbucketSCMSource("amuniz", "test-repos");
        source.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, true),
                new TagDiscoveryTrait()
        ));
        source.setOwner(getSCMSourceOwnerMock());
        return source;
    }

    private SCMSourceOwner getSCMSourceOwnerMock() {
        SCMSourceOwner mocked = mock(SCMSourceOwner.class);
        when(mocked.getSCMSourceCriteria(any(SCMSource.class))).thenReturn(new SCMSourceCriteria() {

            @Override
            public boolean isHead(Probe probe, TaskListener listener) {
                return true;
            }

            @Override
            public int hashCode() {
                return getClass().hashCode();
            }

            @Override
            public boolean equals(Object obj) {
                return getClass().isInstance(obj);
            }
        });
        return mocked;
    }
}
