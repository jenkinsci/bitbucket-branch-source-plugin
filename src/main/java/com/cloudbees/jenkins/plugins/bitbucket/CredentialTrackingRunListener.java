package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.listeners.RunListener;

/**
 * Tracks the usage of credentials
 */
@Extension
public class CredentialTrackingRunListener extends RunListener<Run<?, ?>> {
    @Override
    public void onInitialize(Run<?, ?> run) {
        final BitbucketSCMSource source = BitbucketSCMSource.findForRun(run);

        if (source == null) {
            return;
        }

        final boolean usesSshCheckout = source.getTraits().stream().anyMatch(scmSourceTrait -> scmSourceTrait instanceof SSHCheckoutTrait);

        if (!usesSshCheckout) {
            CredentialsProvider.track(run, source.credentials());
        }
    }
}
