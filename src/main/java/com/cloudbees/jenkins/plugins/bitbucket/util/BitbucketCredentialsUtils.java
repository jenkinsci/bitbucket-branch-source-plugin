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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpointProvider;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.credentials.BitbucketOAuthAuthenticatorSource;
import com.cloudbees.jenkins.plugins.bitbucket.impl.credentials.BitbucketUserAPITokenAuthenticatorSource;
import com.cloudbees.jenkins.plugins.bitbucket.impl.credentials.BitbucketUsernamePasswordAuthenticatorSource;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Queue;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import jenkins.authentication.tokens.api.AuthenticationTokenContext;
import jenkins.authentication.tokens.api.AuthenticationTokenSource;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSourceOwner;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;

/**
 * Utility class for common code accessing credentials.
 */
public class BitbucketCredentialsUtils {
    @SuppressWarnings("serial")
    private static class TimeBoxedCredentialsMatcher implements CredentialsMatcher {
        private static final Logger logger = Logger.getLogger(TimeBoxedCredentialsMatcher.class.getName());
        private CredentialsMatcher delegate;

        public TimeBoxedCredentialsMatcher(CredentialsMatcher matcher) {
            this.delegate = matcher;
        }

        @Override
        public boolean matches(Credentials item) {
            TimeLimiter timeLimiter = SimpleTimeLimiter.create(Executors.newSingleThreadExecutor());

            try {
                // JENKINS-75225
                return timeLimiter.callWithTimeout(() -> delegate.matches(item), Duration.ofMillis(200));
            } catch (TimeoutException e) {
                // takes long maybe credentials are not stored in Jenkins and requires some remote call than will fail
                logger.fine(() -> "Credentials " + item.getDescriptor() + " takes too long to get password, maybe is performing remote call");
                return false;
            } catch (Exception e) {
                // JENKINS-75184
                return false;
            }
        }
    }

    /**
     * Matcher that excludes AWS credentials from Bitbucket credentials list.
     * This improves performance when there are many AWS credentials in the system.
     */
    @SuppressWarnings("serial")
    private static class AwsCredentialsExclusionMatcher implements CredentialsMatcher {
        private static final Logger logger = Logger.getLogger(AwsCredentialsExclusionMatcher.class.getName());

        @Override
        public boolean matches(Credentials item) {
            if (item == null) {
                return false;
            }
            try {
                String className = item.getClass().getName().toLowerCase();
                String descriptorId = "";
                try {
                    if (item.getDescriptor() != null) {
                        descriptorId = item.getDescriptor().getId().toLowerCase();
                    }
                } catch (AssertionError | NullPointerException e) {
                    // Some credentials may not have a descriptor (e.g., AmazonECSRegistryCredential)
                    // In this case, we'll only check the class name
                    logger.fine(() -> "Credential " + className + " has no descriptor, checking class name only");
                }

                // Exclude AWS credentials to improve performance
                if (className.contains("aws") || className.contains("amazon") ||
                    descriptorId.contains("aws") || descriptorId.contains("amazon")) {
                    logger.fine(() -> "Excluding AWS credential: " + className);
                    return false;
                }
                return true;
            } catch (Exception e) {
                // If any error occurs, allow the credential to pass through
                // This prevents breaking the credentials list loading
                logger.warning(() -> "Error checking credential: " + e.getMessage());
                return true;
            }
        }
    }

    private BitbucketCredentialsUtils() {
        throw new IllegalAccessError("Utility class");
    }

