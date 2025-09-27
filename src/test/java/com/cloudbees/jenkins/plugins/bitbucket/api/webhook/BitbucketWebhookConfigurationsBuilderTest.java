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
package com.cloudbees.jenkins.plugins.bitbucket.api.webhook;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.cloud.CloudWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.plugin.PluginWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.server.ServerWebhookConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;

@WithJenkins
class BitbucketWebhookConfigurationsBuilderTest {

    static JenkinsRule rule;

    @BeforeAll
    static void init(JenkinsRule rule) {
        BitbucketWebhookConfigurationsBuilderTest.rule = rule;
    }

    @SuppressWarnings("deprecation")
    @Test
    void test_plugin_configuration() {
        String credentialsId = "credentialsId";
        String jenkinsRootURL = "http://localhost:8090/jenkins";

        BitbucketWebhookConfigurationBuilder builder = BitbucketWebhookConfigurationsBuilder.lookup("PLUGIN");
        builder.autoManaged(credentialsId);
        builder.callbackRootURL(jenkinsRootURL);

        BitbucketWebhookConfiguration webhook = builder.build();
        assertThat(webhook).isInstanceOfSatisfying(PluginWebhookConfiguration.class, plugin -> {
            assertThat(plugin.isManageHooks()).isTrue();
            assertThat(plugin.getCredentialsId()).isEqualTo(credentialsId);
            assertThat(plugin.getEndpointJenkinsRootURL()).startsWith(jenkinsRootURL);
        });
    }

    @Test
    void test_cloud_configuration() {
        String credentialsId = "credentialsId";
        String signatureCredentialsId = "signatureId";
        String jenkinsRootURL = "http://localhost:8090/jenkins";

        NativeBitbucketWebhookConfigurationBuilder builder = BitbucketWebhookConfigurationsBuilder.lookup("CLOUD_NATIVE", NativeBitbucketWebhookConfigurationBuilder.class);
        builder.autoManaged(credentialsId);
        builder.callbackRootURL(jenkinsRootURL);
        builder.signature(signatureCredentialsId);

        BitbucketWebhookConfiguration webhook = builder.build();
        assertThat(webhook).isInstanceOfSatisfying(CloudWebhookConfiguration.class, cloud -> {
            assertThat(cloud.isManageHooks()).isTrue();
            assertThat(cloud.getCredentialsId()).isEqualTo(credentialsId);
            assertThat(cloud.getEndpointJenkinsRootURL()).startsWith(jenkinsRootURL);
            assertThat(cloud.isEnableHookSignature()).isTrue();
            assertThat(cloud.getHookSignatureCredentialsId()).isEqualTo(signatureCredentialsId);
        });
    }

    @Test
    void test_server_configuration() {
        String credentialsId = "credentialsId";
        String signatureCredentialsId = "signatureId";
        String jenkinsRootURL = "http://localhost:8090/jenkins";

        NativeBitbucketWebhookConfigurationBuilder builder = BitbucketWebhookConfigurationsBuilder.lookup("SERVER_NATIVE", NativeBitbucketWebhookConfigurationBuilder.class);
        builder.autoManaged(credentialsId);
        builder.callbackRootURL(jenkinsRootURL);
        builder.signature(signatureCredentialsId);

        BitbucketWebhookConfiguration webhook = builder.build();
        assertThat(webhook).isInstanceOfSatisfying(ServerWebhookConfiguration.class, server -> {
            assertThat(server.isManageHooks()).isTrue();
            assertThat(server.getCredentialsId()).isEqualTo(credentialsId);
            assertThat(server.getEndpointJenkinsRootURL()).startsWith(jenkinsRootURL);
            assertThat(server.isEnableHookSignature()).isTrue();
            assertThat(server.getHookSignatureCredentialsId()).isEqualTo(signatureCredentialsId);
        });
    }
}
