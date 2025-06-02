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

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainSpecification;
import com.cloudbees.plugins.credentials.domains.HostnameSpecification;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;

@WithJenkins
class AbstractBitbucketEndpointDescriptorTest {

    static JenkinsRule j;

    @BeforeAll
    static void init(JenkinsRule rule) {
        j = rule;
    }

    @BeforeEach
    void reset() {
        SystemCredentialsProvider.getInstance()
                .setDomainCredentialsMap(Collections.emptyMap());
    }

    @Test
    void given__cloudCredentials__when__listingForServer__then__noCredentials() throws Exception {
        SystemCredentialsProvider.getInstance()
            .setDomainCredentialsMap(Map.of(
                    new Domain("cloud", "bb cloud", List.of(new HostnameSpecification("bitbucket.org", ""))),
                    List.of(new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, "dummy", "dummy", "user", "pass")))
            );
        ListBoxModel result = new DummyEndpointConfiguration(true, "dummy")
                .getDescriptor()
                .doFillCredentialsIdItems(null, "http://bitbucket.example.com");
        assertThat(result).isEmpty();
    }

    @Test
    void given__cloudCredentials__when__listingForCloud__then__credentials() throws Exception {
        SystemCredentialsProvider.getInstance()
            .setDomainCredentialsMap(Map.of(
                    new Domain("cloud", "bb cloud", List.of(new HostnameSpecification("bitbucket.org", ""))),
                    List.of(new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, "dummy", "dummy", "user", "pass")))
            );
        ListBoxModel result = new DummyEndpointConfiguration(true, "dummy")
                .getDescriptor()
                .doFillCredentialsIdItems(null, "http://bitbucket.org");
        assertThat(result).hasSize(1);
    }

    @Test
    void given__cloud_HMAC_Credentials__when__listingForCloud__then__credentials() {
        List<DomainSpecification> domainSpecifications = Collections.<DomainSpecification>singletonList(new HostnameSpecification("bitbucket.org", ""));
        SystemCredentialsProvider.getInstance()
            .setDomainCredentialsMap(Map.of(
                    new Domain("cloud", "bb cloud", domainSpecifications),
                    List.of(new StringCredentialsImpl(CredentialsScope.SYSTEM, "dummy", "dummy", Secret.fromString("pass"))))
            );
        ListBoxModel result = new DummyEndpointConfiguration(true, "dummy")
                .getDescriptor()
                .doFillHookSignatureCredentialsIdItems(null, "https://bitbucket.org");
        assertThat(result).hasSize(1);
    }
}
