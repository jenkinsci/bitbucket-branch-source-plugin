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
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketMockApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.WebhookAutoRegisterListener;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.cloud.CloudWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.cloud.CloudWebhookManager;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.plugin.PluginWebhookManager;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.server.ServerWebhookManager;
import com.cloudbees.jenkins.plugins.bitbucket.test.util.HookProcessorTestUtil;
import com.cloudbees.jenkins.plugins.bitbucket.trait.WebhookRegistrationTrait;
import hudson.model.listeners.ItemListener;
import hudson.util.RingBufferLogHandler;
import java.util.List;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.model.JenkinsLocationConfiguration;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

class WebhooksAutoregisterTest {

    @WithJenkins
    @Test
    void test_register_webhook_using_item_configuration(JenkinsRule rule) throws Exception {
        BitbucketApi client = BitbucketIntegrationClientFactory.getApiMockClient(BitbucketCloudEndpoint.SERVER_URL);
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, client);
        RingBufferLogHandler log = HookProcessorTestUtil.createJULTestHandler(WebhookAutoRegisterListener.class,
                CloudWebhookManager.class,
                ServerWebhookManager.class,
                PluginWebhookManager.class);

        MockMultiBranchProjectImpl p = rule.jenkins.createProject(MockMultiBranchProjectImpl.class, "test");
        BitbucketSCMSource source = new BitbucketSCMSource("amuniz", "test-repos");
        source.setTraits(List.of(new WebhookRegistrationTrait(WebhookRegistration.ITEM)));
        BranchSource branchSource = new BranchSource(source);
        branchSource.setStrategy(new DefaultBranchPropertyStrategy(null));
        p.getSourcesList().add(branchSource);
        p.scheduleBuild2(0);
        HookProcessorTestUtil.waitForLogFileMessage(rule, "Can not register hook. Jenkins root URL is not valid", log);

        setRootUrl(rule);
        p.save(); // force item listener to run onUpdated

        HookProcessorTestUtil.waitForLogFileMessage(rule, "Registering cloud hook for amuniz/test-repos", log);

    }

    @WithJenkins
    @Test
    void test_register_webhook_using_system_configuration(JenkinsRule rule) throws Exception {
        BitbucketApi client = BitbucketIntegrationClientFactory.getApiMockClient(BitbucketCloudEndpoint.SERVER_URL);
        BitbucketMockApiFactory.add(BitbucketCloudEndpoint.SERVER_URL, client);
        RingBufferLogHandler log = HookProcessorTestUtil.createJULTestHandler(WebhookAutoRegisterListener.class,
                CloudWebhookManager.class,
                ServerWebhookManager.class,
                PluginWebhookManager.class);

        BitbucketEndpointConfiguration.get().setEndpoints(List.of(new BitbucketCloudEndpoint(false, 0, 0, new CloudWebhookConfiguration(true, "dummy"))));

        MockMultiBranchProjectImpl p = rule.jenkins.createProject(MockMultiBranchProjectImpl.class, "test");
        BitbucketSCMSource source = new BitbucketSCMSource( "amuniz", "test-repos");
        p.getSourcesList().add(new BranchSource(source));
        p.scheduleBuild2(0);
        HookProcessorTestUtil.waitForLogFileMessage(rule, "Can not register hook. Jenkins root URL is not valid", log);

        setRootUrl(rule);
        ItemListener.fireOnUpdated(p);

        HookProcessorTestUtil.waitForLogFileMessage(rule, "Registering cloud hook for amuniz/test-repos", log);

    }

    private void setRootUrl(JenkinsRule rule) throws Exception {
        JenkinsLocationConfiguration.get().setUrl(rule.getURL().toString().replace("localhost", "127.0.0.1"));
    }

}
