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
package com.cloudbees.jenkins.plugins.bitbucket.impl.util;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Queue;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSourceOwner;
import org.apache.commons.lang.StringUtils;
import org.springframework.security.core.Authentication;

/**
 * Utility class for common code accessing credentials
 */
public class BitbucketCredentials {
    private BitbucketCredentials() {
        throw new IllegalAccessError("Utility class");
    }

    @CheckForNull
    public static <T extends StandardCredentials> T lookupCredentials(@CheckForNull String serverURL,
                                                                      @CheckForNull Item item,
                                                                      @CheckForNull String id,
                                                                      @NonNull Class<T> type) {
        if (StringUtils.isNotBlank(id)) {
            Authentication authentication = item instanceof Queue.Task task
                    ? task.getDefaultAuthentication2()
                    : ACL.SYSTEM2;

            return CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentialsInItem(
                            type,
                            item,
                            authentication,
                            URIRequirementBuilder.fromUri(serverURL).build()
                    ),
                    CredentialsMatchers.allOf(
                            CredentialsMatchers.withId(id),
                            CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(type))
                    )
            );
        }
        return null;
    }

    @CheckForNull
    public static <T extends StandardCredentials> T lookupCredentials(@CheckForNull String serverURL,
                                                                      @CheckForNull ItemGroup<?> itemGroup,
                                                                      @CheckForNull String id,
                                                                      @NonNull Class<T> type) {
        if (StringUtils.isNotBlank(id)) {
            return CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentialsInItemGroup(
                            type,
                            itemGroup,
                            null,
                            URIRequirementBuilder.fromUri(serverURL).build()
                            ),
                    CredentialsMatchers.allOf(
                            CredentialsMatchers.withId(id),
                            CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(type))
                            )
                    );
        }
        return null;
    }

    public static ListBoxModel fillCredentialsIdItems(SCMSourceOwner context, String serverURL) {
        StandardListBoxModel result = new StandardListBoxModel();
        result.includeEmptyValue();
        AccessControlled contextToCheck = context == null ? Jenkins.get() : context;
        if (!contextToCheck.hasPermission(CredentialsProvider.VIEW)) {
            return result;
        }
        Authentication authentication = context instanceof Queue.Task task
                ? task.getDefaultAuthentication2()
                : ACL.SYSTEM2;

        serverURL = BitbucketEndpointConfiguration.get()
                .findEndpoint(serverURL)
                .orElse(BitbucketEndpointConfiguration.get().getDefaultEndpoint())
                .getServerUrl();

        result.includeMatchingAs(
                authentication,
                context,
                StandardCredentials.class,
                URIRequirementBuilder.fromUri(serverURL).build(),
                AuthenticationTokens.matcher(BitbucketAuthenticator.authenticationContext(serverURL))
        );
        return result;
    }

    public static FormValidation checkCredentialsId(@CheckForNull SCMSourceOwner context, String value, String serverURL) {
        if (StringUtils.isNotBlank(value)) {
            serverURL = BitbucketEndpointConfiguration.get()
                    .findEndpoint(serverURL)
                    .orElse(BitbucketEndpointConfiguration.get().getDefaultEndpoint())
                    .getServerUrl();

            AccessControlled contextToCheck = context == null ? Jenkins.get() : context;
            contextToCheck.checkPermission(CredentialsProvider.VIEW);

            Authentication authentication = context instanceof Queue.Task task
                    ? task.getDefaultAuthentication2()
                    : ACL.SYSTEM2;

            if (CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentialsInItem(
                            StandardCertificateCredentials.class,
                            context,
                            authentication,
                            URIRequirementBuilder.fromUri(serverURL).build()),
                    CredentialsMatchers.allOf(
                            CredentialsMatchers.withId(value),
                            AuthenticationTokens.matcher(BitbucketAuthenticator.authenticationContext(serverURL))
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
