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
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryProtocol;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import com.cloudbees.jenkins.plugins.bitbucket.server.BitbucketServerWebhookImplementation;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerAPIClient;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UriResolverTest {

    @Test
    public void httpUriResolver() throws Exception {
        BitbucketApi api = new BitbucketCloudApiClient(false, 0, 0, "test", null, (BitbucketAuthenticator) null);
        assertEquals("https://bitbucket.org/user1/repo1.git", api.getRepositoryUri(
                BitbucketRepositoryProtocol.HTTP,
                null,
                "user1",
                "repo1"
        ));
        api = new BitbucketServerAPIClient("http://localhost:1234", "test", null, null, false,
                BitbucketServerWebhookImplementation.PLUGIN);
        assertEquals("http://localhost:1234/scm/user2/repo2.git", api.getRepositoryUri(
                BitbucketRepositoryProtocol.HTTP,
                null,
                "user2",
                "repo2"
        ));
        api = new BitbucketServerAPIClient("http://192.168.1.100:1234", "test", null, null, false,
                BitbucketServerWebhookImplementation.PLUGIN);
        assertEquals("http://192.168.1.100:1234/scm/user2/repo2.git", api.getRepositoryUri(
                BitbucketRepositoryProtocol.HTTP,
                null,
                "user2",
                "repo2"
        ));
        api = new BitbucketServerAPIClient("http://devtools.test:1234/git/web/", "test", null, null, false,
                BitbucketServerWebhookImplementation.PLUGIN);
        assertEquals("http://devtools.test:1234/git/web/scm/user2/repo2.git", api.getRepositoryUri(
                BitbucketRepositoryProtocol.HTTP,
                null,
                "user2",
                "repo2"
        ));
    }

    @Test
    public void sshUriResolver() throws Exception {
        BitbucketApi api = new BitbucketCloudApiClient(false, 0, 0, "test", null, (BitbucketAuthenticator) null);
        assertEquals("git@bitbucket.org:user1/repo1.git", api.getRepositoryUri(
                BitbucketRepositoryProtocol.SSH,
                null,
                "user1",
                "repo1"
        ));
        api = new BitbucketServerAPIClient("http://localhost:1234", "test", null, null, false,
                BitbucketServerWebhookImplementation.PLUGIN);
        assertEquals("ssh://git@localhost:7999/user2/repo2.git", api.getRepositoryUri(
                BitbucketRepositoryProtocol.SSH,
                "ssh://git@localhost:7999/user1/repo1.git",
                "user2",
                "repo2"
        ));
        api = new BitbucketServerAPIClient("http://myserver", "test", null, null, false,
                BitbucketServerWebhookImplementation.PLUGIN);
        assertEquals("ssh://git@myserver:7999/user2/repo2.git", api.getRepositoryUri(
                BitbucketRepositoryProtocol.SSH,
                "ssh://git@myserver:7999/user1/repo1.git",
                "user2",
                "repo2"
        ));
    }

}
