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
package com.cloudbees.jenkins.plugins.bitbucket.util;

import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;

@WithJenkins
class BitbucketCredentialsUtilsTest {

    private static JenkinsRule j;

    @BeforeAll
    static void init(JenkinsRule rule) throws Exception {
        j = rule;

        StandardUsernamePasswordCredentials credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "id", "description", "username", "password");

        CredentialsStore store = CredentialsProvider.lookupStores(j.getInstance()).iterator().next();
        store.addCredentials(Domain.global(), credentials);
    }

    /**
     * Some plugins do remote work when getPassword is called and aren't expecting to just be randomly looked up
     * One example is GitHubAppCredentials
     */
    @Test
    @Issue("JENKINS-63401")
    void matches_returns_false_when_exception_getting_password() throws Exception {
        StandardUsernamePasswordCredentials exceptionCredentials = new ExceptionalCredentials();

        CredentialsStore store = CredentialsProvider.lookupStores(j.getInstance()).iterator().next();
        store.addCredentials(Domain.global(), exceptionCredentials);

        ListBoxModel result = BitbucketCredentialsUtils.listCredentials(j.jenkins, BitbucketCloudEndpoint.SERVER_URL, null);
        assertThat(result)
            .isNotEmpty()
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("name")
            .containsOnly(new ListBoxModel.Option(null, "id"));
    }

    @Test
    @Issue("JENKINS-75225")
    void matches_returns_false_when_getting_password_takes_longer() throws Exception {
        StandardUsernamePasswordCredentials remoteCredentials = new TakeLongCredentials("remote-credentials");

        CredentialsStore store = CredentialsProvider.lookupStores(j.getInstance()).iterator().next();
        store.addCredentials(Domain.global(), remoteCredentials);

        ListBoxModel result = BitbucketCredentialsUtils.listCredentials(j.jenkins, BitbucketCloudEndpoint.SERVER_URL, null);
        assertThat(result)
            .isNotEmpty()
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("name")
            .containsOnly(new ListBoxModel.Option(null, "id"));
    }

    @Test
    @Issue("JENKINS-76330")
    void matches_returns_false_when_blacklisted() throws Exception {
        List<StandardUsernamePasswordCredentials> remoteCredentials = List.of(new TakeLongCredentials("1"),
                new TakeLongCredentials("2"),
                new TakeLongCredentials("3"),
                new TakeLongCredentials("4"));

        TakeLongCredentials.resetCallsCounter();
        CredentialsStore store = CredentialsProvider.lookupStores(j.getInstance()).iterator().next();
        for (StandardUsernamePasswordCredentials c : remoteCredentials) {
            store.addCredentials(Domain.global(), c);
        }

        ListBoxModel result = BitbucketCredentialsUtils.listCredentials(j.jenkins, BitbucketCloudEndpoint.SERVER_URL, null);
        assertThat(result)
            .isNotEmpty()
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("name")
            .containsOnly(new ListBoxModel.Option(null, "id"));

        // assert that the number of calls to TakeLongCredentials#getPassword
        // were actually 2 and no more even though 4 TakeLongCredentials were processed
        assertThat(TakeLongCredentials.getCallsCounter()).isEqualTo(2);
    }

    @SuppressWarnings("serial")
    private static class ExceptionalCredentials implements StandardUsernamePasswordCredentials {

        @NonNull
        @Override
        public Secret getPassword() {
            throw new IllegalArgumentException("Failed authentication");
        }

        @NonNull
        @Override
        public String getUsername() {
            return "dummy-username";
        }

        @Override
        public CredentialsScope getScope() {
            return null;
        }

        @NonNull
        @Override
        public CredentialsDescriptor getDescriptor() {
            return null;
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public String getId() {
            return "exception-credentials";
        }
    }

    @SuppressWarnings("serial")
    private static class TakeLongCredentials implements StandardUsernamePasswordCredentials {
        private static int countCalls = 0;

        static void resetCallsCounter() {
            countCalls = 0;
        }

        static int getCallsCounter() {
            return countCalls;
        }

        private String id;

        public TakeLongCredentials(@NonNull String id) {
            this.id = id;
        }

        @NonNull
        @Override
        public Secret getPassword() {
            countCalls += 1;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return Secret.fromString("password");
        }

        @NonNull
        @Override
        public String getUsername() {
            return "dummy-username";
        }

        @Override
        public CredentialsScope getScope() {
            return null;
        }

        @NonNull
        @Override
        public CredentialsDescriptor getDescriptor() {
            return null;
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public String getId() {
            return id;
        }
    }
}
