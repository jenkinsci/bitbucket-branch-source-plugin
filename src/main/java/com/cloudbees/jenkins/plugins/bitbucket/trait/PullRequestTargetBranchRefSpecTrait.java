/*
 * The MIT License
 *
 * Copyright (c) 2025, Falco Nikolas
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

import com.cloudbees.jenkins.plugins.bitbucket.Messages;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMHead;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.GitSCMSourceContext;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Exposes the target branch of pull request as ref specs of a
 * {@link AbstractGitSCMSource} as a {@link SCMSourceTrait}.
 *
 * @author Nikolas Falco
 * @since 936.2.0
 * @see <a href="https://github.com/nfalco79/bitbucket-trait-plugin/blob/master/src/main/java/com/github/nfalco79/jenkins/plugins/bitbucket/trait/PullRequestTargetBranchTrait.java">Original source</a>
 */
public class PullRequestTargetBranchRefSpecTrait extends SCMSourceTrait {

    /**
     * Constructor for stapler.
     */
    @DataBoundConstructor
    public PullRequestTargetBranchRefSpecTrait() {
        // for stapler
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorateBuilder(SCMBuilder<?, ?> builder) {
        if (builder instanceof GitSCMBuilder gitBuilder) {
            SCMHead head = builder.head();
            if (head instanceof PullRequestSCMHead prHead) {
                String targetBranch = prHead.getTarget().getName();
                gitBuilder.withRefSpec("+refs/heads/" + targetBranch + ":refs/remotes/@{remote}/" + targetBranch);
            }
        }
    }

    /**
     * Our descriptor.
     */
    @Symbol("bitbucketPRTargetBranchRefSpec")
    @Extension
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.PullRequestTargetBranchRefSpecTrait_displayName();
        }

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("rawtypes")
        @Override
        public Class<? extends SCMBuilder> getBuilderClass() {
            return GitSCMBuilder.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<? extends SCM> getScmClass() {
            return GitSCM.class;
        }

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("rawtypes")
        @Override
        public Class<? extends SCMSourceContext> getContextClass() {
            return GitSCMSourceContext.class;
        }

    }
}
