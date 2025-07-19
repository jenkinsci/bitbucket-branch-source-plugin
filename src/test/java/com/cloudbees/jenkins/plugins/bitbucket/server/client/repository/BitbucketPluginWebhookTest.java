/*
 * The MIT License
 *
 * Copyright (c) 2025, Falco Nikolas
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
package com.cloudbees.jenkins.plugins.bitbucket.server.client.repository;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BitbucketPluginWebhookTest {

    private BitbucketPluginWebhook sut;

    @BeforeEach
    void setup() {
        sut = new BitbucketPluginWebhook();
    }

    @Test
    void test_getEvents() {
        sut.setPrCommented(true);
        sut.setTagCreated(true);
        sut.setPrCreated(true);
        sut.setPrDeclined(true);
        sut.setPrDeleted(true);
        sut.setPrMerged(true);
        assertThat(sut.getEvents()).containsOnly(
                "PULL_REQUEST_COMMENT",
                "PULL_REQUEST_DECLINED",
                "PULL_REQUEST_DELETED",
                "PULL_REQUEST_OPENED",
                "PULL_REQUEST_MERGED",
                "TAG_CREATED");
    }

    @Test
    void test_setEvents() {
        sut.setEvents(List.of(
                "ABSTRACT_REPOSITORY_REFS_CHANGED",
                "BRANCH_CREATED",
                "BRANCH_DELETED",
                "PULL_REQUEST_DECLINED",
                "PULL_REQUEST_DELETED",
                "PULL_REQUEST_MERGED",
                "PULL_REQUEST_OPENED",
                "PULL_REQUEST_REOPENED",
                "PULL_REQUEST_UPDATED",
                "REPOSITORY_MIRROR_SYNCHRONIZED",
                "TAG_CREATED"));

        assertThat(sut.isPrCreated()).isTrue();
        assertThat(sut.isPrDeclined()).isTrue();
        assertThat(sut.isPrDeleted()).isTrue();
        assertThat(sut.isPrCommented()).isFalse();
        assertThat(sut.isPrMerged()).isTrue();
        assertThat(sut.isPrReopened()).isTrue();
        assertThat(sut.isPrRescoped()).isFalse();
        assertThat(sut.isPrUpdated()).isTrue();
        assertThat(sut.isBranchCreated()).isTrue();
        assertThat(sut.isBranchDeleted()).isTrue();
        assertThat(sut.isRepoMirrorSynced()).isTrue();
        assertThat(sut.isRepoPush()).isTrue();
        assertThat(sut.isTagCreated()).isTrue();
        assertThat(sut.isBuildStatus()).isFalse();
    }
}
