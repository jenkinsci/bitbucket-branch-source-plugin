package com.cloudbees.jenkins.plugins.bitbucket.hooks;

import hudson.model.Cause;
import org.kohsuke.stapler.export.Exported;

public class WebhookCause extends Cause {
    private String payload;

    public WebhookCause(String payload) {
        this.payload = payload;
    }

    @Exported(
            visibility = 3
    )
    public String getPayload() {
        return payload;
    }

    public String getShortDescription() {
        return "Bitbucket Webhook Cause";
    }
}
