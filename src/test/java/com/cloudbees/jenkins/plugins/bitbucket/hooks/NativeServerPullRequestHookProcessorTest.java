/*
 * The MIT License
 *
 * Copyright (c) 2025, Allan Burdajewicz
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
package com.cloudbees.jenkins.plugins.bitbucket.hooks;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.BranchSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.OriginPullRequestDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.api.PullRequestBranchType;
import hudson.scm.SCM;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NativeServerPullRequestHookProcessorTest {

    private static final String SERVER_URL = "http://localhost:7990";
    private NativeServerPullRequestHookProcessor sut;
    private SCMHeadEvent<?> scmEvent;

    @BeforeEach
    void setup() {
        sut = new NativeServerPullRequestHookProcessor() {
            @Override
            protected void notifyEvent(SCMHeadEvent<?> event, int delaySeconds) {
                NativeServerPullRequestHookProcessorTest.this.scmEvent = event;
            }
        };
    }

    @WithJenkins
    @Test
    @Issue("JENKINS-75523")
    void test_pr_where_source_is_tag(JenkinsRule rule) throws Exception {
        sut.process(HookEventType.SERVER_PULL_REQUEST_OPENED, loadResource("native/prOpenFromTagPayload.json"), BitbucketType.SERVER, "origin", SERVER_URL);

        ServerHeadEvent event = (ServerHeadEvent) scmEvent;
        assertThat(event).isNotNull();
        assertThat(event.getSourceName()).isEqualTo("test-repos");
        assertThat(event.getType()).isEqualTo(SCMEvent.Type.CREATED);
        assertThat(event.isMatch(mock(SCM.class))).isFalse();

        BitbucketSCMSource scmSource = new BitbucketSCMSource("aMUNIZ", "test-repos");
        scmSource.setTraits(List.of(new OriginPullRequestDiscoveryTrait(Set.of(ChangeRequestCheckoutStrategy.HEAD))));
        Map<SCMHead, SCMRevision> heads = event.heads(scmSource);
        assertThat(heads.keySet())
            .first()
            .usingRecursiveComparison()
            .isEqualTo(new PullRequestSCMHead("PR-1", "AMUNIZ", "test-repos", "v1.0", PullRequestBranchType.TAG,
                    "1", "pr from tag", new BranchSCMHead("master"), SCMHeadOrigin.DEFAULT, ChangeRequestCheckoutStrategy.HEAD));
    }

    private String loadResource(String resource) throws IOException {
        try (InputStream stream = this.getClass().getResourceAsStream(resource)) {
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        }
    }
}
