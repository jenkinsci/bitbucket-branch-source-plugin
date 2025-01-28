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
package com.cloudbees.jenkins.plugins.bitbucket.impl.extension;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import hudson.Extension;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import java.util.Objects;
import org.jenkinsci.plugins.gitclient.GitClient;

public class GitClientAuthenticatorExtension extends GitSCMExtension {

    private final StandardUsernameCredentials credentials;
    private final String url;

    // @DataBoundConstructor causes a failure: Could not instantiate arguments for com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl. Secrets are involved, so details are available on more verbose logging levels.
    public GitClientAuthenticatorExtension(String url, StandardUsernameCredentials credentials) {
        this.url = url;
        this.credentials = credentials;
    }

    @Override
    public GitClient decorate(GitSCM scm, GitClient git) throws GitException {
        if (credentials != null) {
            if (url == null) {
                git.setCredentials(credentials);
            } else {
                git.addCredentials(url, credentials);
            }
        }

        return git;
    }

    public StandardUsernameCredentials getCredentials() {
        return credentials;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public int hashCode() {
        return Objects.hash(credentials != null ? credentials.getId() : null, credentials);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        GitClientAuthenticatorExtension other = (GitClientAuthenticatorExtension) obj;
        return Objects.equals(credentials != null ? credentials.getId() : credentials, other.credentials != null ? other.credentials.getId() : other.credentials)
                && Objects.equals(url, other.url);
    }

    @Extension
    // No @Symbol because Pipeline users should not configure this in other ways than this plugin provides
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Translated git client credentials.";
        }
    }
}
