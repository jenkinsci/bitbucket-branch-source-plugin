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

import com.cloudbees.jenkins.plugins.bitbucket.server.BitbucketServerVersion;
import com.cloudbees.jenkins.plugins.bitbucket.server.BitbucketServerWebhookImplementation;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.google.common.io.Resources;
import hudson.XmlFile;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AuthorizationStrategy;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeThat;

public class BitbucketEndpointConfigurationTest {

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Before
    public void cleanUp() throws IOException {
        BitbucketEndpointConfiguration.get().setEndpoints(null);
        new XmlFile(new File(Jenkins.get().getRootDir(), BitbucketEndpointConfiguration.get().getId() + ".xml"))
                .delete();
        SystemCredentialsProvider.getInstance()
                .setDomainCredentialsMap(Collections.<Domain, List<Credentials>>emptyMap());
    }

    @Test
    public void given__newInstance__when__notConfigured__then__cloudPresent() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        assertThat(instance.getEndpoints(), contains(instanceOf(BitbucketCloudEndpoint.class)));
    }

    @Test
    public void given__newInstance__when__configuredWithEmpty__then__cloudPresent() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(Collections.<AbstractBitbucketEndpoint>emptyList());
        assertThat(instance.getEndpoints(), contains(instanceOf(BitbucketCloudEndpoint.class)));
    }

    @Test
    public void given__newInstance__when__configuredWithCloud__then__cloudPresent() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        assumeThat(instance.getEndpoints().get(0).getCredentialsId(), not(is("dummy")));
        instance.setEndpoints(
                Collections.<AbstractBitbucketEndpoint>singletonList(new BitbucketCloudEndpoint(true, "dummy")));
        assertThat(instance.getEndpoints(), contains(instanceOf(BitbucketCloudEndpoint.class)));
        assertThat(instance.getEndpoints().get(0).getCredentialsId(), is("dummy"));
    }

    @Test
    public void given__newInstance__when__configuredWithMultipleCloud__then__onlyFirstCloudPresent() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        assumeThat(instance.getEndpoints().get(0).getCredentialsId(), not(is("first")));
        instance.setEndpoints(Arrays.<AbstractBitbucketEndpoint>asList(
                new BitbucketCloudEndpoint(true, "first"),
                new BitbucketCloudEndpoint(true, "second"),
                new BitbucketCloudEndpoint(true, "third")));
        assertThat(instance.getEndpoints(), contains(instanceOf(BitbucketCloudEndpoint.class)));
        assertThat(instance.getEndpoints().get(0).getCredentialsId(), is("first"));
    }

    @Test
    public void given__newInstance__when__configuredAsAnon__then__permissionError() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        j.jenkins.setAuthorizationStrategy(new FullControlOnceLoggedInAuthorizationStrategy());
        try (ACLContext context = ACL.as(Jenkins.ANONYMOUS)) {
            instance.setEndpoints(Arrays.<AbstractBitbucketEndpoint>asList(
                    new BitbucketCloudEndpoint(true, "first"),
                    new BitbucketCloudEndpoint(true, "second"),
                    new BitbucketCloudEndpoint(true, "third")));
            assertThat(instance.getEndpoints(), contains(instanceOf(BitbucketCloudEndpoint.class)));
            assertThat(instance.getEndpoints().get(0).getCredentialsId(), is("first"));
        } catch (RuntimeException x) {
            assertThat(x.getMessage(), is(hudson.security.Messages.AccessDeniedException2_MissingPermission("anonymous", "Overall/Administer")));
        } finally {
            j.jenkins.setAuthorizationStrategy(AuthorizationStrategy.UNSECURED);
        }
    }

    @Test
    public void given__newInstance__when__configuredWithServerUsingCloudUrl__then__convertedToCloud() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        assumeThat(instance.getEndpoints().get(0).getCredentialsId(), not(is("dummy")));
        instance.setEndpoints(Arrays.asList(
                new BitbucketServerEndpoint("I am silly", BitbucketCloudEndpoint.SERVER_URL, true, "dummy"),
                new BitbucketCloudEndpoint(true, "second"),
                new BitbucketCloudEndpoint(true, "third")));
        assertThat(instance.getEndpoints(), contains(instanceOf(BitbucketCloudEndpoint.class)));
        assertThat(instance.getEndpoints().get(0).getCredentialsId(), is("dummy"));
    }

    @Test
    public void given__newInstance__when__configuredWithServer__then__serverPresent() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        assumeThat(instance.getEndpoints().get(0).getCredentialsId(), not(is("dummy")));
        instance.setEndpoints(
                Collections.<AbstractBitbucketEndpoint>singletonList(
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "dummy")));
        assertThat(instance.getEndpoints(), contains(instanceOf(BitbucketServerEndpoint.class)));
        assertThat(instance.getEndpoints().get(0).getDisplayName(), is("Example Inc"));
        assertThat(instance.getEndpoints().get(0).getServerUrl(), is("https://bitbucket.example.com"));
        assertThat(instance.getEndpoints().get(0).isManageHooks(), is(true));
        assertThat(instance.getEndpoints().get(0).getCredentialsId(), is("dummy"));
    }

    @Test
    public void given__newInstance__when__configuredWithTwoServers__then__serversPresent() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        assumeThat(instance.getEndpoints().get(0).getCredentialsId(), not(is("dummy")));
        instance.setEndpoints(
                Arrays.<AbstractBitbucketEndpoint>asList(
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "dummy"),
                        new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", false, null)));
        assertThat(instance.getEndpoints(),
                contains(instanceOf(BitbucketServerEndpoint.class), instanceOf(BitbucketServerEndpoint.class)));
        assertThat(instance.getEndpoints().get(0).getDisplayName(), is("Example Inc"));
        assertThat(instance.getEndpoints().get(0).getServerUrl(), is("https://bitbucket.example.com"));
        assertThat(instance.getEndpoints().get(0).isManageHooks(), is(true));
        assertThat(instance.getEndpoints().get(0).getCredentialsId(), is("dummy"));
        assertThat(instance.getEndpoints().get(1).getDisplayName(), is("Example Org"));
        assertThat(instance.getEndpoints().get(1).getServerUrl(), is("http://example.org:8080/bitbucket"));
        assertThat(instance.getEndpoints().get(1).isManageHooks(), is(false));
        assertThat(instance.getEndpoints().get(1).getCredentialsId(), is(nullValue()));
    }

    @Test
    public void given__instanceWithCloud__when__addingAnotherCloud__then__onlyFirstCloudRetained() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                Collections.<AbstractBitbucketEndpoint>singletonList(new BitbucketCloudEndpoint(true, "dummy")));
        assumeThat(instance.getEndpoints(), contains(instanceOf(BitbucketCloudEndpoint.class)));
        assumeThat(instance.getEndpoints().get(0).getCredentialsId(), is("dummy"));
        assertThat(instance.addEndpoint(new BitbucketCloudEndpoint(false, null)), is(false));
        assertThat(instance.getEndpoints(), contains(instanceOf(BitbucketCloudEndpoint.class)));
        assertThat(instance.getEndpoints().get(0).getCredentialsId(), is("dummy"));
    }

    @Test
    public void given__instanceWithServer__when__addingCloud__then__cloudAdded() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                Collections.<AbstractBitbucketEndpoint>singletonList(
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "dummy")));
        assumeThat(instance.getEndpoints(), contains(instanceOf(BitbucketServerEndpoint.class)));
        assumeThat(instance.getEndpoints().get(0).getCredentialsId(), is("dummy"));
        assertThat(instance.addEndpoint(new BitbucketCloudEndpoint(true, "added")), is(true));
        assertThat(instance.getEndpoints(),
                contains(instanceOf(BitbucketServerEndpoint.class), instanceOf(BitbucketCloudEndpoint.class)));
        assertThat(instance.getEndpoints().get(0).getCredentialsId(), is("dummy"));
        assertThat(instance.getEndpoints().get(1).getCredentialsId(), is("added"));
    }

    @Test
    public void given__instanceWithServer__when__addingDifferentServer__then__serverAdded() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                Collections.<AbstractBitbucketEndpoint>singletonList(
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "dummy")));
        assumeThat(instance.getEndpoints(), contains(instanceOf(BitbucketServerEndpoint.class)));
        assumeThat(instance.getEndpoints().get(0).getCredentialsId(), is("dummy"));
        assertThat(instance.addEndpoint(
                new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "added")),
                is(true));
        assertThat(instance.getEndpoints(), contains(instanceOf(BitbucketServerEndpoint.class), instanceOf(
                BitbucketServerEndpoint.class)));
        assertThat(instance.getEndpoints().get(0).getCredentialsId(), is("dummy"));
        assertThat(instance.getEndpoints().get(1).getCredentialsId(), is("added"));
    }

    @Test
    public void given__instanceWithServer__when__addingSameServer__then__onlyFirstServerRetained() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                Collections.<AbstractBitbucketEndpoint>singletonList(
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "dummy")));
        assumeThat(instance.getEndpoints(), contains(instanceOf(BitbucketServerEndpoint.class)));
        assumeThat(instance.getEndpoints().get(0).getCredentialsId(), is("dummy"));
        assertThat(instance.addEndpoint(
                new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", false, null)), is(false));
        assertThat(instance.getEndpoints(), contains(instanceOf(BitbucketServerEndpoint.class)));
        assertThat(instance.getEndpoints().get(0).getCredentialsId(), is("dummy"));
    }

    @Test
    public void given__instanceWithCloud__when__updatingCloud__then__cloudUpdated() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                Collections.<AbstractBitbucketEndpoint>singletonList(new BitbucketCloudEndpoint(true, "dummy")));
        assumeThat(instance.getEndpoints(), contains(instanceOf(BitbucketCloudEndpoint.class)));
        assumeThat(instance.getEndpoints().get(0).getCredentialsId(), is("dummy"));
        instance.updateEndpoint(new BitbucketCloudEndpoint(false, null));
        assertThat(instance.getEndpoints(), contains(instanceOf(BitbucketCloudEndpoint.class)));
        assertThat(instance.getEndpoints().get(0).getCredentialsId(), is(nullValue()));
    }

    @Test
    public void given__instanceWithServer__when__updatingCloud__then__cloudAdded() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                Collections.<AbstractBitbucketEndpoint>singletonList(
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "dummy")));
        assumeThat(instance.getEndpoints(), contains(instanceOf(BitbucketServerEndpoint.class)));
        assumeThat(instance.getEndpoints().get(0).getCredentialsId(), is("dummy"));
        instance.updateEndpoint(new BitbucketCloudEndpoint(true, "added"));
        assertThat(instance.getEndpoints(),
                contains(instanceOf(BitbucketServerEndpoint.class), instanceOf(BitbucketCloudEndpoint.class)));
        assertThat(instance.getEndpoints().get(0).getCredentialsId(), is("dummy"));
        assertThat(instance.getEndpoints().get(1).getCredentialsId(), is("added"));
    }

    @Test
    public void given__instanceWithServer__when__updatingDifferentServer__then__serverAdded() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                Collections.<AbstractBitbucketEndpoint>singletonList(
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "dummy")));
        assumeThat(instance.getEndpoints(), contains(instanceOf(BitbucketServerEndpoint.class)));
        assumeThat(instance.getEndpoints().get(0).getCredentialsId(), is("dummy"));
        instance.updateEndpoint(
                new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "added"));
        assertThat(instance.getEndpoints(), contains(instanceOf(BitbucketServerEndpoint.class), instanceOf(
                BitbucketServerEndpoint.class)));
        assertThat(instance.getEndpoints().get(0).getCredentialsId(), is("dummy"));
        assertThat(instance.getEndpoints().get(1).getCredentialsId(), is("added"));
    }

    @Test
    public void given__instanceWithServer__when__updatingSameServer__then__serverUpdated() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                Collections.<AbstractBitbucketEndpoint>singletonList(
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "dummy")));
        assumeThat(instance.getEndpoints(), contains(instanceOf(BitbucketServerEndpoint.class)));
        assertThat(instance.getEndpoints().get(0).getDisplayName(), is("Example Inc"));
        assertThat(instance.getEndpoints().get(0).getServerUrl(), is("https://bitbucket.example.com"));
        assertThat(instance.getEndpoints().get(0).isManageHooks(), is(true));
        assertThat(instance.getEndpoints().get(0).getCredentialsId(), is("dummy"));
        instance.updateEndpoint(
                new BitbucketServerEndpoint("Example, Inc.", "https://bitbucket.example.com/", false, null));
        assertThat(instance.getEndpoints(), contains(instanceOf(BitbucketServerEndpoint.class)));
        assertThat(instance.getEndpoints().get(0).getDisplayName(), is("Example, Inc."));
        assertThat(instance.getEndpoints().get(0).getServerUrl(), is("https://bitbucket.example.com"));
        assertThat(instance.getEndpoints().get(0).isManageHooks(), is(false));
        assertThat(instance.getEndpoints().get(0).getCredentialsId(), is(nullValue()));
    }

    @Test
    public void given__newInstance__when__removingCloud__then__defaultRestored() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        assumeThat(instance.getEndpoints(), contains(instanceOf(BitbucketCloudEndpoint.class)));
        assertThat(instance.getEndpoints().get(0).getCredentialsId(), is(nullValue()));
        assertThat(instance.removeEndpoint(new BitbucketCloudEndpoint(true, "dummy")), is(true));
        assertThat(instance.getEndpoints(), contains(instanceOf(BitbucketCloudEndpoint.class)));
    }

    @Test
    public void given__instanceWithCloudAndServers__when__removingServer__then__matchingServerRemoved() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                Arrays.asList(
                        new BitbucketCloudEndpoint(true, "first"),
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "second"),
                        new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "third")
                ));
        assumeThat(instance.getEndpoints(),
                contains(instanceOf(BitbucketCloudEndpoint.class), instanceOf(BitbucketServerEndpoint.class),
                        instanceOf(BitbucketServerEndpoint.class)));
        assumeThat(instance.getEndpoints().get(0).getCredentialsId(), is("first"));
        assumeThat(instance.getEndpoints().get(1).getCredentialsId(), is("second"));
        assumeThat(instance.getEndpoints().get(2).getCredentialsId(), is("third"));
        assertThat(instance.removeEndpoint(
                new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", false, null)), is(true));
        assertThat(instance.getEndpoints(),
                contains(instanceOf(BitbucketCloudEndpoint.class), instanceOf(BitbucketServerEndpoint.class)));
        assertThat(instance.getEndpoints().get(0).getCredentialsId(), is("first"));
        assertThat(instance.getEndpoints().get(1).getCredentialsId(), is("third"));
    }

    @Test
    public void given__instanceWithCloudAndServers__when__removingCloud__then__cloudRemoved() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                Arrays.asList(
                        new BitbucketCloudEndpoint(true, "first"),
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "second"),
                        new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "third")
                ));
        assumeThat(instance.getEndpoints(),
                contains(instanceOf(BitbucketCloudEndpoint.class), instanceOf(BitbucketServerEndpoint.class),
                        instanceOf(BitbucketServerEndpoint.class)));
        assumeThat(instance.getEndpoints().get(0).getCredentialsId(), is("first"));
        assumeThat(instance.getEndpoints().get(1).getCredentialsId(), is("second"));
        assumeThat(instance.getEndpoints().get(2).getCredentialsId(), is("third"));
        assertThat(instance.removeEndpoint(
                new BitbucketCloudEndpoint(false, null)), is(true));
        assertThat(instance.getEndpoints(),
                contains(instanceOf(BitbucketServerEndpoint.class), instanceOf(BitbucketServerEndpoint.class)));
        assertThat(instance.getEndpoints().get(0).getCredentialsId(), is("second"));
        assertThat(instance.getEndpoints().get(1).getCredentialsId(), is("third"));
    }

    @Test
    public void given__instanceWithCloudAndServers__when__removingNonExisting__then__noChange() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                Arrays.asList(
                        new BitbucketCloudEndpoint(true, "first"),
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "second"),
                        new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "third")
                ));
        assumeThat(instance.getEndpoints(),
                contains(instanceOf(BitbucketCloudEndpoint.class), instanceOf(BitbucketServerEndpoint.class),
                        instanceOf(BitbucketServerEndpoint.class)));
        assumeThat(instance.getEndpoints().get(0).getCredentialsId(), is("first"));
        assumeThat(instance.getEndpoints().get(1).getCredentialsId(), is("second"));
        assumeThat(instance.getEndpoints().get(2).getCredentialsId(), is("third"));
        assertThat(instance.removeEndpoint(
                new BitbucketServerEndpoint("Test", "http://bitbucket.test", true, "fourth")), is(false));
        assertThat(instance.getEndpoints(),
                contains(instanceOf(BitbucketCloudEndpoint.class), instanceOf(BitbucketServerEndpoint.class),
                        instanceOf(BitbucketServerEndpoint.class)));
        assertThat(instance.getEndpoints().get(0).getCredentialsId(), is("first"));
        assertThat(instance.getEndpoints().get(1).getCredentialsId(), is("second"));
        assertThat(instance.getEndpoints().get(2).getCredentialsId(), is("third"));
    }

    @Test
    public void given__instance__when__onlyOneEndpoint__then__endpointsNotSelectable() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                Collections.<AbstractBitbucketEndpoint>singletonList(
                        new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "dummy")
                ));
        assertThat(instance.isEndpointSelectable(), is(false));
    }

    @Test
    public void given__instance__when__multipleEndpoints__then__endpointsSelectable() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                Arrays.asList(
                        new BitbucketCloudEndpoint(true, "first"),
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "second"),
                        new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "third")
                ));
        assertThat(instance.isEndpointSelectable(), is(true));
    }

    @Test
    public void given__instanceWithCloudAndServers__when__findingExistingEndpoint__then__endpointFound() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                Arrays.asList(
                        new BitbucketCloudEndpoint(true, "first"),
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "second"),
                        new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "third")
                ));
        AbstractBitbucketEndpoint ep = instance.findEndpoint(BitbucketCloudEndpoint.SERVER_URL);
        assertThat(ep, notNullValue());
        assertThat(ep.getCredentialsId(), is("first"));

        ep = instance.findEndpoint("https://bitbucket.example.com/");
        assertThat(ep, notNullValue());
        assertThat(ep.getCredentialsId(), is("second"));

        ep = instance.findEndpoint("https://bitbucket.example.com");
        assertThat(ep, notNullValue());
        assertThat(ep.getCredentialsId(), is("second"));

        ep = instance.findEndpoint("https://BITBUCKET.EXAMPLE.COM:443/");
        assertThat(ep, notNullValue());
        assertThat(ep.getCredentialsId(), is("second"));

        ep = instance.findEndpoint("http://example.org:8080/bitbucket/../bitbucket/");
        assertThat(ep, notNullValue());
        assertThat(ep.getCredentialsId(), is("third"));
    }

    @Test
    public void given__instanceWithServers__when__findingNonExistingEndpoint__then__endpointNotFound() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                Arrays.<AbstractBitbucketEndpoint>asList(
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "dummy"),
                        new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "dummy")
                ));
        assertThat(instance.findEndpoint(BitbucketCloudEndpoint.SERVER_URL), nullValue());
        assertThat(instance.findEndpoint("http://bitbucket.example.com/"), nullValue());
        assertThat(instance.findEndpoint("http://bitbucket.example.com:80/"), nullValue());
        assertThat(instance.findEndpoint("http://bitbucket.example.com:443"), nullValue());
        assertThat(instance.findEndpoint("https://BITBUCKET.EXAMPLE.COM:443/bitbucket/"), nullValue());
        assertThat(instance.findEndpoint("http://example.org/bitbucket/../bitbucket/"), nullValue());
        assertThat(instance.findEndpoint("bitbucket.org"), nullValue());
        assertThat(instance.findEndpoint("bitbucket.example.com"), nullValue());
    }

    @Test
    public void given__instanceWithCloudAndServers__when__findingInvalid__then__endpointNotFound() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                Arrays.asList(
                        new BitbucketCloudEndpoint(true, "first"),
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "second"),
                        new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "third")
                ));
        assertThat(instance.findEndpoint("0schemes-start-with+digits:no leading slash"), nullValue());
        assertThat(instance.findEndpoint("http://host name with spaces:443"), nullValue());
        assertThat(instance.findEndpoint("http://invalid.port.test:65536/bitbucket/"), nullValue());
    }

    @Test
    public void given__instanceWithCloudAndServers__when__populatingDropBox__then__endpointsListed() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                Arrays.asList(
                        new BitbucketCloudEndpoint(true, "first"),
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "second"),
                        new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "third")
                ));
        ListBoxModel items = instance.getEndpointItems();
        assertThat(items, hasSize(3));
        assertThat(items.get(0).name, is(Messages.BitbucketCloudEndpoint_displayName() + " ("
                + BitbucketCloudEndpoint.SERVER_URL + ")"));
        assertThat(items.get(0).value, is(BitbucketCloudEndpoint.SERVER_URL));
        assertThat(items.get(1).name, is("Example Inc (https://bitbucket.example.com)"));
        assertThat(items.get(1).value, is("https://bitbucket.example.com"));
        assertThat(items.get(2).name, is("Example Org (http://example.org:8080/bitbucket)"));
        assertThat(items.get(2).value, is("http://example.org:8080/bitbucket"));
    }

    @Test
    public void given__instanceWithCloudAndServers__when__resolvingExistingEndpoint__then__normalizedReturned() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                Arrays.asList(
                        new BitbucketCloudEndpoint(true, "first"),
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "second"),
                        new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "third")
                ));
        assertThat(instance.getEndpointItems(), hasSize(3));
        assertThat(instance.readResolveServerUrl(null), is(BitbucketCloudEndpoint.SERVER_URL));
        assertThat(instance.getEndpointItems(), hasSize(3));
        assertThat(instance.readResolveServerUrl(""), is(BitbucketCloudEndpoint.SERVER_URL));
        assertThat(instance.getEndpointItems(), hasSize(3));
        assertThat(instance.readResolveServerUrl("https://bitbucket.EXAMPLE.COM:443/"),
                is("https://bitbucket.example.com"));
        assertThat(instance.getEndpointItems(), hasSize(3));
        assertThat(instance.readResolveServerUrl("https://bitbucket.example.com"),
                is("https://bitbucket.example.com"));
        assertThat(instance.getEndpointItems(), hasSize(3));
        assertThat(instance.readResolveServerUrl("http://example.org:8080/bitbucket/"),
                is("http://example.org:8080/bitbucket"));
        assertThat(instance.getEndpointItems(), hasSize(3));
        assertThat(instance.readResolveServerUrl("http://example.org:8080/bitbucket/foo/../"),
                is("http://example.org:8080/bitbucket"));
        assertThat(instance.getEndpointItems(), hasSize(3));
        assertThat(instance.readResolveServerUrl("http://example.org:8080/foo/../bitbucket/."),
                is("http://example.org:8080/bitbucket"));
        assertThat(instance.getEndpointItems(), hasSize(3));
    }

    @Test
    public void
    given__instanceWithCloudAndServers__when__resolvingNewEndpointAsSystem__then__addedAndNormalizedReturned() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(Collections.<AbstractBitbucketEndpoint>singletonList(
                new BitbucketServerEndpoint("existing", "https://bitbucket.test", false, null)));
        assertThat(instance.getEndpointItems(), hasSize(1));
        assertThat(instance.readResolveServerUrl(null), is(BitbucketCloudEndpoint.SERVER_URL));
        assertThat(instance.getEndpointItems(), hasSize(2));
        assertThat(instance.readResolveServerUrl(""), is(BitbucketCloudEndpoint.SERVER_URL));
        assertThat(instance.getEndpointItems(), hasSize(2));
        assertThat(instance.readResolveServerUrl("https://bitbucket.EXAMPLE.COM:443/"),
                is("https://bitbucket.example.com"));
        assertThat(instance.getEndpointItems(), hasSize(3));
        assertThat(instance.readResolveServerUrl("https://bitbucket.example.com"),
                is("https://bitbucket.example.com"));
        assertThat(instance.getEndpointItems(), hasSize(3));
        assertThat(instance.readResolveServerUrl("http://example.org:8080/bitbucket/"),
                is("http://example.org:8080/bitbucket"));
        assertThat(instance.getEndpointItems(), hasSize(4));
        assertThat(instance.readResolveServerUrl("http://example.org:8080/bitbucket/foo/../"),
                is("http://example.org:8080/bitbucket"));
        assertThat(instance.getEndpointItems(), hasSize(4));
        assertThat(instance.readResolveServerUrl("http://example.org:8080/foo/../bitbucket/."),
                is("http://example.org:8080/bitbucket"));
        assertThat(instance.getEndpointItems(), hasSize(4));
        assertThat(instance.getEndpoints().get(0).getDisplayName(), is("existing"));
        assertThat(instance.getEndpoints().get(0).getServerUrl(), is("https://bitbucket.test"));
        assertThat(instance.getEndpoints().get(0).isManageHooks(), is(false));
        assertThat(instance.getEndpoints().get(0).getCredentialsId(), is(nullValue()));
        assertThat(instance.getEndpoints().get(1).getDisplayName(), is(Messages.BitbucketCloudEndpoint_displayName()));
        assertThat(instance.getEndpoints().get(1).getServerUrl(), is("https://bitbucket.org"));
        assertThat(instance.getEndpoints().get(1).isManageHooks(), is(false));
        assertThat(instance.getEndpoints().get(1).getCredentialsId(), is(nullValue()));
        assertThat(instance.getEndpoints().get(2).getDisplayName(), is("example"));
        assertThat(instance.getEndpoints().get(2).getServerUrl(), is("https://bitbucket.example.com"));
        assertThat(instance.getEndpoints().get(2).isManageHooks(), is(false));
        assertThat(instance.getEndpoints().get(2).getCredentialsId(), is(nullValue()));
        assertThat(instance.getEndpoints().get(3).getDisplayName(), is("example"));
        assertThat(instance.getEndpoints().get(3).getServerUrl(), is("http://example.org:8080/bitbucket"));
        assertThat(instance.getEndpoints().get(3).isManageHooks(), is(false));
        assertThat(instance.getEndpoints().get(3).getCredentialsId(), is(nullValue()));
    }

    @Test
    public void
    given__instanceWithCloudAndServers__when__resolvingNewEndpointAsAnon__then__normalizedReturnedNotAdded() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(Collections.<AbstractBitbucketEndpoint>singletonList(
                new BitbucketServerEndpoint("existing", "https://bitbucket.test", false, null)));
        try (ACLContext context = ACL.as(Jenkins.ANONYMOUS)) {
            assertThat(instance.getEndpointItems(), hasSize(1));
            assertThat(instance.readResolveServerUrl(null), is(BitbucketCloudEndpoint.SERVER_URL));
            assertThat(instance.getEndpointItems(), hasSize(1));
            assertThat(instance.readResolveServerUrl(""), is(BitbucketCloudEndpoint.SERVER_URL));
            assertThat(instance.getEndpointItems(), hasSize(1));
            assertThat(instance.readResolveServerUrl("https://bitbucket.EXAMPLE.COM:443/"),
                    is("https://bitbucket.example.com"));
            assertThat(instance.getEndpointItems(), hasSize(1));
            assertThat(instance.readResolveServerUrl("https://bitbucket.example.com"),
                    is("https://bitbucket.example.com"));
            assertThat(instance.getEndpointItems(), hasSize(1));
            assertThat(instance.readResolveServerUrl("http://example.org:8080/bitbucket/"),
                    is("http://example.org:8080/bitbucket"));
            assertThat(instance.getEndpointItems(), hasSize(1));
            assertThat(instance.readResolveServerUrl("http://example.org:8080/bitbucket/foo/../"),
                    is("http://example.org:8080/bitbucket"));
            assertThat(instance.getEndpointItems(), hasSize(1));
            assertThat(instance.readResolveServerUrl("http://example.org:8080/foo/../bitbucket/."),
                    is("http://example.org:8080/bitbucket"));
            assertThat(instance.getEndpointItems(), hasSize(1));
            assertThat(instance.getEndpoints().get(0).getDisplayName(),
                    is("existing"));
            assertThat(instance.getEndpoints().get(0).getServerUrl(), is("https://bitbucket.test"));
            assertThat(instance.getEndpoints().get(0).isManageHooks(), is(false));
            assertThat(instance.getEndpoints().get(0).getCredentialsId(), is(nullValue()));
        }
    }

    @Test
    public void given__instanceWithConfig__when__configRoundtrip__then__configRetained() throws Exception {
        BitbucketEndpointConfiguration instance = BitbucketEndpointConfiguration.get();
        instance.setEndpoints(
                Arrays.asList(
                        new BitbucketCloudEndpoint(true, "first"),
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "second"),
                        new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", false, null)
                ));
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(
                Collections.singletonMap(Domain.global(), Arrays.<Credentials>asList(
                        new UsernamePasswordCredentialsImpl(
                                CredentialsScope.SYSTEM, "first", null, "user1", "pass1"),
                        new UsernamePasswordCredentialsImpl(
                                CredentialsScope.SYSTEM, "second", null, "user2", "pass2"),
                        new UsernamePasswordCredentialsImpl(
                                CredentialsScope.SYSTEM, "third", null, "user3", "pass3")
                )));

        j.configRoundtrip();

        assertThat(instance.getEndpoints(), hasSize(3));

        BitbucketCloudEndpoint endpoint1 = (BitbucketCloudEndpoint) instance.getEndpoints().get(0);
        assertThat(endpoint1.getDisplayName(), is(Messages.BitbucketCloudEndpoint_displayName()));
        assertThat(endpoint1.getServerUrl(), is("https://bitbucket.org"));
        assertThat(endpoint1.isManageHooks(), is(true));
        assertThat(endpoint1.getCredentialsId(), is("first"));

        BitbucketServerEndpoint endpoint2 = (BitbucketServerEndpoint) instance.getEndpoints().get(1);
        assertThat(endpoint2.getDisplayName(), is("Example Inc"));
        assertThat(endpoint2.getServerUrl(), is("https://bitbucket.example.com"));
        assertThat(endpoint2.isManageHooks(), is(true));
        assertThat(endpoint2.getCredentialsId(), is("second"));

        BitbucketServerEndpoint endpoint3 = (BitbucketServerEndpoint) instance.getEndpoints().get(2);
        assertThat(endpoint3.getDisplayName(), is("Example Org"));
        assertThat(endpoint3.getServerUrl(), is("http://example.org:8080/bitbucket"));
        assertThat(endpoint3.isManageHooks(), is(false));
        assertThat(endpoint3.getCredentialsId(), is(nullValue()));
    }

    @Test
    public void given__serverConfig__without__webhookImplementation__then__usePlugin() throws Exception {
        final URL configWithoutWebhookImpl = Resources.getResource(getClass(), "config-without-webhook-impl.xml");
        final File configFile = new File(Jenkins.get().getRootDir(), BitbucketEndpointConfiguration.class.getName() + ".xml");
        FileUtils.copyURLToFile(configWithoutWebhookImpl, configFile);

        final BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();

        assertThat(instance.getEndpoints(), contains(instanceOf(BitbucketServerEndpoint.class)));
        final BitbucketServerEndpoint endpoint = (BitbucketServerEndpoint) instance.getEndpoints().get(0);
        assertThat(endpoint.getWebhookImplementation(), is(BitbucketServerWebhookImplementation.PLUGIN));
    }

    @Test
    public void should_support_configuration_as_code() throws Exception {
        ConfigurationAsCode.get().configure(getClass().getResource(getClass().getSimpleName() + "/configuration-as-code.yml").toString());

        BitbucketEndpointConfiguration instance = BitbucketEndpointConfiguration.get();

        assertThat(instance.getEndpoints(), hasSize(11));

        BitbucketCloudEndpoint endpoint1 = (BitbucketCloudEndpoint) instance.getEndpoints().get(0);
        assertThat(endpoint1.getDisplayName(), is(Messages.BitbucketCloudEndpoint_displayName()));
        assertThat(endpoint1.getServerUrl(), is("https://bitbucket.org"));
        assertThat(endpoint1.isManageHooks(), is(true));
        assertThat(endpoint1.getCredentialsId(), is("first"));
        assertThat(endpoint1.isEnableCache(), is(true));
        assertThat(endpoint1.getTeamCacheDuration(), is(1));
        assertThat(endpoint1.getRepositoriesCacheDuration(), is(2));

        BitbucketServerEndpoint serverEndpoint;
        serverEndpoint = (BitbucketServerEndpoint) instance.getEndpoints().get(1);
        assertThat(serverEndpoint.getDisplayName(), is("Example Inc"));
        assertThat(serverEndpoint.getServerUrl(), is("https://bitbucket.example.com"));
        assertThat(serverEndpoint.isManageHooks(), is(true));
        assertThat(serverEndpoint.getCredentialsId(), is("second"));
        assertThat(serverEndpoint.isCallCanMerge(), is(false));
        assertThat(serverEndpoint.isCallChanges(), is(true));
        assertThat(serverEndpoint.getWebhookImplementation(), is(BitbucketServerWebhookImplementation.PLUGIN));
        assertThat(serverEndpoint.getServerVersion(), is(BitbucketServerVersion.VERSION_7));

        serverEndpoint = (BitbucketServerEndpoint) instance.getEndpoints().get(2);
        assertThat(serverEndpoint.getDisplayName(), is("Example Org"));
        assertThat(serverEndpoint.getServerUrl(), is("http://example.org:8080/bitbucket"));
        assertThat(serverEndpoint.isManageHooks(), is(false));
        assertThat(serverEndpoint.getCredentialsId(), is(nullValue()));
        assertThat(serverEndpoint.isCallCanMerge(), is(true));
        assertThat(serverEndpoint.isCallChanges(), is(true));
        assertThat(serverEndpoint.getWebhookImplementation(), is(BitbucketServerWebhookImplementation.PLUGIN));
        assertThat(serverEndpoint.getServerVersion(), is(BitbucketServerVersion.VERSION_7));

        serverEndpoint = (BitbucketServerEndpoint) instance.getEndpoints().get(3);
        assertThat(serverEndpoint.getDisplayName(), is("Example Inc"));
        assertThat(serverEndpoint.getServerUrl(), is("http://bitbucket.example.com:8083"));
        assertThat(serverEndpoint.isManageHooks(), is(true));
        assertThat(serverEndpoint.getCredentialsId(), is("third"));
        assertThat(serverEndpoint.isCallCanMerge(), is(true));
        assertThat(serverEndpoint.isCallChanges(), is(true));
        assertThat(serverEndpoint.getWebhookImplementation(), is(BitbucketServerWebhookImplementation.NATIVE));
        assertThat(serverEndpoint.getServerVersion(), is(BitbucketServerVersion.VERSION_7));

        serverEndpoint = (BitbucketServerEndpoint) instance.getEndpoints().get(4);
        assertThat(serverEndpoint.getDisplayName(), is("Example Inc"));
        assertThat(serverEndpoint.getServerUrl(), is("http://bitbucket.example.com:8084"));
        assertThat(serverEndpoint.isManageHooks(), is(true));
        assertThat(serverEndpoint.getCredentialsId(), is("fourth"));
        assertThat(serverEndpoint.isCallCanMerge(), is(false));
        assertThat(serverEndpoint.isCallChanges(), is(false));
        assertThat(serverEndpoint.getWebhookImplementation(), is(BitbucketServerWebhookImplementation.PLUGIN));
        assertThat(serverEndpoint.getServerVersion(), is(BitbucketServerVersion.VERSION_7));

        serverEndpoint = (BitbucketServerEndpoint) instance.getEndpoints().get(5);
        assertThat(serverEndpoint.getDisplayName(), is("Example Inc"));
        assertThat(serverEndpoint.getServerUrl(), is("http://bitbucket.example.com:8085"));
        assertThat(serverEndpoint.isManageHooks(), is(true));
        assertThat(serverEndpoint.getCredentialsId(), is("fifth"));
        assertThat(serverEndpoint.isCallCanMerge(), is(false));
        assertThat(serverEndpoint.isCallChanges(), is(true));
        assertThat(serverEndpoint.getWebhookImplementation(), is(BitbucketServerWebhookImplementation.PLUGIN));
        assertThat(serverEndpoint.getServerVersion(), is(BitbucketServerVersion.VERSION_7));

        serverEndpoint = (BitbucketServerEndpoint) instance.getEndpoints().get(6);
        assertThat(serverEndpoint.getDisplayName(), is("Example Inc"));
        assertThat(serverEndpoint.getServerUrl(), is("http://bitbucket.example.com:8086"));
        assertThat(serverEndpoint.isManageHooks(), is(false));
        assertThat(serverEndpoint.getCredentialsId(), is(nullValue()));
        assertThat(serverEndpoint.isCallCanMerge(), is(false));
        assertThat(serverEndpoint.isCallChanges(), is(false));
        assertThat(serverEndpoint.getWebhookImplementation(), is(BitbucketServerWebhookImplementation.PLUGIN));
        assertThat(serverEndpoint.getServerVersion(), is(BitbucketServerVersion.VERSION_7));

        serverEndpoint = (BitbucketServerEndpoint) instance.getEndpoints().get(7);
        assertThat(serverEndpoint.getDisplayName(), is("Example Inc"));
        assertThat(serverEndpoint.getServerUrl(), is("http://bitbucket.example.com:8087"));
        assertThat(serverEndpoint.isManageHooks(), is(false));
        assertThat(serverEndpoint.getCredentialsId(), is(nullValue()));
        assertThat(serverEndpoint.isCallCanMerge(), is(false));
        assertThat(serverEndpoint.isCallChanges(), is(false));
        assertThat(serverEndpoint.getWebhookImplementation(), is(BitbucketServerWebhookImplementation.PLUGIN));
        assertThat(serverEndpoint.getServerVersion(), is(BitbucketServerVersion.VERSION_7));

        serverEndpoint = (BitbucketServerEndpoint) instance.getEndpoints().get(8);
        assertThat(serverEndpoint.getDisplayName(), is("Example Inc"));
        assertThat(serverEndpoint.getServerUrl(), is("http://bitbucket.example.com:8088"));
        assertThat(serverEndpoint.isManageHooks(), is(false));
        assertThat(serverEndpoint.getCredentialsId(), is(nullValue()));
        assertThat(serverEndpoint.isCallCanMerge(), is(false));
        assertThat(serverEndpoint.isCallChanges(), is(true));
        assertThat(serverEndpoint.getWebhookImplementation(), is(BitbucketServerWebhookImplementation.PLUGIN));
        assertThat(serverEndpoint.getServerVersion(), is(BitbucketServerVersion.VERSION_6));

        serverEndpoint = (BitbucketServerEndpoint) instance.getEndpoints().get(9);
        assertThat(serverEndpoint.getDisplayName(), is("Example Inc"));
        assertThat(serverEndpoint.getServerUrl(), is("http://bitbucket.example.com:8089"));
        assertThat(serverEndpoint.isManageHooks(), is(false));
        assertThat(serverEndpoint.getCredentialsId(), is(nullValue()));
        assertThat(serverEndpoint.isCallCanMerge(), is(false));
        assertThat(serverEndpoint.isCallChanges(), is(true));
        assertThat(serverEndpoint.getWebhookImplementation(), is(BitbucketServerWebhookImplementation.PLUGIN));
        assertThat(serverEndpoint.getServerVersion(), is(BitbucketServerVersion.VERSION_5_10));

        serverEndpoint = (BitbucketServerEndpoint) instance.getEndpoints().get(10);
        assertThat(serverEndpoint.getDisplayName(), is("Example Inc"));
        assertThat(serverEndpoint.getServerUrl(), is("http://bitbucket.example.com:8090"));
        assertThat(serverEndpoint.isManageHooks(), is(false));
        assertThat(serverEndpoint.getCredentialsId(), is(nullValue()));
        assertThat(serverEndpoint.isCallCanMerge(), is(false));
        assertThat(serverEndpoint.isCallChanges(), is(true));
        assertThat(serverEndpoint.getWebhookImplementation(), is(BitbucketServerWebhookImplementation.PLUGIN));
        assertThat(serverEndpoint.getServerVersion(), is(BitbucketServerVersion.VERSION_5));

    }
}
