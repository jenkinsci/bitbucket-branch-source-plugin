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
package com.cloudbees.jenkins.plugins.bitbucket.impl.extension;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import java.util.List;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitClientAuthenticatorExtensionTest {

    @Issue("JENKINS-75188")
    @Test
    void test_equals_hashCode() throws Exception {
        GitSCMExtension extension = new GitClientAuthenticatorExtension("url", null);
        assertThat(extension.hashCode()).isNotZero();
        assertThat(extension).isEqualTo(new GitClientAuthenticatorExtension("url", null));

        extension = new GitClientAuthenticatorExtension("url", new UsernamePasswordCredentialsImpl(null, "id", null, null, null));
        assertThat(extension.hashCode()).isNotZero();
        assertThat(extension).isNotEqualTo(new GitClientAuthenticatorExtension("url", null));

        extension = new GitClientAuthenticatorExtension("url", new UsernamePasswordCredentialsImpl(null, null, null, null, null));
        assertThat(extension.hashCode()).isNotZero();
        assertThat(extension).isNotEqualTo(new GitClientAuthenticatorExtension("url", new UsernamePasswordCredentialsImpl(null, "some-id", null, null, null)));
    }

    @Issue("JENKINS-76486")
    @Test
    void decoratesAllRemotesUsingTheTranslatedCredential() throws Exception {
        StandardUsernameCredentials credentials = mock(StandardUsernameCredentials.class);
        GitClientAuthenticatorExtension extension =
                new GitClientAuthenticatorExtension("https://bitbucket.org/fork/repository.git", credentials);
        UserRemoteConfig origin = new UserRemoteConfig(
                "https://bitbucket.org/fork/repository.git", "origin", null, null);
        UserRemoteConfig upstream = new UserRemoteConfig(
                "https://bitbucket.org/upstream/repository.git", "upstream", null, null);
        UserRemoteConfig other = new UserRemoteConfig(
                "https://example.com/other/repository.git", "other", null, "other-credential");
        GitSCM scm = mock(GitSCM.class);
        GitClient git = mock(GitClient.class);
        when(scm.getUserRemoteConfigs()).thenReturn(List.of(origin, upstream, other));

        assertThat(extension.decorate(scm, git)).isSameAs(git);

        verify(git).addCredentials(origin.getUrl(), credentials);
        verify(git).addCredentials(upstream.getUrl(), credentials);
        verify(git, never()).addCredentials(other.getUrl(), credentials);
    }
}
