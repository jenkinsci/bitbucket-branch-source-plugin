/*
 * The MIT License
 *
 * Copyright (c) 2025, Nikolas Falco
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

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMNavigator;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.OriginPullRequestDiscoveryTrait;
import hudson.scm.SCM;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import jenkins.scm.api.SCMEvent.Type;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMSource;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PullRequestHookProcessorTest {

    private PullRequestHookProcessor sut;
    protected SCMHeadEvent<?> scmEvent;

    @BeforeEach
    void setup() {
        sut = new PullRequestHookProcessor() {
            @Override
            protected void notifyEvent(SCMHeadEvent<?> event, int delaySeconds) {
                PullRequestHookProcessorTest.this.scmEvent = event;
            }
        };
    }

    @Test
    void test_pullrequest_created() throws Exception {
        sut.process(HookEventType.PULL_REQUEST_CREATED, loadResource("pullrequest_created.json"), BitbucketType.CLOUD, "origin");

        PREvent event = (PREvent) scmEvent;
        assertThat(event).isNotNull();
        assertThat(event.getSourceName()).isEqualTo("test-repos");
        assertThat(event.getType()).isEqualTo(Type.CREATED);
        assertThat(event.isMatch(mock(SCM.class))).isFalse();
    }

    @Test
    void test_pullrequest_rejected() throws Exception {
        sut.process(HookEventType.PULL_REQUEST_DECLINED, loadResource("pullrequest_rejected.json"), BitbucketType.CLOUD, "origin");

        PREvent event = (PREvent) scmEvent;
        assertThat(event).isNotNull();
        assertThat(event.getSourceName()).isEqualTo("test-repos");
        assertThat(event.getType()).isEqualTo(Type.REMOVED);
        assertThat(event.isMatch(mock(SCM.class))).isFalse();
    }

    @Test
    void test_pullrequest_created_when_event_match_SCMNavigator() throws Exception {
        sut.process(HookEventType.PULL_REQUEST_CREATED, loadResource("pullrequest_created.json"), BitbucketType.CLOUD, "origin");

        PREvent event = (PREvent) scmEvent;
        // discard any scm navigator than bitbucket
        assertThat(event.isMatch(mock(SCMNavigator.class))).isFalse();

        BitbucketSCMNavigator scmNavigator = new BitbucketSCMNavigator("amuniz");
        // cloud could not filter by ProjectKey
        assertThat(event.isMatch(scmNavigator)).isTrue();
        // if set must match the project of repository from which the hook is generated
        scmNavigator.setProjectKey("PRJKEY");
        assertThat(event.isMatch(scmNavigator)).isTrue();
        // project key is case sensitive
        scmNavigator.setProjectKey("prjkey");
        assertThat(event.isMatch(scmNavigator)).isFalse();

        // workspace/owner is case insensitive
        scmNavigator = new BitbucketSCMNavigator("AMUNIZ");
        assertThat(event.isMatch(scmNavigator)).isTrue();
    }

    @WithJenkins
    @Test
    void test_pullrequest_created_when_event_match_SCMSource(JenkinsRule r) throws Exception {
        sut.process(HookEventType.PULL_REQUEST_CREATED, loadResource("pullrequest_created.json"), BitbucketType.CLOUD, "origin");

        PREvent event = (PREvent) scmEvent;
        // discard any scm navigator than bitbucket
        assertThat(event.isMatch(mock(SCMSource.class))).isFalse();

        BitbucketSCMSource scmSource = new BitbucketSCMSource("amuniz", "test-repos");
        // skip scm source that has not been configured to discover PRs
        assertThat(event.isMatch(scmSource)).isFalse();

        scmSource.setTraits(List.of(new OriginPullRequestDiscoveryTrait(2)));
        assertThat(event.isMatch(scmSource)).isTrue();

        // workspace/owner is case insensitive
        scmSource = new BitbucketSCMSource("AMUNIZ", "TEST-REPOS");
        scmSource.setTraits(List.of(new OriginPullRequestDiscoveryTrait(1)));
        assertThat(event.isMatch(scmSource)).isTrue();

        assertThat(event.getPullRequests(scmSource))
            .isNotEmpty()
            .hasSize(1);
    }

    @WithJenkins
    @Test
    void test_pullrequest_rejected_returns_empty_pullrequests_when_event_match_SCMSource(JenkinsRule r) throws Exception {
        sut.process(HookEventType.PULL_REQUEST_DECLINED, loadResource("pullrequest_rejected.json"), BitbucketType.CLOUD, "origin");

        PREvent event = (PREvent) scmEvent;

        BitbucketSCMSource scmSource = new BitbucketSCMSource("aMUNIZ", "test-repos");
        scmSource.setTraits(List.of(new OriginPullRequestDiscoveryTrait(2)));
        assertThat(event.isMatch(scmSource)).isTrue();
        assertThat(event.getPullRequests(scmSource)).isEmpty();
    }

    private String loadResource(String resource) throws IOException {
        try (InputStream stream = this.getClass().getResourceAsStream("cloud/" + resource)) {
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        }
    }
}
