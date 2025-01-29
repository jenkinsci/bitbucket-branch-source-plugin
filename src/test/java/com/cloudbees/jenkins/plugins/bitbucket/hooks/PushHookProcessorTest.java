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

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketTagSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.BranchSCMHead;
import hudson.scm.SCM;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import jenkins.plugins.git.AbstractGitSCMSource.SCMRevisionImpl;
import jenkins.scm.api.SCMEvent.Type;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMRevision;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PushHookProcessorTest {
    private PushHookProcessor sut;
    private SCMHeadEvent<?> scmEvent;

    @BeforeEach
    void setup() {
        sut = new PushHookProcessor() {
            @Override
            protected void notifyEvent(SCMHeadEvent<?> event, int delaySeconds) {
                PushHookProcessorTest.this.scmEvent = event;
            }
        };
    }

    @Test
    void test_tag_created() throws Exception {
        sut.process(HookEventType.PUSH, loadResource("tag_created.json"), BitbucketType.CLOUD, "origin");

        PushEvent event = (PushEvent) scmEvent;
        assertThat(event).isNotNull();
        assertThat(event.getSourceName()).isEqualTo("test-repos");
        assertThat(event.getType()).isEqualTo(Type.CREATED);
        assertThat(event.isMatch(mock(SCM.class))).isFalse();

        BitbucketSCMSource scmSource = new BitbucketSCMSource("AMUNIZ", "test-repos");
        Map<SCMHead, SCMRevision> heads = event.heads(scmSource);
        assertThat(heads.keySet())
            .first()
            .usingRecursiveComparison()
            .isEqualTo(new BitbucketTagSCMHead("simple-tag", 1738608795000l)); // verify is using last commit date
    }

    @Test
    void test_annotated_tag_created() throws Exception {
        sut.process(HookEventType.PUSH, loadResource("annotated_tag_created.json"), BitbucketType.CLOUD, "origin");

        PushEvent event = (PushEvent) scmEvent;
        assertThat(event).isNotNull();
        assertThat(event.getSourceName()).isEqualTo("test-repos");
        assertThat(event.getType()).isEqualTo(Type.CREATED);
        assertThat(event.isMatch(mock(SCM.class))).isFalse();

        BitbucketSCMSource scmSource = new BitbucketSCMSource("AMUNIz", "test-repos");
        Map<SCMHead, SCMRevision> heads = event.heads(scmSource);
        assertThat(heads.keySet())
            .first()
            .usingRecursiveComparison()
            .isEqualTo(new BitbucketTagSCMHead("test-tag", 1738608816000l));
    }

    @Test
    void test_commmit_created() throws Exception {
        sut.process(HookEventType.PUSH, loadResource("commit_created.json"), BitbucketType.CLOUD, "origin");

        PushEvent event = (PushEvent) scmEvent;
        assertThat(event).isNotNull();
        assertThat(event.getSourceName()).isEqualTo("test-repos");
        assertThat(event.getType()).isEqualTo(Type.UPDATED);
        assertThat(event.isMatch(mock(SCM.class))).isFalse();

        BitbucketSCMSource scmSource = new BitbucketSCMSource("aMUNIZ", "test-repos");
        Map<SCMHead, SCMRevision> heads = event.heads(scmSource);
        assertThat(heads.keySet())
            .first()
            .usingRecursiveComparison()
            .isEqualTo(new BranchSCMHead("feature/issue-819"));
        assertThat(heads.values())
            .first()
            .usingRecursiveComparison()
            .isEqualTo(new SCMRevisionImpl(new BranchSCMHead("feature/issue-819"), "5ecffa3874e96920f24a2b3c0d0038e47d5cd1a4"));
    }

    private String loadResource(String resource) throws IOException {
        try (InputStream stream = this.getClass().getResourceAsStream("cloud/" + resource)) {
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        }
    }

}
