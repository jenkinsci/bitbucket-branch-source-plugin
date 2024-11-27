/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfiguratorException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class WebhookConfigurationTest {

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Before
    public void config() throws ConfiguratorException {
        ConfigurationAsCode.get().configure(
                getClass().getResource(getClass().getSimpleName() + "/configuration-as-code.yml").toString());
    }

    @Test
    public void given_instanceWithServerVersion6_when_getHooks_SERVER_PR_RVWR_UPDATE_EVENT_exists() {
        WebhookConfiguration whc = new WebhookConfiguration();
        BitbucketSCMSource owner = Mockito.mock(BitbucketSCMSource.class);
        final String server = "http://bitbucket.example.com:8088";
        when(owner.getServerUrl()).thenReturn(server);
        when(owner.getEndpointJenkinsRootUrl()).thenReturn(server);
        BitbucketWebHook hook = whc.getHook(owner);
        assertTrue(hook.getEvents().contains(HookEventType.SERVER_PULL_REQUEST_REVIEWER_UPDATED.getKey()));
    }

    @Test
    public void given_instanceWithServerVersion6_5_when_getHooks_SERVER_MIRROR_REPO_SYNC_EVENT_exists() {
        WebhookConfiguration whc = new WebhookConfiguration();
        BitbucketSCMSource owner = Mockito.mock(BitbucketSCMSource.class);
        final String server = "http://bitbucket.example.com:8091";
        when(owner.getServerUrl()).thenReturn(server);
        when(owner.getEndpointJenkinsRootUrl()).thenReturn(server);
        when(owner.getEndpointJenkinsRootUrl()).thenReturn(server);
        when(owner.getMirrorId()).thenReturn("dummy-mirror-id");
        BitbucketWebHook hook = whc.getHook(owner);
        assertTrue(hook.getEvents().contains(HookEventType.SERVER_MIRROR_REPO_SYNCHRONIZED.getKey()));
    }

    @Test
    public void given_instanceWithServerVersion510_when_getHooks_SERVER_PR_RVWR_UPDATE_EVENT_exists() {
        WebhookConfiguration whc = new WebhookConfiguration();
        BitbucketSCMSource owner = Mockito.mock(BitbucketSCMSource.class);
        final String server = "http://bitbucket.example.com:8089";
        when(owner.getServerUrl()).thenReturn(server);
        when(owner.getEndpointJenkinsRootUrl()).thenReturn(server);
        BitbucketWebHook hook = whc.getHook(owner);
        assertTrue(hook.getEvents().contains(HookEventType.SERVER_PULL_REQUEST_REVIEWER_UPDATED.getKey()));
    }

    @Test
    public void given_instanceWithServerVersion59_when_getHooks_SERVER_PR_RVWR_UPDATE_EVENT_not_exists() {
        WebhookConfiguration whc = new WebhookConfiguration();
        BitbucketSCMSource owner = Mockito.mock(BitbucketSCMSource.class);
        final String server = "http://bitbucket.example.com:8090";
        when(owner.getServerUrl()).thenReturn(server);
        when(owner.getEndpointJenkinsRootUrl()).thenReturn(server);
        BitbucketWebHook hook = whc.getHook(owner);
        assertFalse(hook.getEvents().contains(HookEventType.SERVER_PULL_REQUEST_REVIEWER_UPDATED.getKey()));
    }

    @Test
    public void given_instanceWithServerVersion59_when_getHooks_SERVER_PR_MOD_EVENT_not_exists() {
        WebhookConfiguration whc = new WebhookConfiguration();
        BitbucketSCMSource owner = Mockito.mock(BitbucketSCMSource.class);
        final String server = "http://bitbucket.example.com:8090";
        when(owner.getServerUrl()).thenReturn(server);
        when(owner.getEndpointJenkinsRootUrl()).thenReturn(server);
        BitbucketWebHook hook = whc.getHook(owner);
        assertFalse(hook.getEvents().contains(HookEventType.SERVER_PULL_REQUEST_MODIFIED.getKey()));
    }

    @Test
    public void given_instanceWithServerVersion7_when_getHooks_SERVER_PR_FROM_REF_UPDATED_EVENT_exists() {
        WebhookConfiguration whc = new WebhookConfiguration();
        BitbucketSCMSource owner = Mockito.mock(BitbucketSCMSource.class);
        final String server = "http://bitbucket.example.com:8087";
        when(owner.getServerUrl()).thenReturn(server);
        when(owner.getEndpointJenkinsRootUrl()).thenReturn(server);
        BitbucketWebHook hook = whc.getHook(owner);
        assertTrue(hook.getEvents().contains(HookEventType.SERVER_PULL_REQUEST_FROM_REF_UPDATED.getKey()));
    }

}
