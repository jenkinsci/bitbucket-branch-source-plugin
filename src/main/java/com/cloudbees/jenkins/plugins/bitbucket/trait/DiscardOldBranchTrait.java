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
package com.cloudbees.jenkins.plugins.bitbucket.trait;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketGitSCMBuilder;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSourceRequest;
import com.cloudbees.jenkins.plugins.bitbucket.Messages;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBranch;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.FormValidation;
import java.io.IOException;
import java.time.LocalDate;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMHeadFilter;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Discard all branches with head commit older than the configured days.
 *
 * @author Nikolas Falco
 * @since 933.3.0
 * @see <a href="https://github.com/nfalco79/bitbucket-trait-plugin/blob/master/src/main/java/com/github/nfalco79/jenkins/plugins/bitbucket/trait/DiscardOldBranchTrait.java">Original source</a>
 */
public class DiscardOldBranchTrait extends SCMSourceTrait {

    private int keepForDays = 1;

    @DataBoundConstructor
    public DiscardOldBranchTrait(@CheckForNull int keepForDays) {
        this.keepForDays = keepForDays;
    }

    public int getKeepForDays() {
        return keepForDays;
    }

    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        context.withFilter(new ExcludeOldSCMHeadBranch());
    }

    public final class ExcludeOldSCMHeadBranch extends SCMHeadFilter {
        @Override
        public boolean isExcluded(SCMSourceRequest request, SCMHead head) throws IOException, InterruptedException {
            if (keepForDays > 0) {
                BitbucketSCMSourceRequest bbRequest = (BitbucketSCMSourceRequest) request;
                String branchName = head.getName();
                if (head instanceof PullRequestSCMHead prHead) {
                    // getName return the PR-<id>, not the branch name
                    branchName = prHead.getBranchName();
                }

                for (BitbucketBranch branch : bbRequest.getBranches()) {
                    if (branchName.equals(branch.getName())) {
                        LocalDate commitDate = asLocalDate(branch.getDateMillis());
                        LocalDate expiryDate = LocalDate.now().minusDays(keepForDays);
                        return commitDate.isBefore(expiryDate);
                    }
                }
            }
            return false;
        }

        @NonNull
        private LocalDate asLocalDate(@NonNull long milliseconds) {
            return new java.sql.Date(milliseconds).toLocalDate();
        }
    }

    /**
     * Our descriptor.
     */
    @Symbol("bitbucketDiscardOldBranch")
    @Extension
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        public FormValidation doCheckKeepForDays(@QueryParameter final int keepForDays) {
            if (keepForDays <= 0) {
                return FormValidation.error("Invalid value. Days must be greater than 0");
            }
            return FormValidation.ok();
        }

        @Override
        public String getDisplayName() {
            return Messages.DiscardOldBranchTrait_displayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicableToBuilder(@SuppressWarnings("rawtypes") @NonNull Class<? extends SCMBuilder> builderClass) {
            return BitbucketGitSCMBuilder.class.isAssignableFrom(builderClass);
        }
    }

}
