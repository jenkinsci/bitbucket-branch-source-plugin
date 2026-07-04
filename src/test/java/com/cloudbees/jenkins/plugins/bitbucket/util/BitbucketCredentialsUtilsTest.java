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

import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketServerEndpoint;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;

@WithJenkins
class BitbucketCredentialsUtilsTest {

    private static final String CLOUD_URL = "https://bitbucket.org";
    private static final String SERVER_URL = "http://localhost";

    private JenkinsRule j;
    private CredentialsStore store;

    @BeforeEach
    void init(JenkinsRule rule) throws Exception {
        j = rule;
        store = CredentialsProvider.lookupStores(j.getInstance()).iterator().next();
        store.addCredentials(Domain.global(),
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "id", "description", "username", "password"));
    }

    @Test
    @Issue("JENKINS-63401")
    void credentials_whose_getPassword_throws_are_included_in_dropdown() throws Exception {
        // Given a credential store with one normal credential and one whose getPassword() throws
        store.addCredentials(Domain.global(), new ExceptionalCredentials());

        // When listing credentials
        ListBoxModel result = BitbucketCredentialsUtils.listCredentials(j.jenkins, SERVER_URL, null);

        // Then both credentials appear
        assertThat(result)
                .extracting(o -> o.value)
                .contains("id", "exception-credentials");
    }

    @Test
    @Issue("JENKINS-75225")
    void credentials_with_slow_getPassword_are_included_in_dropdown() throws Exception {
        // Given a credential store with one normal credential and one whose getPassword() sleeps 1s
        store.addCredentials(Domain.global(), new SlowCredentials("slow-credentials"));

        // When listing credentials
        ListBoxModel result = BitbucketCredentialsUtils.listCredentials(j.jenkins, SERVER_URL, null);

        // Then both credentials appear
        assertThat(result)
                .extracting(o -> o.value)
                .contains("id", "slow-credentials");
    }

    @Test
    @Issue("JENKINS-76425")
    void blacklisted_credentials_are_still_included_on_subsequent_calls() throws Exception {
        // Given 4 slow credentials of the same class (before fix: 2 timeouts → entire class blacklisted)
        List<String> slowIds = List.of("slow-1", "slow-2", "slow-3", "slow-4");
        for (String id : slowIds) {
            store.addCredentials(Domain.global(), new SlowCredentials(id));
        }

        // When listCredentials is called twice
        ListBoxModel first = BitbucketCredentialsUtils.listCredentials(j.jenkins, SERVER_URL, null);
        ListBoxModel second = BitbucketCredentialsUtils.listCredentials(j.jenkins, SERVER_URL, null);

        // Then all slow credentials appear in both calls
        for (ListBoxModel result : List.of(first, second)) {
            assertThat(result)
                    .extracting(o -> o.value)
                    .contains("slow-1", "slow-2", "slow-3", "slow-4");
        }
    }

    @Test
    void checkCredentialsId_returns_ok_for_username_password_credential() throws Exception {
        // Given a normal username/password credential in the store (id="id", user="username", pass="password")
        // When checkCredentialsId is called for a Cloud URL
        FormValidation result = BitbucketCredentialsUtils.checkCredentialsId(null, CLOUD_URL, "id");

        // Then result is ok
        assertThat(result.kind).isEqualTo(FormValidation.Kind.OK);
    }

    @Test
    void checkCredentialsId_returns_ok_for_oauth_credential() throws Exception {
        store.addCredentials(Domain.global(), new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, "oauth-old", null,
                "A".repeat(18),
                "B".repeat(32)));

        FormValidation result = BitbucketCredentialsUtils.checkCredentialsId(null, CLOUD_URL, "oauth-old");

        assertThat(result.kind).isEqualTo(FormValidation.Kind.OK);
    }

    @Test
    void checkCredentialsId_returns_ok_for_api_token_credential() throws Exception {
        store.addCredentials(Domain.global(), new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, "api-token", null,
                "user@example.com",
                "T".repeat(192)));

        FormValidation result = BitbucketCredentialsUtils.checkCredentialsId(null, CLOUD_URL, "api-token");

        assertThat(result.kind).isEqualTo(FormValidation.Kind.OK);
    }

    @Test
    void checkCredentialsId_returns_warning_for_blank_username() throws Exception {
        store.addCredentials(Domain.global(), new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, "blank-user", null, "", "somepassword"));

        FormValidation result = BitbucketCredentialsUtils.checkCredentialsId(null, CLOUD_URL, "blank-user");

        assertThat(result.kind).isEqualTo(FormValidation.Kind.WARNING);
    }

    @Test
    void checkCredentialsId_returns_warning_for_blank_password() throws Exception {
        store.addCredentials(Domain.global(), new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, "blank-pass", null, "someuser", ""));

        FormValidation result = BitbucketCredentialsUtils.checkCredentialsId(null, CLOUD_URL, "blank-pass");

        assertThat(result.kind).isEqualTo(FormValidation.Kind.WARNING);
    }

    @Test
    void checkCredentialsId_returns_warning_not_exception_when_getPassword_throws() throws Exception {
        // Given a credential whose getPassword() throws (e.g. Vault backend offline)
        store.addCredentials(Domain.global(), new ExceptionalCredentials());

        // When checkCredentialsId is called for that credential
        FormValidation result = BitbucketCredentialsUtils.checkCredentialsId(null, CLOUD_URL, "exception-credentials");

        // Then a warning is returned rather than the exception propagating to an HTTP 500
        assertThat(result.kind).isEqualTo(FormValidation.Kind.WARNING);
    }

    @Test
    void checkCredentialsId_returns_error_when_credential_not_found() throws Exception {
        FormValidation result = BitbucketCredentialsUtils.checkCredentialsId(null, CLOUD_URL, "nonexistent-id");

        assertThat(result.kind).isEqualTo(FormValidation.Kind.ERROR);
    }

    @Test
    void checkCredentialsId_server_endpoint_validation_is_skipped() throws Exception {
        // Server validation is intentionally skipped (same as master). Nonexistent credential and blank
        // password both return ok() for Server URLs. Validation for Server is a separate future improvement.
        BitbucketEndpointConfiguration.get().addEndpoint(new BitbucketServerEndpoint("test", SERVER_URL));

        assertThat(BitbucketCredentialsUtils.checkCredentialsId(null, SERVER_URL, "nonexistent-id").kind)
                .isEqualTo(FormValidation.Kind.OK);

        store.addCredentials(Domain.global(), new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, "server-blank-pass", null, "svc-account", ""));
        assertThat(BitbucketCredentialsUtils.checkCredentialsId(null, SERVER_URL, "server-blank-pass").kind)
                .isEqualTo(FormValidation.Kind.OK);
    }

    @Test
    void checkCredentialsId_returns_warning_when_blank_id_provided() {
        FormValidation result = BitbucketCredentialsUtils.checkCredentialsId(null, CLOUD_URL, "");

        assertThat(result.kind).isEqualTo(FormValidation.Kind.WARNING);
    }

    @SuppressWarnings("serial")
    private static class ExceptionalCredentials extends UsernamePasswordCredentialsImpl {

        ExceptionalCredentials() throws hudson.model.Descriptor.FormException {
            super(CredentialsScope.GLOBAL, "exception-credentials", "throws on getPassword", "dummy-username", "placeholder");
        }

        @NonNull
        @Override
        public Secret getPassword() {
            throw new IllegalArgumentException("Failed authentication");
        }
    }

    /**
     * Simulates a credential backed by an external store (Vault, AWS SM) whose {@code getPassword()}
     * performs a network call. Used to reproduce JENKINS-75225 and JENKINS-76425 — before the fix,
     * matchers called {@code getPassword()} during dropdown population, causing timeouts and blacklisting.
     * After the fix, {@code getPassword()} is never called during {@code listCredentials}.
     */
    @SuppressWarnings("serial")
    static class SlowCredentials extends UsernamePasswordCredentialsImpl {

        SlowCredentials(@NonNull String id) throws hudson.model.Descriptor.FormException {
            super(CredentialsScope.GLOBAL, id, "sleeps on getPassword", "dummy-username", "placeholder");
        }

        @NonNull
        @Override
        public Secret getPassword() {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Secret.fromString("password");
        }
    }
}
