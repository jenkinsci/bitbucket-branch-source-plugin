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
package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryProtocol;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCMDescriptor;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * A {@link SCMSourceTrait} for {@link BitbucketSCMSource} that causes the {@link GitSCM}
 * checkout to be performed using a SSH private key rather than the Bitbucket username password credentials used
 * for scanning / indexing.
 *
 * @since 2.2.0
 */
public class SSHCheckoutTrait extends SCMSourceTrait {

    /**
     * Credentials for actual clone; may be SSH private key.
     */
    @CheckForNull
    private final String credentialsId;

    /**
     * Constructor.
     *
     * @param credentialsId the {@link SSHUserPrivateKey#getId()} of the credentials to use or
     *                      {@link BitbucketSCMSource.DescriptorImpl#ANONYMOUS} to defer to the agent configured
     *                      credentials (typically anonymous but not always)
     */
    @DataBoundConstructor
    public SSHCheckoutTrait(@CheckForNull String credentialsId) {
        if (BitbucketSCMSource.DescriptorImpl.ANONYMOUS.equals(credentialsId)) {
            // legacy migration of "magic" credential ID.
            this.credentialsId = null;
        } else {
            this.credentialsId = Util.fixEmpty(credentialsId);
        }
    }

    /**
     * Returns the configured credentials id.
     *
     * @return the configured credentials id or {@code null} to use the build agent's key.
     */
    @CheckForNull
    public final String getCredentialsId() {
        return credentialsId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorateBuilder(SCMBuilder<?, ?> builder) {
        if (builder instanceof GitSCMBuilder) {
            ((BitbucketGitSCMBuilder) builder)
                .withCredentials(credentialsId, BitbucketRepositoryProtocol.SSH);
        }
    }

    /**
     * Our descriptor.
     */
    @Symbol("bitbucketSshCheckout")
    @Extension
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.SSHCheckoutTrait_displayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<? extends SCMSourceContext> getContextClass() {
            return BitbucketSCMSourceContext.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<? extends SCMSource> getSourceClass() {
            return BitbucketSCMSource.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicableToBuilder(@NonNull Class<? extends SCMBuilder> builderClass) {
            return BitbucketGitSCMBuilder.class.isAssignableFrom(builderClass);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicableToSCM(@NonNull SCMDescriptor<?> scm) {
            return scm instanceof GitSCM.DescriptorImpl;
        }

        /**
         * Form completion.
         *
         * @param context       the context.
         * @param serverUrl     the server url.
         * @param credentialsId the current selection.
         * @return the form items.
         */
        @Restricted(NoExternalUse.class)
        @SuppressWarnings("unused") // stapler form binding
        public ListBoxModel doFillCredentialsIdItems(@CheckForNull @AncestorInPath Item context,
                                                     @QueryParameter String serverUrl,
                                                     @QueryParameter String credentialsId) {
            if (context == null
                    ? !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
                    : !context.hasPermission(Item.EXTENDED_READ)) {
                return new StandardListBoxModel().includeCurrentValue(credentialsId);
            }
            StandardListBoxModel result = new StandardListBoxModel();
            result.add(Messages.SSHCheckoutTrait_useAgentKey(), "");
            return result.includeMatchingAs(
                    context instanceof Queue.Task
                            ? ((Queue.Task) context).getDefaultAuthentication()
                            : ACL.SYSTEM,
                    context,
                    StandardUsernameCredentials.class,
                    URIRequirementBuilder.fromUri(serverUrl).build(),
                    CredentialsMatchers.instanceOf(SSHUserPrivateKey.class)
            );
        }

        /**
         * Validation for checkout credentials.
         *
         * @param context   the context.
         * @param serverUrl the server url.
         * @param value     the current selection.
         * @return the validation results
         */
        @Restricted(NoExternalUse.class)
        @SuppressWarnings("unused") // stapler form binding
        public FormValidation doCheckCredentialsId(@CheckForNull @AncestorInPath Item context,
                                                   @QueryParameter String serverUrl,
                                                   @QueryParameter String value) {
            if (context == null
                    ? !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
                    : !context.hasPermission(Item.EXTENDED_READ)) {
                return FormValidation.ok();
            }
            if (StringUtils.isBlank(value)) {
                // use agent key
                return FormValidation.ok();
            }
            if (CredentialsMatchers.firstOrNull(CredentialsProvider.lookupCredentials(
                    SSHUserPrivateKey.class,
                    context,
                    context instanceof Queue.Task ? ((Queue.Task) context).getDefaultAuthentication() : ACL.SYSTEM,
                    URIRequirementBuilder.fromUri(serverUrl).build()),
                    CredentialsMatchers.withId(value)) != null) {
                return FormValidation.ok();
            }
            if (CredentialsMatchers.firstOrNull(CredentialsProvider.lookupCredentials(
                    StandardUsernameCredentials.class,
                    context,
                    context instanceof Queue.Task ? ((Queue.Task) context).getDefaultAuthentication() : ACL.SYSTEM,
                    URIRequirementBuilder.fromUri(serverUrl).build()),
                    CredentialsMatchers.withId(value)) != null) {
                return FormValidation.error(Messages.SSHCheckoutTrait_incompatibleCredentials());
            }
            return FormValidation.warning(Messages.SSHCheckoutTrait_missingCredentials());
        }
    }
}
