/*
 * The MIT License
 *
 * Copyright (c) 2025, Madis Parn
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
import com.cloudbees.jenkins.plugins.bitbucket.Messages;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.FormValidation;
import java.util.concurrent.TimeUnit;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.mixin.TagSCMHead;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMHeadPrefilter;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Discard all tags with creation date older than the configured days.
 *
 * @author Madis Parn
 * @since 936.0.0
 */
public class DiscardOldTagTrait extends SCMSourceTrait {

    private int keepForDays;

    @DataBoundConstructor
    public DiscardOldTagTrait(@CheckForNull int keepForDays) {
        this.keepForDays = keepForDays;
    }

    public int getKeepForDays() {
        return keepForDays;
    }

    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        if (keepForDays > 0) {
            context.withPrefilter(new ExcludeOldSCMTag(keepForDays));
        }
    }

    public static final class ExcludeOldSCMTag extends SCMHeadPrefilter {

        private final long expiryMs;

        public ExcludeOldSCMTag(int keepForDays) {
            this.expiryMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(keepForDays);
        }

        @Override
        public boolean isExcluded(@NonNull SCMSource source, @NonNull SCMHead head) {
            if (!(head instanceof TagSCMHead tagHead)) {
                return false;
            }
            long tagTimestamp = tagHead.getTimestamp();
            return tagTimestamp > 0 && tagTimestamp < expiryMs;
        }

    }

    @Symbol("bitbucketDiscardOldTag")
    @Extension
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        public FormValidation doCheckKeepForDays(@QueryParameter int keepForDays) {
            if (keepForDays <= 0) {
                return FormValidation.error("Invalid value. Days must be greater than 0");
            }
            return FormValidation.ok();
        }

        @Override
        public String getDisplayName() {
            return Messages.DiscardOldTagTrait_displayName();
        }

        @Override
        public boolean isApplicableToBuilder(@NonNull Class<? extends SCMBuilder> builderClass) {
            return BitbucketGitSCMBuilder.class.isAssignableFrom(builderClass);
        }
    }

}
