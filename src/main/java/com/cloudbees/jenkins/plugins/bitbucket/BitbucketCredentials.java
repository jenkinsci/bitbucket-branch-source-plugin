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
package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Queue;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSourceOwner;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;

/**
 * Utility class for common code accessing credentials
 */
class BitbucketCredentials {
    private BitbucketCredentials() {
        throw new IllegalAccessError("Utility class");
    }

    @CheckForNull
    static <T extends StandardCredentials> T lookupCredentials(@CheckForNull String serverUrl,
                                                               @CheckForNull SCMSourceOwner context,
                                                               @CheckForNull String id,
                                                               @NonNull Class<T> type) {
        if (StringUtils.isNotBlank(id) && context != null) {
            return CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentials(
                            type,
                            context,
                            context instanceof Queue.Task
                                    ? Tasks.getDefaultAuthenticationOf((Queue.Task) context)
                                    : ACL.SYSTEM,
                            URIRequirementBuilder.fromUri(serverUrl).build()
                    ),
                    CredentialsMatchers.allOf(
                            CredentialsMatchers.withId(id),
                            CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(type))
                    )
            );
        }
        return null;
    }

    static ListBoxModel fillCredentialsIdItems(
        @AncestorInPath SCMSourceOwner context,
        @QueryParameter String serverUrl) {
        StandardListBoxModel result = new StandardListBoxModel();
        result.includeEmptyValue();
        AccessControlled contextToCheck = context == null ? Jenkins.get() : context;
        if (!contextToCheck.hasPermission(CredentialsProvider.VIEW)) {
            return result;
        }
        result.includeMatchingAs(
                context instanceof Queue.Task
                        ? Tasks.getDefaultAuthenticationOf((Queue.Task) context)
                        : ACL.SYSTEM,
                context,
                StandardCredentials.class,
                URIRequirementBuilder.fromUri(serverUrl).build(),
                AuthenticationTokens.matcher(BitbucketAuthenticator.authenticationContext(serverUrl))
        );
        return result;
    }

    static FormValidation checkCredentialsId(
        @AncestorInPath @CheckForNull SCMSourceOwner context,
        @QueryParameter String value,
        @QueryParameter String serverUrl) {
        if (!value.isEmpty()) {
            AccessControlled contextToCheck = context == null ? Jenkins.get() : context;
            contextToCheck.checkPermission(CredentialsProvider.VIEW);
            if (CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentials(
                            StandardCertificateCredentials.class,
                            context,
                            context instanceof Queue.Task ? Tasks.getDefaultAuthenticationOf((Queue.Task) context) : ACL.SYSTEM,
                            URIRequirementBuilder.fromUri(serverUrl).build()),
                    CredentialsMatchers.allOf(
                            CredentialsMatchers.withId(value),
                            AuthenticationTokens.matcher(BitbucketAuthenticator.authenticationContext(serverUrl))
                    )
            ) != null) {
                return FormValidation.warning("A certificate was selected. You will likely need to configure Checkout over SSH.");
            }
            return FormValidation.ok();
        } else {
            return FormValidation.warning("Credentials are required for build notifications");
        }
    }
}
