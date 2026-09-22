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

import hudson.model.Job;
import hudson.model.Run;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BitbucketBranchDetailTest {

    @SuppressWarnings("rawtypes")
    @Test
    void displaysAsExpected() {
        Run run = mock(Run.class);
        Job job = mock(Job.class);
        when(run.getParent()).thenReturn(job);
        ObjectMetadataAction metadata = mock(ObjectMetadataAction.class);
        when(metadata.getObjectDisplayName()).thenReturn("master");
        when(metadata.getObjectUrl()).thenReturn("https://bitbucket.org/amuniz/test-repos/branch/master");
        when(job.getAction(ObjectMetadataAction.class)).thenReturn(metadata);

        BitbucketBranchDetail detail = new BitbucketBranchDetail(run);

        assertEquals("master", detail.getDisplayName());
        assertEquals("https://bitbucket.org/amuniz/test-repos/branch/master", detail.getLink());
    }
}
