/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket.endpoints;

import hudson.util.FormValidation;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class BitbucketServerEndpointTest {

    @Test
    public void smokes() {
        BitbucketServerEndpoint endpoint = new BitbucketServerEndpoint("Dummy", "http://dummy.example.com", false, null);

        assertThat(endpoint.getDisplayName(), is("Dummy"));
        assertThat(endpoint.getServerUrl(), is( "http://dummy.example.com"));
    }

    @Test
    public void getRepositoryUrl() {
        BitbucketServerEndpoint endpoint = new BitbucketServerEndpoint("Dummy", "http://dummy.example.com", false, null);

        assertThat(endpoint.getRepositoryUrl("TST", "test-repo"), is("http://dummy.example.com/projects/TST/repos/test-repo"));
        assertThat(endpoint.getRepositoryUrl("~tester", "test-repo"), is("http://dummy.example.com/users/tester/repos/test-repo"));
    }

    @Test
    public void given__badUrl__when__check__then__fail() {
        assertThat(BitbucketServerEndpoint.DescriptorImpl.doCheckServerUrl("").kind, is(FormValidation.Kind.ERROR));
    }

    @Test
    public void given__goodUrl__when__check__then__ok() {
        assertThat(BitbucketServerEndpoint.DescriptorImpl.doCheckServerUrl("http://dummy.example.com").kind, is(FormValidation.Kind.OK));
    }
}
