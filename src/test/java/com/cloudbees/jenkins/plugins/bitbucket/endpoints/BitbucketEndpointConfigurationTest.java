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

import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpointProvider;
import com.cloudbees.jenkins.plugins.bitbucket.impl.BitbucketPlugin;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketServerEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.Messages;
import com.cloudbees.jenkins.plugins.bitbucket.server.BitbucketServerVersion;
import com.cloudbees.jenkins.plugins.bitbucket.server.BitbucketServerWebhookImplementation;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.google.common.io.Resources;
import hudson.XmlFile;
import hudson.model.User;
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
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SuppressWarnings("deprecation")
@WithJenkins
class BitbucketEndpointConfigurationTest {

    private static JenkinsRule r;

    @BeforeAll
    static void init(JenkinsRule rule) {
        r = rule;
        BitbucketPlugin.aliases();
    }

    @AfterEach
    void cleanUp() throws IOException {
        BitbucketEndpointConfiguration.get().setEndpoints(null);
        new XmlFile(new File(Jenkins.get().getRootDir(), BitbucketEndpointConfiguration.get().getId() + ".xml"))
                .delete();
        SystemCredentialsProvider.getInstance()
                .setDomainCredentialsMap(Collections.emptyMap());
    }

    @Test
    void given__newInstance__when__notConfigured__then__cloudPresent() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        assertThat(instance.getEndpoints()).hasOnlyElementsOfType(BitbucketCloudEndpoint.class);
    }

    @Test
    void given__newInstance__when__configuredWithEmpty__then__cloudPresent() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(Collections.emptyList());
        assertThat(instance.getEndpoints()).hasOnlyElementsOfType(BitbucketCloudEndpoint.class);
    }

    @Test
    void given__newInstance__when__configuredWithCloud__then__cloudPresent() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        assumeFalse("dummy".equals(instance.getEndpoints().get(0).getCredentialsId()));
        instance.setEndpoints(List.of(buildEndpoint(true, "dummy")));
        assertThat(instance.getEndpoints()).hasOnlyElementsOfType(BitbucketCloudEndpoint.class);
        assertThat(instance.getEndpoints()).element(0).satisfies(endpoint -> assertThat(endpoint.getCredentialsId()).isEqualTo("dummy"));
    }

    @Test
    void given__newInstance__when__configuredWithMultipleCloud__then__onlyFirstCloudPresent() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        assumeFalse("first".equals(instance.getEndpoints().get(0).getCredentialsId()));
        instance.setEndpoints(List.of(buildEndpoint(true, "first"), buildEndpoint(true, "second"), buildEndpoint(true, "third")));
        assertThat(instance.getEndpoints()).hasOnlyElementsOfType(BitbucketCloudEndpoint.class);
        assertThat(instance.getEndpoints()).element(0).satisfies(endpoint -> assertThat(endpoint.getCredentialsId()).isEqualTo("first"));
    }

    @Test
    void given__newInstance__when__configuredAsAnon__then__permissionError() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        r.jenkins.setAuthorizationStrategy(new FullControlOnceLoggedInAuthorizationStrategy());
        try (ACLContext context = ACL.as2(Jenkins.ANONYMOUS2)) {
            assertThatThrownBy(() ->
                    instance.setEndpoints(List.of(buildEndpoint(true, "first"), buildEndpoint(true, "second"), buildEndpoint(true, "third")))
                )
                .hasMessage(hudson.security.Messages.AccessDeniedException2_MissingPermission("anonymous", "Overall/Administer"));
        } finally {
            r.jenkins.setAuthorizationStrategy(AuthorizationStrategy.UNSECURED);
        }
    }

    @Test
    void given__newInstance__when__configuredAsManage__then__OK() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        MockAuthorizationStrategy mockStrategy = new MockAuthorizationStrategy();
        mockStrategy.grant(Jenkins.MANAGE).onRoot().to("admin");
        r.jenkins.setAuthorizationStrategy(mockStrategy);
        try (ACLContext context = ACL.as(User.get("admin"))) {
            instance.setEndpoints(List.of(buildEndpoint(true, "first"), buildEndpoint(true, "second"), buildEndpoint(true, "third")));
            assertThat(instance.getEndpoints()).hasOnlyElementsOfType(BitbucketCloudEndpoint.class);
            assertThat(instance.getEndpoints()).element(0).satisfies(endpoint -> assertThat(endpoint.getCredentialsId()).isEqualTo("first"));
        } finally {
            r.jenkins.setAuthorizationStrategy(AuthorizationStrategy.UNSECURED);
        }
    }

    @Test
    void given__newInstance__when__configuredWithServerUsingCloudUrl__then__convertedToCloud() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        assumeFalse("dummy".equals(instance.getEndpoints().get(0).getCredentialsId()));
        instance.setEndpoints(List.of(
                new BitbucketServerEndpoint("I am silly", BitbucketCloudEndpoint.SERVER_URL, true, "dummy"),
                buildEndpoint(true, "second"),
                buildEndpoint(true, "third")));
        assertThat(instance.getEndpoints()).hasOnlyElementsOfType(BitbucketCloudEndpoint.class);
        assertThat(instance.getEndpoints()).element(0).satisfies(endpoint -> assertThat(endpoint.getCredentialsId()).isEqualTo("dummy"));
    }

    @Test
    void given__newInstance__when__configuredWithServer__then__serverPresent() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        assumeFalse("dummy".equals(instance.getEndpoints().get(0).getCredentialsId()));
        instance.setEndpoints(List.of(new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "dummy")));

        assertThat(instance.getEndpoints()).hasOnlyElementsOfType(BitbucketServerEndpoint.class);
        assertThat(instance.getEndpoints()).element(0).satisfies(endpoint -> {
            assertThat(endpoint.getDisplayName()).isEqualTo("Example Inc");
            assertThat(endpoint.getServerURL()).isEqualTo("https://bitbucket.example.com");
            assertThat(endpoint.isManageHooks()).isTrue();
            assertThat(endpoint.getCredentialsId()).isEqualTo("dummy");
        });
    }

    @Test
    void given__newInstance__when__configuredWithTwoServers__then__serversPresent() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        assumeFalse("dummy".equals(instance.getEndpoints().get(0).getCredentialsId()));
        instance.setEndpoints(List.of(
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "dummy"),
                        new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", false, null)));

        assertThat(instance.getEndpoints()).hasOnlyElementsOfType(BitbucketServerEndpoint.class);
        assertThat(instance.getEndpoints()).element(0).satisfies(endpoint -> {
            assertThat(endpoint.getDisplayName()).isEqualTo("Example Inc");
            assertThat(endpoint.getServerURL()).isEqualTo("https://bitbucket.example.com");
            assertThat(endpoint.isManageHooks()).isTrue();
            assertThat(endpoint.getCredentialsId()).isEqualTo("dummy");
        });
        assertThat(instance.getEndpoints()).element(1).satisfies(endpoint -> {
            assertThat(endpoint.getDisplayName()).isEqualTo("Example Org");
            assertThat(endpoint.getServerURL()).isEqualTo("http://example.org:8080/bitbucket");
            assertThat(endpoint.isManageHooks()).isFalse();
            assertThat(endpoint.getCredentialsId()).isNull();
        });
    }

    @Test
    void given__instanceWithCloud__when__addingAnotherCloud__then__onlyFirstCloudRetained() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(List.of(buildEndpoint(true, "dummy")));
        assumeTrue("dummy".equals(instance.getEndpoints().get(0).getCredentialsId()));

        assertThat(instance.addEndpoint(new BitbucketCloudEndpoint())).isFalse();
        assertThat(instance.getEndpoints()).hasOnlyElementsOfType(BitbucketCloudEndpoint.class);
        assertThat(instance.getEndpoints()).element(0).satisfies(endpoint -> assertThat(endpoint.getCredentialsId()).isEqualTo("dummy"));
    }

    @Test
    void given__instanceWithServer__when__addingCloud__then__cloudAdded() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(List.of(new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "dummy")));
        assumeTrue("dummy".equals(instance.getEndpoints().get(0).getCredentialsId()));

        assertThat(instance.addEndpoint(buildEndpoint(true, "added"))).isTrue();
        assertThat(instance.getEndpoints()).hasExactlyElementsOfTypes(BitbucketServerEndpoint.class, BitbucketCloudEndpoint.class);
        assertThat(instance.getEndpoints()).element(0).satisfies(endpoint -> assertThat(endpoint.getCredentialsId()).isEqualTo("dummy"));

        assertThat(instance.getEndpoints()).element(0).satisfies(endpoint -> assertThat(endpoint.getCredentialsId()).isEqualTo("dummy"));
        assertThat(instance.getEndpoints()).element(1).satisfies(endpoint -> assertThat(endpoint.getCredentialsId()).isEqualTo("added"));
    }

    @Test
    void given__instanceWithServer__when__addingDifferentServer__then__serverAdded() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(List.of(new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "dummy")));
        assumeTrue("dummy".equals(instance.getEndpoints().get(0).getCredentialsId()));

        assertThat(instance.addEndpoint(new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "added"))).isTrue();
        assertThat(instance.getEndpoints()).hasOnlyElementsOfType(BitbucketServerEndpoint.class);
        assertThat(instance.getEndpoints()).element(0).satisfies(endpoint -> assertThat(endpoint.getCredentialsId()).isEqualTo("dummy"));
        assertThat(instance.getEndpoints()).element(1).satisfies(endpoint -> assertThat(endpoint.getCredentialsId()).isEqualTo("added"));
    }

    @Test
    void given__instanceWithServer__when__addingSameServer__then__onlyFirstServerRetained() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(List.of(new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "dummy")));
        assumeTrue("dummy".equals(instance.getEndpoints().get(0).getCredentialsId()));

        assertThat(instance.addEndpoint(new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", false, null))).isFalse();
        assertThat(instance.getEndpoints()).hasOnlyElementsOfType(BitbucketServerEndpoint.class);
        assertThat(instance.getEndpoints()).element(0).satisfies(endpoint -> assertThat(endpoint.getCredentialsId()).isEqualTo("dummy"));
    }

    @Test
    void given__instanceWithCloud__when__updatingCloud__then__cloudUpdated() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(List.of(buildEndpoint(true, "dummy")));
        assumeTrue("dummy".equals(instance.getEndpoints().get(0).getCredentialsId()));

        instance.updateEndpoint(new BitbucketCloudEndpoint());

        assertThat(instance.getEndpoints()).hasOnlyElementsOfType(BitbucketCloudEndpoint.class);
        assertThat(instance.getEndpoints()).element(0).satisfies(endpoint -> assertThat(endpoint.getCredentialsId()).isNull());
    }

    @Test
    void given__instanceWithServer__when__updatingCloud__then__cloudAdded() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(List.of(new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "dummy")));
        assumeTrue("dummy".equals(instance.getEndpoints().get(0).getCredentialsId()));
        instance.updateEndpoint(buildEndpoint(true, "added"));

        assertThat(instance.getEndpoints()).hasExactlyElementsOfTypes(BitbucketServerEndpoint.class, BitbucketCloudEndpoint.class);
        assertThat(instance.getEndpoints()).element(0).satisfies(endpoint -> assertThat(endpoint.getCredentialsId()).isEqualTo("dummy"));
        assertThat(instance.getEndpoints()).element(1).satisfies(endpoint -> assertThat(endpoint.getCredentialsId()).isEqualTo("added"));
    }

    @Test
    void given__instanceWithServer__when__updatingDifferentServer__then__serverAdded() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(List.of(new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "dummy", false, null)));
        assumeTrue("dummy".equals(instance.getEndpoints().get(0).getCredentialsId()));

        instance.updateEndpoint(new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "added", false, null));

        assertThat(instance.getEndpoints()).hasOnlyElementsOfType(BitbucketServerEndpoint.class);
        assertThat(instance.getEndpoints()).element(0).satisfies(endpoint -> assertThat(endpoint.getCredentialsId()).isEqualTo("dummy"));
        assertThat(instance.getEndpoints()).element(1).satisfies(endpoint -> assertThat(endpoint.getCredentialsId()).isEqualTo("added"));
    }

    @Test
    void given__instanceWithServer__when__updatingSameServer__then__serverUpdated() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(List.of(new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "dummy")));

        instance.updateEndpoint(new BitbucketServerEndpoint("Example, Inc.", "https://bitbucket.example.com/", false, null));

        assertThat(instance.getEndpoints()).hasOnlyElementsOfType(BitbucketServerEndpoint.class);
        assertThat(instance.getEndpoints()).element(0).satisfies(endpoint -> {
            assertThat(endpoint.getDisplayName()).isEqualTo("Example, Inc.");
            assertThat(endpoint.getServerURL()).isEqualTo("https://bitbucket.example.com");
            assertThat(endpoint.isManageHooks()).isFalse();
            assertThat(endpoint.getCredentialsId()).isNull();
        });
    }

    @Test
    void given__newInstance__when__removingCloud__then__defaultRestored() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        // verify there is always a default endpoint
        assertThat(instance.getEndpoints()).isNotEmpty()
            .element(0)
            .satisfies(endpoint -> assertThat(endpoint.getCredentialsId()).isNull());
        // remove default does not really remove it
        assertThat(instance.removeEndpoint(buildEndpoint(true, "dummy"))).isFalse();
        // default always exists
        assertThat(instance.getEndpoints()).hasOnlyElementsOfType(BitbucketCloudEndpoint.class);
    }

    @Test
    void given__instanceWithCloudAndServers__when__removingServer__then__matchingServerRemoved() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                List.of(
                        buildEndpoint(true, "first"),
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "second"),
                        new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "third")
                ));
        assumeTrue("first".equals(instance.getEndpoints().get(0).getCredentialsId()));
        assumeTrue("second".equals(instance.getEndpoints().get(1).getCredentialsId()));
        assumeTrue("third".equals(instance.getEndpoints().get(2).getCredentialsId()));

        assertThat(instance.removeEndpoint(new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", false, null))).isTrue();
        assertThat(instance.getEndpoints()).hasExactlyElementsOfTypes(BitbucketCloudEndpoint.class, BitbucketServerEndpoint.class);
        assertThat(instance.getEndpoints().get(0).getCredentialsId()).isEqualTo("first");
        assertThat(instance.getEndpoints().get(1).getCredentialsId()).isEqualTo("third");
    }

    @Test
    void given__instanceWithCloudAndServers__when__removingCloud__then__cloudRemoved() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                List.of(
                        buildEndpoint(true, "first"),
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "second"),
                        new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "third")
                ));
        assumeTrue("first".equals(instance.getEndpoints().get(0).getCredentialsId()));
        assumeTrue("second".equals(instance.getEndpoints().get(1).getCredentialsId()));
        assumeTrue("third".equals(instance.getEndpoints().get(2).getCredentialsId()));

        assertThat(instance.removeEndpoint(new BitbucketCloudEndpoint())).isTrue();
        assertThat(instance.getEndpoints()).hasOnlyElementsOfType(BitbucketServerEndpoint.class);
        assertThat(instance.getEndpoints().get(0).getCredentialsId()).isEqualTo("second");
        assertThat(instance.getEndpoints().get(1).getCredentialsId()).isEqualTo("third");
    }

    @Test
    void given__instanceWithCloudAndServers__when__removingNonExisting__then__noChange() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                List.of(
                        buildEndpoint(true, "first"),
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "second"),
                        new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "third")
                ));
        assumeTrue("first".equals(instance.getEndpoints().get(0).getCredentialsId()));
        assumeTrue("second".equals(instance.getEndpoints().get(1).getCredentialsId()));
        assumeTrue("third".equals(instance.getEndpoints().get(2).getCredentialsId()));

        assertThat(instance.removeEndpoint(new BitbucketServerEndpoint("Test", "http://bitbucket.test", true, "fourth"))).isFalse();
        assertThat(instance.getEndpoints()).hasExactlyElementsOfTypes(BitbucketCloudEndpoint.class, BitbucketServerEndpoint.class, BitbucketServerEndpoint.class);
        assertThat(instance.getEndpoints().get(0).getCredentialsId()).isEqualTo("first");
        assertThat(instance.getEndpoints().get(1).getCredentialsId()).isEqualTo("second");
        assertThat(instance.getEndpoints().get(2).getCredentialsId()).isEqualTo("third");
    }

    @Test
    void given__instance__when__onlyOneEndpoint__then__endpointsNotSelectable() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(List.of(new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "dummy", false, null)));
        assertThat(instance.isEndpointSelectable()).isFalse();
    }

    @Test
    void given__instance__when__multipleEndpoints__then__endpointsSelectable() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                List.of(
                        buildEndpoint(true, "first"),
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "second"),
                        new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "third")
                ));
        assertThat(instance.isEndpointSelectable()).isTrue();
    }

    @Test
    void given__instanceWithCloudAndServers__when__findingExistingEndpoint__then__endpointFound() {
        BitbucketEndpointConfiguration.get().setEndpoints(
                List.of(
                        buildEndpoint(true, "first"),
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "second"),
                        new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "third")
                ));
        assertThat(BitbucketEndpointProvider.lookupEndpoint(BitbucketCloudEndpoint.SERVER_URL)).isPresent()
            .hasValueSatisfying(endpoint -> assertThat(endpoint.getCredentialsId()).isEqualTo("first"));

        assertThat(BitbucketEndpointProvider.lookupEndpoint("https://bitbucket.example.com/")).isPresent()
            .hasValueSatisfying(endpoint -> assertThat(endpoint.getCredentialsId()).isEqualTo("second"));

        assertThat(BitbucketEndpointProvider.lookupEndpoint("https://BITBUCKET.EXAMPLE.COM:443/")).isPresent()
            .hasValueSatisfying(endpoint -> assertThat(endpoint.getCredentialsId()).isEqualTo("second"));

        assertThat(BitbucketEndpointProvider.lookupEndpoint("http://example.org:8080/bitbucket/../bitbucket/")).isPresent()
            .hasValueSatisfying(endpoint -> assertThat(endpoint.getCredentialsId()).isEqualTo("third"));
    }

    @Test
    void given__instanceWithServers__when__findingNonExistingEndpoint__then__endpointNotFound() {
        BitbucketEndpointConfiguration.get().setEndpoints(
                List.of(
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "dummy"),
                        new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "dummy")
                ));
        assertThat(BitbucketEndpointProvider.lookupEndpoint(BitbucketCloudEndpoint.SERVER_URL)).isEmpty();
        assertThat(BitbucketEndpointProvider.lookupEndpoint("http://bitbucket.example.com/")).isEmpty();
        assertThat(BitbucketEndpointProvider.lookupEndpoint("http://bitbucket.example.com:80/")).isEmpty();
        assertThat(BitbucketEndpointProvider.lookupEndpoint("http://bitbucket.example.com:443")).isEmpty();
        assertThat(BitbucketEndpointProvider.lookupEndpoint("https://BITBUCKET.EXAMPLE.COM:443/bitbucket/")).isEmpty();
        assertThat(BitbucketEndpointProvider.lookupEndpoint("http://example.org/bitbucket/../bitbucket/")).isEmpty();
        assertThat(BitbucketEndpointProvider.lookupEndpoint("bitbucket.org")).isEmpty();
        assertThat(BitbucketEndpointProvider.lookupEndpoint("bitbucket.example.com")).isEmpty();
    }

    @Test
    void given__instanceWithCloudAndServers__when__findingInvalid__then__endpointNotFound() {
        BitbucketEndpointConfiguration.get().setEndpoints(
                List.of(
                        buildEndpoint(true, "first"),
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "second"),
                        new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "third")
                ));
        assertThat(BitbucketEndpointProvider.lookupEndpoint("0schemes-start-with+digits:no leading slash")).isEmpty();
        assertThat(BitbucketEndpointProvider.lookupEndpoint("http://host name with spaces:443")).isEmpty();
        assertThat(BitbucketEndpointProvider.lookupEndpoint("http://invalid.port.test:65536/bitbucket/")).isEmpty();
    }

    @Test
    void given__instanceWithCloudAndServers__when__populatingDropBox__then__endpointsListed() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                List.of(
                        buildEndpoint(true, "first"),
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "second"),
                        new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "third")
                ));
        ListBoxModel items = instance.getEndpointItems();
        assertThat(items).hasSize(3);
        assertThat(items.get(0).name).isEqualTo(Messages.BitbucketCloudEndpoint_displayName() + " (" + BitbucketCloudEndpoint.SERVER_URL + ")");
        assertThat(items.get(0).value).isEqualTo(BitbucketCloudEndpoint.SERVER_URL);
        assertThat(items.get(1).name).isEqualTo("Example Inc (https://bitbucket.example.com)");
        assertThat(items.get(1).value).isEqualTo("https://bitbucket.example.com");
        assertThat(items.get(2).name).isEqualTo("Example Org (http://example.org:8080/bitbucket)");
        assertThat(items.get(2).value).isEqualTo("http://example.org:8080/bitbucket");
    }

    @Test
    void given__instanceWithCloudAndServers__when__resolvingExistingEndpoint__then__normalizedReturned() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(
                List.of(
                        buildEndpoint(true, "first"),
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "second"),
                        new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", true, "third")
                ));
        assertThat(instance.getEndpointItems()).hasSize(3);
        assertThat(instance.readResolveServerUrl(null)).isEqualTo(BitbucketCloudEndpoint.SERVER_URL);
        assertThat(instance.getEndpointItems()).hasSize(3);
        assertThat(instance.readResolveServerUrl("")).isEqualTo(BitbucketCloudEndpoint.SERVER_URL);
        assertThat(instance.getEndpointItems()).hasSize(3);
        assertThat(instance.readResolveServerUrl("https://bitbucket.EXAMPLE.COM:443/")).isEqualTo("https://bitbucket.example.com");
        assertThat(instance.getEndpointItems()).hasSize(3);
        assertThat(instance.readResolveServerUrl("https://bitbucket.example.com")).isEqualTo("https://bitbucket.example.com");
        assertThat(instance.getEndpointItems()).hasSize(3);
        assertThat(instance.readResolveServerUrl("http://example.org:8080/bitbucket/")).isEqualTo("http://example.org:8080/bitbucket");
        assertThat(instance.getEndpointItems()).hasSize(3);
        assertThat(instance.readResolveServerUrl("http://example.org:8080/bitbucket/foo/../")).isEqualTo("http://example.org:8080/bitbucket");
        assertThat(instance.getEndpointItems()).hasSize(3);
        assertThat(instance.readResolveServerUrl("http://example.org:8080/foo/../bitbucket/.")).isEqualTo("http://example.org:8080/bitbucket");
        assertThat(instance.getEndpointItems()).hasSize(3);
    }

    @Test
    void given__instanceWithCloudAndServers__when__resolvingNewEndpointAsSystem__then__addedAndNormalizedReturned() {
        MockAuthorizationStrategy mockStrategy = new MockAuthorizationStrategy();
        r.jenkins.setAuthorizationStrategy(mockStrategy);
        try (ACLContext context = ACL.as2(ACL.SYSTEM2)) {
            BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
            instance.setEndpoints(List.of(new BitbucketServerEndpoint("existing", "https://bitbucket.test", false, null, false, null)));
            assertThat(instance.getEndpointItems()).hasSize(1);
            assertThat(instance.readResolveServerUrl(null)).isEqualTo(BitbucketCloudEndpoint.SERVER_URL);
            assertThat(instance.getEndpointItems()).hasSize(2);
            assertThat(instance.readResolveServerUrl("")).isEqualTo(BitbucketCloudEndpoint.SERVER_URL);
            assertThat(instance.getEndpointItems()).hasSize(2);
            assertThat(instance.readResolveServerUrl("https://bitbucket.EXAMPLE.COM:443/")).isEqualTo("https://bitbucket.example.com");
            assertThat(instance.getEndpointItems()).hasSize(3);
            assertThat(instance.readResolveServerUrl("https://bitbucket.example.com")).isEqualTo("https://bitbucket.example.com");
            assertThat(instance.getEndpointItems()).hasSize(3);
            assertThat(instance.readResolveServerUrl("http://example.org:8080/bitbucket/")).isEqualTo("http://example.org:8080/bitbucket");
            assertThat(instance.getEndpointItems()).hasSize(4);
            assertThat(instance.readResolveServerUrl("http://example.org:8080/bitbucket/foo/../")).isEqualTo("http://example.org:8080/bitbucket");
            assertThat(instance.getEndpointItems()).hasSize(4);
            assertThat(instance.readResolveServerUrl("http://example.org:8080/foo/../bitbucket/.")).isEqualTo("http://example.org:8080/bitbucket");
            assertThat(instance.getEndpointItems()).hasSize(4);
            assertThat(instance.getEndpoints().get(0).getDisplayName()).isEqualTo("existing");
            assertThat(instance.getEndpoints().get(0).getServerURL()).isEqualTo("https://bitbucket.test");
            assertThat(instance.getEndpoints().get(0).isManageHooks()).isFalse();
            assertThat(instance.getEndpoints().get(0).getCredentialsId()).isNull();
            assertThat(instance.getEndpoints().get(1).getDisplayName()).isEqualTo(Messages.BitbucketCloudEndpoint_displayName());
            assertThat(instance.getEndpoints().get(1).getServerURL()).isEqualTo("https://bitbucket.org");
            assertThat(instance.getEndpoints().get(1).isManageHooks()).isFalse();
            assertThat(instance.getEndpoints().get(1).getCredentialsId()).isNull();
            assertThat(instance.getEndpoints().get(2).getDisplayName()).isEqualTo("example");
            assertThat(instance.getEndpoints().get(2).getServerURL()).isEqualTo("https://bitbucket.example.com");
            assertThat(instance.getEndpoints().get(2).isManageHooks()).isFalse();
            assertThat(instance.getEndpoints().get(2).getCredentialsId()).isNull();
            assertThat(instance.getEndpoints().get(3).getDisplayName()).isEqualTo("example");
            assertThat(instance.getEndpoints().get(3).getServerURL()).isEqualTo("http://example.org:8080/bitbucket");
            assertThat(instance.getEndpoints().get(3).isManageHooks()).isFalse();
            assertThat(instance.getEndpoints().get(3).getCredentialsId()).isNull();
        } finally {
            r.jenkins.setAuthorizationStrategy(AuthorizationStrategy.UNSECURED);
        }
    }

    @Test
    void given__instanceWithCloudAndServers__when__resolvingNewEndpointAsAnon__then__normalizedReturnedNotAdded() {
        BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();
        instance.setEndpoints(List.of(new BitbucketServerEndpoint("existing", "https://bitbucket.test", false, null, false, null)));
        try (ACLContext context = ACL.as2(Jenkins.ANONYMOUS2)) {
            assertThat(instance.getEndpointItems()).hasSize(1);
            assertThat(instance.readResolveServerUrl(null)).isEqualTo(BitbucketCloudEndpoint.SERVER_URL);
            assertThat(instance.getEndpointItems()).hasSize(1);
            assertThat(instance.readResolveServerUrl("")).isEqualTo(BitbucketCloudEndpoint.SERVER_URL);
            assertThat(instance.getEndpointItems()).hasSize(1);
            assertThat(instance.readResolveServerUrl("https://bitbucket.EXAMPLE.COM:443/")).isEqualTo("https://bitbucket.example.com");
            assertThat(instance.getEndpointItems()).hasSize(1);
            assertThat(instance.readResolveServerUrl("https://bitbucket.example.com")).isEqualTo("https://bitbucket.example.com");
            assertThat(instance.getEndpointItems()).hasSize(1);
            assertThat(instance.readResolveServerUrl("http://example.org:8080/bitbucket/")).isEqualTo("http://example.org:8080/bitbucket");
            assertThat(instance.getEndpointItems()).hasSize(1);
            assertThat(instance.readResolveServerUrl("http://example.org:8080/bitbucket/foo/../")).isEqualTo("http://example.org:8080/bitbucket");
            assertThat(instance.getEndpointItems()).hasSize(1);
            assertThat(instance.readResolveServerUrl("http://example.org:8080/foo/../bitbucket/.")).isEqualTo("http://example.org:8080/bitbucket");
            assertThat(instance.getEndpointItems()).hasSize(1);
            assertThat(instance.getEndpoints().get(0).getDisplayName()).isEqualTo("existing");
            assertThat(instance.getEndpoints().get(0).getServerURL()).isEqualTo("https://bitbucket.test");
            assertThat(instance.getEndpoints().get(0).isManageHooks()).isFalse();
            assertThat(instance.getEndpoints().get(0).getCredentialsId()).isNull();
        }
    }

    @Test
    void given__instanceWithConfig__when__configRoundtrip__then__configRetained() throws Exception {
        BitbucketEndpointConfiguration instance = BitbucketEndpointConfiguration.get();
        instance.setEndpoints(
                List.of(
                        buildEndpoint(true, "first"),
                        new BitbucketServerEndpoint("Example Inc", "https://bitbucket.example.com/", true, "second", false, null),
                        new BitbucketServerEndpoint("Example Org", "http://example.org:8080/bitbucket/", false, null, false, null)
                ));
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(
                Collections.singletonMap(Domain.global(), Arrays.asList(
                        new UsernamePasswordCredentialsImpl(
                                CredentialsScope.SYSTEM, "first", null, "user1", "pass1"),
                        new UsernamePasswordCredentialsImpl(
                                CredentialsScope.SYSTEM, "second", null, "user2", "pass2"),
                        new UsernamePasswordCredentialsImpl(
                                CredentialsScope.SYSTEM, "third", null, "user3", "pass3")
                )));

        r.configRoundtrip();

        assertThat(instance.getEndpoints()).hasSize(3);

        BitbucketCloudEndpoint endpoint1 = (BitbucketCloudEndpoint) instance.getEndpoints().get(0);
        assertThat(endpoint1.getDisplayName()).isEqualTo(Messages.BitbucketCloudEndpoint_displayName());
        assertThat(endpoint1.getServerURL()).isEqualTo("https://bitbucket.org");
        assertThat(endpoint1.isManageHooks()).isTrue();
        assertThat(endpoint1.getCredentialsId()).isEqualTo("first");

        BitbucketServerEndpoint endpoint2 = (BitbucketServerEndpoint) instance.getEndpoints().get(1);
        assertThat(endpoint2.getDisplayName()).isEqualTo("Example Inc");
        assertThat(endpoint2.getServerURL()).isEqualTo("https://bitbucket.example.com");
        assertThat(endpoint2.isManageHooks()).isTrue();
        assertThat(endpoint2.getCredentialsId()).isEqualTo("second");

        BitbucketServerEndpoint endpoint3 = (BitbucketServerEndpoint) instance.getEndpoints().get(2);
        assertThat(endpoint3.getDisplayName()).isEqualTo("Example Org");
        assertThat(endpoint3.getServerURL()).isEqualTo("http://example.org:8080/bitbucket");
        assertThat(endpoint3.isManageHooks()).isFalse();
        assertThat(endpoint3.getCredentialsId()).isNull();
    }

    @Test
    void given__serverConfig__without__webhookImplementation__then__usePlugin() throws Exception {
        final URL configWithoutWebhookImpl = Resources.getResource(getClass(), "config-without-webhook-impl.xml");
        final File configFile = new File(Jenkins.get().getRootDir(), BitbucketEndpointConfiguration.class.getName() + ".xml");
        FileUtils.copyURLToFile(configWithoutWebhookImpl, configFile);

        final BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();

        assertThat(instance.getEndpoints()).hasOnlyElementsOfType(BitbucketServerEndpoint.class);
        final BitbucketServerEndpoint endpoint = (BitbucketServerEndpoint) instance.getEndpoints().get(0);
        assertThat(endpoint.getWebhookImplementation()).isEqualTo(BitbucketServerWebhookImplementation.PLUGIN);
    }

    @Test
    void load_serverConfig__with_old_signatures() throws Exception {
        final URL configWithoutWebhookImpl = Resources.getResource(getClass(), "config-old-signatures.xml");
        final File configFile = new File(Jenkins.get().getRootDir(), BitbucketEndpointConfiguration.class.getName() + ".xml");
        FileUtils.copyURLToFile(configWithoutWebhookImpl, configFile);

        final BitbucketEndpointConfiguration instance = new BitbucketEndpointConfiguration();

        assertThat(instance.getEndpoints()).element(0)
            .isInstanceOf(BitbucketCloudEndpoint.class)
            .asInstanceOf(InstanceOfAssertFactories.type(BitbucketCloudEndpoint.class))
            .satisfies(endpoint -> {
                assertThat(endpoint.isEnableCache()).isFalse();
                assertThat(endpoint.isManageHooks()).isFalse();
                assertThat(endpoint.getCredentialsId()).isNull();
                assertThat(endpoint.getTeamCacheDuration()).isEqualTo(360);
                assertThat(endpoint.getRepositoriesCacheDuration()).isEqualTo(180);
            });
        assertThat(instance.getEndpoints()).element(1)
            .isInstanceOf(BitbucketServerEndpoint.class)
            .asInstanceOf(InstanceOfAssertFactories.type(BitbucketServerEndpoint.class))
            .satisfies(endpoint -> {
                assertThat(endpoint.isManageHooks()).isTrue();
                assertThat(endpoint.getCredentialsId()).isEqualTo("admin.basic.credentials");
                assertThat(endpoint.getEndpointJenkinsRootURL()).isEqualTo("http://host.docker.internal:8090/jenkins/");
                assertThat(endpoint.getDisplayName()).isEqualTo("server");
                assertThat(endpoint.getWebhookImplementation()).isEqualTo(BitbucketServerWebhookImplementation.NATIVE);
            });
        assertThat(instance.getEndpoints()).element(1).isInstanceOf(BitbucketServerEndpoint.class);
    }

    @Test
    void should_support_configuration_as_code() throws Exception {
        ConfigurationAsCode.get().configure(getClass().getResource(getClass().getSimpleName() + "/configuration-as-code.yml").toString());

        BitbucketEndpointConfiguration instance = BitbucketEndpointConfiguration.get();

        assertThat(instance.getEndpoints()).hasSize(12);

        assertThat(instance.getEndpoints()).element(0).isInstanceOf(BitbucketCloudEndpoint.class);
        BitbucketCloudEndpoint endpoint = (BitbucketCloudEndpoint) instance.getEndpoints().get(0);
        assertThat(endpoint.getDisplayName()).isEqualTo(Messages.BitbucketCloudEndpoint_displayName());
        assertThat(endpoint.getServerURL()).isEqualTo("https://bitbucket.org");
        assertThat(endpoint.isManageHooks()).isTrue();
        assertThat(endpoint.getCredentialsId()).isEqualTo("first");
        assertThat(endpoint.isEnableCache()).isTrue();
        assertThat(endpoint.getTeamCacheDuration()).isEqualTo(1);
        assertThat(endpoint.getRepositoriesCacheDuration()).isEqualTo(2);
        assertThat(endpoint.isEnableHookSignature()).isTrue();
        assertThat(endpoint.getHookSignatureCredentialsId()).isEqualTo("secretId");

        BitbucketServerEndpoint serverEndpoint;

        // test that set credentials, before enabling the corresponding management, will result null
        assertThat(instance.getEndpoints()).element(1).isInstanceOf(BitbucketServerEndpoint.class);
        serverEndpoint = (BitbucketServerEndpoint) instance.getEndpoints().get(1);
        assertThat(serverEndpoint.getDisplayName()).isEqualTo("Example Inc");
        assertThat(serverEndpoint.getServerURL()).isEqualTo("https://bitbucket.example.com");
        assertThat(serverEndpoint.isManageHooks()).isTrue();
        assertThat(serverEndpoint.getCredentialsId()).isEqualTo("second");
        assertThat(serverEndpoint.getWebhookImplementation()).isEqualTo(BitbucketServerWebhookImplementation.NATIVE);
        assertThat(serverEndpoint.getServerVersion()).isEqualTo(BitbucketServerVersion.getMinSupportedVersion().name());

        assertThat(instance.getEndpoints()).element(2).isInstanceOf(BitbucketServerEndpoint.class);
        serverEndpoint = (BitbucketServerEndpoint) instance.getEndpoints().get(2);
        assertThat(serverEndpoint.getDisplayName()).isEqualTo("Example Org");
        assertThat(serverEndpoint.getServerURL()).isEqualTo("http://example.org:8080/bitbucket");
        assertThat(serverEndpoint.isManageHooks()).isFalse();
        assertThat(serverEndpoint.getCredentialsId()).isNull();
        assertThat(serverEndpoint.getWebhookImplementation()).isEqualTo(BitbucketServerWebhookImplementation.NATIVE);
        assertThat(serverEndpoint.getServerVersion()).isEqualTo(BitbucketServerVersion.getMinSupportedVersion().name());

        serverEndpoint = (BitbucketServerEndpoint) instance.getEndpoints().get(3);
        assertThat(serverEndpoint.getDisplayName()).isEqualTo("Example Inc");
        assertThat(serverEndpoint.getServerURL()).isEqualTo("http://bitbucket.example.com:8083");
        assertThat(serverEndpoint.isManageHooks()).isTrue();
        assertThat(serverEndpoint.getCredentialsId()).isEqualTo("third");
        assertThat(serverEndpoint.getWebhookImplementation()).isEqualTo(BitbucketServerWebhookImplementation.NATIVE);
        assertThat(serverEndpoint.getServerVersion()).isEqualTo(BitbucketServerVersion.getMinSupportedVersion().name());

        serverEndpoint = (BitbucketServerEndpoint) instance.getEndpoints().get(4);
        assertThat(serverEndpoint.getDisplayName()).isEqualTo("Example Inc");
        assertThat(serverEndpoint.getServerURL()).isEqualTo("http://bitbucket.example.com:8084");
        assertThat(serverEndpoint.isManageHooks()).isTrue();
        assertThat(serverEndpoint.getCredentialsId()).isEqualTo("fourth");
        assertThat(serverEndpoint.getWebhookImplementation()).isEqualTo(BitbucketServerWebhookImplementation.PLUGIN);
        assertThat(serverEndpoint.getServerVersion()).isEqualTo(BitbucketServerVersion.getMinSupportedVersion().name());

        serverEndpoint = (BitbucketServerEndpoint) instance.getEndpoints().get(5);
        assertThat(serverEndpoint.getDisplayName()).isEqualTo("Example Inc");
        assertThat(serverEndpoint.getServerURL()).isEqualTo("http://bitbucket.example.com:8085");
        assertThat(serverEndpoint.isManageHooks()).isTrue();
        assertThat(serverEndpoint.getCredentialsId()).isEqualTo("fifth");
        assertThat(serverEndpoint.getWebhookImplementation()).isEqualTo(BitbucketServerWebhookImplementation.PLUGIN);
        assertThat(serverEndpoint.getServerVersion()).isEqualTo(BitbucketServerVersion.getMinSupportedVersion().name());

        serverEndpoint = (BitbucketServerEndpoint) instance.getEndpoints().get(6);
        assertThat(serverEndpoint.getDisplayName()).isEqualTo("Example Inc");
        assertThat(serverEndpoint.getServerURL()).isEqualTo("http://bitbucket.example.com:8086");
        assertThat(serverEndpoint.isManageHooks()).isFalse();
        assertThat(serverEndpoint.getCredentialsId()).isNull();
        assertThat(serverEndpoint.getWebhookImplementation()).isEqualTo(BitbucketServerWebhookImplementation.NATIVE);
        assertThat(serverEndpoint.getServerVersion()).isEqualTo(BitbucketServerVersion.getMinSupportedVersion().name());

        serverEndpoint = (BitbucketServerEndpoint) instance.getEndpoints().get(7);
        assertThat(serverEndpoint.getDisplayName()).isEqualTo("Example Inc");
        assertThat(serverEndpoint.getServerURL()).isEqualTo("http://bitbucket.example.com:8087");
        assertThat(serverEndpoint.isManageHooks()).isFalse();
        assertThat(serverEndpoint.getCredentialsId()).isNull();
        assertThat(serverEndpoint.getWebhookImplementation()).isEqualTo(BitbucketServerWebhookImplementation.NATIVE);
        assertThat(serverEndpoint.getServerVersion()).isEqualTo(BitbucketServerVersion.getMinSupportedVersion().name());

        serverEndpoint = (BitbucketServerEndpoint) instance.getEndpoints().get(8);
        assertThat(serverEndpoint.getDisplayName()).isEqualTo("Example Inc");
        assertThat(serverEndpoint.getServerURL()).isEqualTo("http://bitbucket.example.com:8088");
        assertThat(serverEndpoint.isManageHooks()).isFalse();
        assertThat(serverEndpoint.getCredentialsId()).isNull();
        assertThat(serverEndpoint.getWebhookImplementation()).isEqualTo(BitbucketServerWebhookImplementation.NATIVE);
        assertThat(serverEndpoint.getServerVersion()).isEqualTo(BitbucketServerVersion.getMinSupportedVersion().name());

        serverEndpoint = (BitbucketServerEndpoint) instance.getEndpoints().get(9);
        assertThat(serverEndpoint.getDisplayName()).isEqualTo("Example Inc");
        assertThat(serverEndpoint.getServerURL()).isEqualTo("http://bitbucket.example.com:8089");
        assertThat(serverEndpoint.isManageHooks()).isFalse();
        assertThat(serverEndpoint.getCredentialsId()).isNull();
        assertThat(serverEndpoint.getWebhookImplementation()).isEqualTo(BitbucketServerWebhookImplementation.NATIVE);
        assertThat(serverEndpoint.getServerVersion()).isEqualTo(BitbucketServerVersion.getMinSupportedVersion().name());

        serverEndpoint = (BitbucketServerEndpoint) instance.getEndpoints().get(10);
        assertThat(serverEndpoint.getDisplayName()).isEqualTo("Example Inc");
        assertThat(serverEndpoint.getServerURL()).isEqualTo("http://bitbucket.example.com:8090");
        assertThat(serverEndpoint.isManageHooks()).isFalse();
        assertThat(serverEndpoint.getCredentialsId()).isNull();
        assertThat(serverEndpoint.getWebhookImplementation()).isEqualTo(BitbucketServerWebhookImplementation.NATIVE);
        assertThat(serverEndpoint.getServerVersion()).isEqualTo(BitbucketServerVersion.getMinSupportedVersion().name());

        serverEndpoint = (BitbucketServerEndpoint) instance.getEndpoints().get(11);
        assertThat(serverEndpoint.getDisplayName()).isEqualTo("Example Inc");
        assertThat(serverEndpoint.getServerURL()).isEqualTo("http://bitbucket.example.com:8091");
        assertThat(serverEndpoint.isManageHooks()).isFalse();
        assertThat(serverEndpoint.getCredentialsId()).isNull();
        assertThat(serverEndpoint.getWebhookImplementation()).isEqualTo(BitbucketServerWebhookImplementation.NATIVE);
        assertThat(serverEndpoint.getServerVersion()).isEqualTo(BitbucketServerVersion.getMinSupportedVersion().name());
    }

    private BitbucketCloudEndpoint buildEndpoint(boolean manageHook, String credentials) {
        return new BitbucketCloudEndpoint(false, 0, 0, manageHook, credentials, false, null);
    }

}
