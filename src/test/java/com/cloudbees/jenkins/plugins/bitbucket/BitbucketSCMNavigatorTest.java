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

import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.trait.BranchDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.WebhookRegistrationTrait;
import java.util.Arrays;
import java.util.Collections;
import jenkins.model.Jenkins;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;

@WithJenkins
class BitbucketSCMNavigatorTest {

    @SuppressWarnings("unused")
    private static JenkinsRule rule;

    @BeforeAll
    static void init(JenkinsRule rule) {
        BitbucketSCMNavigatorTest.rule = rule;
    }

    private String currentTestName;

    @BeforeEach
    void setup(TestInfo testInfo) {
        currentTestName = testInfo.getTestMethod().get().getName();
    }

    private BitbucketSCMNavigator load() {
        return load(currentTestName);
    }

    private BitbucketSCMNavigator load(String dataSet) {
        return (BitbucketSCMNavigator) Jenkins.XSTREAM2.fromXML(
                getClass().getResource(getClass().getSimpleName() + "/" + dataSet + ".xml"));
    }

    @Test
    void modern() throws Exception {
        BitbucketSCMNavigator instance = load();
        assertThat(instance.id()).isEqualTo("https://bitbucket.org::cloudbeers");
        assertThat(instance.getRepoOwner()).isEqualTo("cloudbeers");
        assertThat(instance.getServerUrl()).isEqualTo(BitbucketCloudEndpoint.SERVER_URL);
        assertThat(instance.getCredentialsId()).isEqualTo("bcaef157-f105-407f-b150-df7722eab6c1");
        assertThat(instance.getTraits()).isEmpty();
    }

    @Test
    void given__instance__when__setTraits_empty__then__traitsEmpty() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Collections.emptyList());
        assertThat(instance.getTraits()).isEmpty();
    }

    @Test
    void given__instance__when__setTraits__then__traitsSet() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(new BranchDiscoveryTrait(1),
                new WebhookRegistrationTrait(WebhookRegistration.DISABLE)));

        assertThat(instance.getTraits())
            .anySatisfy(el -> {
                assertThat(el).isInstanceOf(BranchDiscoveryTrait.class)
                .asInstanceOf(InstanceOfAssertFactories.type(BranchDiscoveryTrait.class))
                .satisfies(trait -> {
                    assertThat(trait.isBuildBranch()).isTrue();
                    assertThat(trait.isBuildBranchesWithPR()).isFalse();
                });
            })
            .anySatisfy(el -> {
                assertThat(el).isInstanceOf(WebhookRegistrationTrait.class)
                .asInstanceOf(InstanceOfAssertFactories.type(WebhookRegistrationTrait.class))
                .satisfies(trait -> {
                    assertThat(trait.getMode()).isEqualTo(WebhookRegistration.DISABLE);
                });
            });
    }

    @Test
    void given__instance__when__setServerUrl__then__urlNormalized() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setServerUrl("https://bitbucket.org:443/foo/../bar/../");
        assertThat(instance.getServerUrl()).isEqualTo("https://bitbucket.org");
    }

    @Test
    void given__instance__when__setCredentials_empty__then__credentials_null() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setCredentialsId("");
        assertThat(instance.getCredentialsId()).isNull();
    }

    @Test
    void given__instance__when__setCredentials_null__then__credentials_null() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setCredentialsId("");
        assertThat(instance.getCredentialsId()).isNull();
    }

    @Test
    void given__instance__when__setCredentials__then__credentials_set() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setCredentialsId("test");
        assertThat(instance.getCredentialsId()).isEqualTo("test");
    }

}
