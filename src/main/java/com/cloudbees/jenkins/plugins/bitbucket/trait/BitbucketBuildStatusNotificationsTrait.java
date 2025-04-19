/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSourceContext;
import com.cloudbees.jenkins.plugins.bitbucket.Messages;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * A {@link SCMSourceTrait} for {@link BitbucketSCMSource} that sets how notifications
 * are sent to Bitbucket.
 *
 * @since 2.10.0
 */
public class BitbucketBuildStatusNotificationsTrait extends SCMSourceTrait {

    private boolean sendSuccessNotificationForUnstableBuild;
    private boolean sendStoppedNotificationForAbortBuild;
    private boolean disableNotificationForNotBuildJobs;
    private boolean useReadableNotificationIds = false;
    // seems that this attribute has been moved out to plugin skip-notifications-trait-plugin
    @SuppressFBWarnings("UUF_UNUSED_FIELD")
    private transient boolean disableNotifications;

    /**
     * Constructor.
     *
     */
    @DataBoundConstructor
    public BitbucketBuildStatusNotificationsTrait() {
        /*
         * empty constructor
         */
    }

    @DataBoundSetter
    public void setSendSuccessNotificationForUnstableBuild(boolean isSendSuccess) {
        sendSuccessNotificationForUnstableBuild = isSendSuccess;
    }

    /**
     * Should unstable builds be communicated as success to Bitbucket.
     *
     * @return if unstable builds will be communicated as successful
     */
    public boolean getSendSuccessNotificationForUnstableBuild() {
        return this.sendSuccessNotificationForUnstableBuild;
    }

    /**
     * Set if aborted builds will be communicated as stopped.
     *
     * @param sendStop comunicate Stop/Cancelled build status to Bitbucket for
     *        aborted build.
     */
    @DataBoundSetter
    public void setSendStoppedNotificationForAbortBuild(boolean sendStop) {
        sendStoppedNotificationForAbortBuild = sendStop;
    }

    /**
     * Return if aborted builds will be communicated as stopped.
     *
     * @return if will be communicated to Bitbucket as Stopped/Cancelled build
     *         failed otherwise.
     */
    public boolean getSendStoppedNotificationForAbortBuild() {
        return this.sendStoppedNotificationForAbortBuild;
    }

    @DataBoundSetter
    public void setDisableNotificationForNotBuildJobs(boolean isNotificationDisabled) {
        disableNotificationForNotBuildJobs = isNotificationDisabled;
    }

    /**
     * Should not build jobs be communicated as stopped.
     *
     * @return if will be communicated to Bitbucket as Stopped/Cancelled build
     *         failed otherwise.
     */
    public boolean getDisableNotificationForNotBuildJobs() {
        return this.disableNotificationForNotBuildJobs;
    }

    /**
     * Use a readable id as key for the build notification status.
     *
     * @return if will not hash the generated key of the build notification
     *         status.
     */
    public boolean getUseReadableNotificationIds() {
        return useReadableNotificationIds;
    }

    @DataBoundSetter
    public void setUseReadableNotificationIds(boolean useReadableNotificationIds) {
        this.useReadableNotificationIds = useReadableNotificationIds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        if (context instanceof BitbucketSCMSourceContext scmContext) {
            scmContext.withSendStopNotificationForNotBuildJobs(getDisableNotificationForNotBuildJobs())
                .withSendSuccessNotificationForUnstableBuild(getSendSuccessNotificationForUnstableBuild())
                .withSendStoppedNotificationForAbortBuild(getSendStoppedNotificationForAbortBuild())
                .withUseReadableNotificationIds(getUseReadableNotificationIds());
        }
    }

    /**
     * Our constructor.
     */
    @Symbol("bitbucketBuildStatusNotifications")
    @Extension
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.BitbucketBuildStatusNotificationsTrait_displayName();
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
    }
}
