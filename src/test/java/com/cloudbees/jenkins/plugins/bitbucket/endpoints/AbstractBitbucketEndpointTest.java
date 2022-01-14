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
import com.damnhandy.uri.template.UriTemplate;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class AbstractBitbucketEndpointTest {

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Before
    public void reset() {
        SystemCredentialsProvider.getInstance()
                .setDomainCredentialsMap(Collections.<Domain, List<Credentials>>emptyMap());
    }

    @Test
    public void given__manage_true__when__noCredentials__then__manage_false() {
        assertThat(new Dummy(true, null).isManageHooks(), is(false));
    }

    @Test
    public void given__manage_false__when__credentials__then__manage_false() {
        assertThat(new Dummy(false, "dummy").isManageHooks(), is(false));
    }

    @Test
    public void given__manage_false__when__credentials__then__credentials_null() {
        assertThat(new Dummy(false, "dummy").getCredentialsId(), is(nullValue()));
    }

    @Test
    public void given__manage_true__when__credentials__then__manage_true() {
        assertThat(new Dummy(true, "dummy").isManageHooks(), is(true));
    }

    @Test
    public void given__manage_true__when__credentials__then__credentialsSet() {
        assertThat(new Dummy(true, "dummy").getCredentialsId(), is("dummy"));
    }

    @Test
    public void given__mange__when__systemCredentials__then__credentialsFound() {
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(Collections.singletonMap(Domain.global(),
                Collections.<Credentials>singletonList(new UsernamePasswordCredentialsImpl(
                        CredentialsScope.SYSTEM, "dummy", "dummy", "user", "pass"))));
        assertThat(new Dummy(true, "dummy").credentials(), notNullValue());
    }

    @Test
    public void given__mange__when__globalCredentials__then__credentialsFound() {
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(Collections.singletonMap(Domain.global(),
                Collections.<Credentials>singletonList(new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, "dummy", "dummy", "user", "pass"))));
        assertThat(new Dummy(true, "dummy").credentials(), notNullValue());
    }

    @Test
    public void given__mange__when__noCredentials__then__credentials_none() {
        assertThat(new Dummy(true, "dummy").credentials(), nullValue());
    }

    private static class Dummy extends AbstractBitbucketEndpoint {

        Dummy(boolean manageHooks, String credentialsId) {
            super(manageHooks, credentialsId);
        }

        @Override
        public String getDisplayName() {
            return "Dummy";
        }

        @NonNull
        @Override
        public String getServerUrl() {
            return "http://dummy.example.com";
        }

        @NonNull
        @Override
        public String getBitbucketJenkinsRootUrl() {
            return "http://master.example.com";
        }

        @NonNull
        @Override
        public String getRepositoryUrl(@NonNull String repoOwner, @NonNull String repository) {
            return UriTemplate
                    .fromTemplate("http://dummy.example.com{/owner,repo}")
                    .set("owner", repoOwner)
                    .set("repo", repository)
                    .expand();
        }
    }
}
