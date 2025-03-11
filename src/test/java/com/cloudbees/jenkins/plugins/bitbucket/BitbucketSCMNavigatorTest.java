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

import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import java.util.Arrays;
import java.util.Collections;
import jenkins.model.Jenkins;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class BitbucketSCMNavigatorTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
    @Rule
    public TestName currentTestName = new TestName();

    private BitbucketSCMNavigator load() {
        return load(currentTestName.getMethodName());
    }

    private BitbucketSCMNavigator load(String dataSet) {
        return (BitbucketSCMNavigator) Jenkins.XSTREAM2.fromXML(
                getClass().getResource(getClass().getSimpleName() + "/" + dataSet + ".xml"));
    }

    @Test
    public void modern() throws Exception {
        BitbucketSCMNavigator instance = load();
        assertThat(instance.id(), is("https://bitbucket.org::cloudbeers"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getServerUrl(), is(BitbucketCloudEndpoint.SERVER_URL));
        assertThat(instance.getCredentialsId(), is("bcaef157-f105-407f-b150-df7722eab6c1"));
        assertThat(instance.getTraits(), is(Collections.emptyList()));
    }

    @Test
    public void given__instance__when__setTraits_empty__then__traitsEmpty() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Collections.emptyList());
        assertThat(instance.getTraits(), is(Collections.emptyList()));
    }

    @Test
    public void given__instance__when__setTraits__then__traitsSet() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(new BranchDiscoveryTrait(1),
                new WebhookRegistrationTrait(WebhookRegistration.DISABLE)));
        assertThat(instance.getTraits(),
                containsInAnyOrder(
                        allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(false))
                        ),
                        allOf(
                                instanceOf(WebhookRegistrationTrait.class),
                                hasProperty("mode", is(WebhookRegistration.DISABLE))
                        )
                )
        );
    }

    @Test
    public void given__instance__when__setServerUrl__then__urlNormalized() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setServerUrl("https://bitbucket.org:443/foo/../bar/../");
        assertThat(instance.getServerUrl(), is("https://bitbucket.org"));
    }

    @Test
    public void given__instance__when__setCredentials_empty__then__credentials_null() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setCredentialsId("");
        assertThat(instance.getCredentialsId(), is(nullValue()));
    }

    @Test
    public void given__instance__when__setCredentials_null__then__credentials_null() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setCredentialsId("");
        assertThat(instance.getCredentialsId(), is(nullValue()));
    }

    @Test
    public void given__instance__when__setCredentials__then__credentials_set() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setCredentialsId("test");
        assertThat(instance.getCredentialsId(), is("test"));
    }

}
