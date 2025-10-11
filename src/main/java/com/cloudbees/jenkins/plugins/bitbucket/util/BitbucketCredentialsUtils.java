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
        result.includeMatchingAs(
                authentication,
                context,
                StandardCredentials.class,
                domainRequirements,
                matcher);
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

        StandardListBoxModel result = new StandardListBoxModel();
        result.includeMatchingAs(
                authentication,
                context,
                StandardCredentials.class,
                domainRequirements,
                matcher);
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
