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

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;

@WithJenkins
class AbstractBitbucketEndpointTest {

    static JenkinsRule j;

    @BeforeAll
    static void init(JenkinsRule rule) {
        j = rule;
    }

    @BeforeEach
    void setup() {
        SystemCredentialsProvider.getInstance()
                .setDomainCredentialsMap(Collections.<Domain, List<Credentials>>emptyMap());
    }

    @Test
    void given__manage_true__when__noCredentials__then__manage_false() {
        assertThat(new DummyEndpointConfiguration(true, null).isManageHooks()).isFalse();
    }

    @Test
    void given__manage_false__when__credentials__then__manage_false() {
        assertThat(new DummyEndpointConfiguration(false, "dummy").isManageHooks()).isFalse();
    }

    @Test
    void given__manage_false__when__credentials__then__credentials_null() {
        assertThat(new DummyEndpointConfiguration(false, "dummy").getCredentialsId()).isNull();
    }

    @Test
    void given__manage_true__when__credentials__then__manage_true() {
        assertThat(new DummyEndpointConfiguration(true, "dummy").isManageHooks()).isTrue();
    }

    @Test
    void given__manage_true__when__credentials__then__credentialsSet() {
        assertThat(new DummyEndpointConfiguration(true, "dummy").getCredentialsId()).isEqualTo("dummy");
    }

    @Test
    void given__mange__when__systemCredentials__then__credentialsFound() throws Exception {
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(Collections.singletonMap(Domain.global(),
                Collections.<Credentials>singletonList(new UsernamePasswordCredentialsImpl(
                        CredentialsScope.SYSTEM, "dummy", "dummy", "user", "pass"))));
        assertThat(new DummyEndpointConfiguration(true, "dummy").credentials()).isNotNull();
    }

    @Test
    void given__mange__when__globalCredentials__then__credentialsFound() throws Exception {
        SystemCredentialsProvider.getInstance()
            .setDomainCredentialsMap(Map.of(
                    Domain.global(),
                    List.of(new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "dummy", "dummy", "user", "pass")))
            );
        assertThat(new DummyEndpointConfiguration(true, "dummy").credentials()).isNotNull();
    }

    @Test
    void given__mange__when__noCredentials__then__credentials_none() {
        assertThat(new DummyEndpointConfiguration(true, "dummy").credentials()).isNull();
    }
}