    @CheckForNull
    public static <T extends StandardCredentials> T lookupCredentials(@CheckForNull Item item,
                                                                      @CheckForNull String serverURL,
                                                                      @CheckForNull String credentialsId,
                                                                      @NonNull Class<T> type) {
        if (StringUtils.isNotBlank(credentialsId)) {
            Authentication authentication = item instanceof Queue.Task task
                    ? task.getDefaultAuthentication2()
                    : ACL.SYSTEM2;
            List<DomainRequirement> domainRequirements = URIRequirementBuilder.fromUri(serverURL).build();

            return CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentialsInItem(
                            type,
                            item,
                            authentication,
                            domainRequirements
                    ),
                    CredentialsMatchers.withId(credentialsId));
        }
        return null;
    }

    @CheckForNull
    public static <T extends StandardCredentials> T lookupCredentials(@CheckForNull ItemGroup<?> itemGroup,
                                                                      @CheckForNull String serverURL,
                                                                      @CheckForNull String credentialsId,
                                                                      @NonNull Class<T> type) {
        if (StringUtils.isNotBlank(credentialsId)) {
            List<DomainRequirement> domainRequirements = URIRequirementBuilder.fromUri(serverURL).build();

            return CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentialsInItemGroup(
                            type,
                            itemGroup,
                            null,
                            domainRequirements),
                    CredentialsMatchers.withId(credentialsId));
        }
        return null;
    }

    public static FormValidation checkCredentialsId(@CheckForNull SCMSourceOwner context,
                                                    @CheckForNull String serverURL,
                                                    @CheckForNull String credentialsId) {
        if (StringUtils.isNotBlank(credentialsId)) {
            serverURL = BitbucketEndpointProvider.lookupEndpoint(serverURL)
                    .orElse(BitbucketEndpointConfiguration.get().getDefaultEndpoint())
                    .getServerURL();

            AccessControlled contextToCheck = context == null ? Jenkins.get() : context;
            contextToCheck.checkPermission(CredentialsProvider.VIEW);

            Authentication authentication = context instanceof Queue.Task task
                    ? task.getDefaultAuthentication2()
                    : ACL.SYSTEM2;
            List<DomainRequirement> domainRequirements = URIRequirementBuilder.fromUri(serverURL).build();

            StandardCertificateCredentials certificateCredentials = CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentialsInItem(
                            StandardCertificateCredentials.class,
                            context,
                            authentication,
                            domainRequirements),
                    CredentialsMatchers.withId(credentialsId));
            if (certificateCredentials != null) {
                return FormValidation.warning("A certificate was selected. You will likely need to configure Checkout over SSH.");
            }
            return FormValidation.ok();
        } else {
            return FormValidation.warning("Credentials are required for build notifications");
        }
    }

    public static ListBoxModel listCredentials(@NonNull Item context,
                                               @CheckForNull String serverURL,
                                               @CheckForNull String credentialsId) {
        StandardListBoxModel result = new StandardListBoxModel();
        result.includeEmptyValue();
        if (!context.hasPermission(CredentialsProvider.VIEW)) {
            return result;
        }
        Authentication authentication = context instanceof Queue.Task task
                ? task.getDefaultAuthentication2()
                : ACL.SYSTEM2;

        serverURL = BitbucketEndpointProvider.lookupEndpoint(serverURL)
                .orElse(BitbucketEndpointConfiguration.get().getDefaultEndpoint())
                .getServerURL();

        List<DomainRequirement> domainRequirements = URIRequirementBuilder.fromUri(serverURL).build();
        CredentialsMatcher matcher = /*AuthenticationTokens.*/matcher(BitbucketAuthenticator.authenticationContext(serverURL));
        // Exclude AWS credentials to improve performance when there are many AWS credentials
        // Apply AWS exclusion filter FIRST, then apply Bitbucket-specific matcher
        CredentialsMatcher combinedMatcher = CredentialsMatchers.allOf(
                new AwsCredentialsExclusionMatcher(),
                matcher
        );
        result.includeMatchingAs(
                authentication,
                context,
                StandardCredentials.class,
                domainRequirements,
                combinedMatcher);
        if (credentialsId != null) {
            result.includeCurrentValue(credentialsId);
        }
        return result;
    }

    public static ListBoxModel listCredentials(@NonNull ItemGroup<?> context,
                                               @CheckForNull String serverURL,
                                               @CheckForNull String credentialsId) {

        List<DomainRequirement> domainRequirements = URIRequirementBuilder.fromUri(serverURL).build();
        Authentication authentication = ACL.SYSTEM2;
        CredentialsMatcher matcher = /*AuthenticationTokens.*/matcher(BitbucketAuthenticator.authenticationContext(serverURL));
        // Exclude AWS credentials to improve performance when there are many AWS credentials
        // Apply AWS exclusion filter FIRST, then apply Bitbucket-specific matcher
        CredentialsMatcher combinedMatcher = CredentialsMatchers.allOf(
                new AwsCredentialsExclusionMatcher(),
                matcher
        );

        StandardListBoxModel result = new StandardListBoxModel();
        result.includeMatchingAs(
                authentication,
                context,
                StandardCredentials.class,
                domainRequirements,
                combinedMatcher);
        if (credentialsId != null) {
            result.includeCurrentValue(credentialsId);
        }
        return result;
    }

    // copy of AuthenticationTokens#matcher
    private static <T> CredentialsMatcher matcher(AuthenticationTokenContext<T> context) {
        List<CredentialsMatcher> matchers = new ArrayList<>();
        for (AuthenticationTokenSource<?, ?> source : ExtensionList.lookup(AuthenticationTokenSource.class)) {
            if (source.fits(context)) {
                CredentialsMatcher matcher = source.matcher();
                if (source instanceof BitbucketUsernamePasswordAuthenticatorSource
                        || source instanceof BitbucketOAuthAuthenticatorSource
                        || source instanceof BitbucketUserAPITokenAuthenticatorSource) {
                    matcher = new TimeBoxedCredentialsMatcher(matcher);
                }
                matchers.add(matcher);
            }
        }
        return matchers.isEmpty()
                ? CredentialsMatchers.never()
                : CredentialsMatchers.anyOf(matchers.toArray(new CredentialsMatcher[matchers.size()]));
    }
}
