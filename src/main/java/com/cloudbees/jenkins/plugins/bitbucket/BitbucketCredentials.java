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

import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Queue;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import java.util.List;
import jenkins.scm.api.SCMSourceOwner;
import org.apache.commons.lang.StringUtils;

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
                            domainRequirementsOf(serverUrl)
                    ),
                    CredentialsMatchers.allOf(
                            CredentialsMatchers.withId(id),
                            CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(type))
                    )
            );
        }
        return null;
    }

    static StandardListBoxModel fillCheckoutCredentials(@CheckForNull String serverUrl,
                                                        @NonNull SCMSourceOwner context,
                                                        @NonNull StandardListBoxModel result) {
        result.includeMatchingAs(
                context instanceof Queue.Task
                        ? Tasks.getDefaultAuthenticationOf((Queue.Task) context)
                        : ACL.SYSTEM,
                context,
                StandardCredentials.class,
                domainRequirementsOf(serverUrl),
                checkoutMatcher()
        );
        return result;
    }

    static StandardListBoxModel fillCredentials(@CheckForNull String serverUrl,
                                                @NonNull SCMSourceOwner context,
                                                @NonNull StandardListBoxModel result) {
        result.includeMatchingAs(
                context instanceof Queue.Task
                        ? Tasks.getDefaultAuthenticationOf((Queue.Task) context)
                        : ACL.SYSTEM,
                context,
                StandardUsernameCredentials.class,
                domainRequirementsOf(serverUrl),
                matcher()
        );
        return result;
    }

    /* package */
    static CredentialsMatcher matcher() {
        return CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class));
    }

    /* package */
    static CredentialsMatcher checkoutMatcher() {
        return CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StandardCredentials.class));
    }

    /* package */
    static List<DomainRequirement> domainRequirementsOf(@CheckForNull String serverUrl) {
        if (serverUrl == null) {
            return URIRequirementBuilder.fromUri("https://bitbucket.org").build();
        } else {
            return URIRequirementBuilder.fromUri(serverUrl).build();
        }
    }

}
