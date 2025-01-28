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

import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.plugins.git.extensions.GitSCMExtension;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

import static org.assertj.core.api.Assertions.assertThat;

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
}
