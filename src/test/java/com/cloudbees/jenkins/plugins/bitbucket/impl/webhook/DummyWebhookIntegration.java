package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookClient;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookIntegration;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import jenkins.scm.api.trait.SCMSourceTrait;

public class DummyWebhookIntegration implements BitbucketWebhookIntegration {

    private String repositoryOwner;
    private String repositoryName;
    private String serverURL;
    private String callbackURL;


    public String getRepositoryOwner() {
        return repositoryOwner;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public String getServerURL() {
        return serverURL;
    }

    public String getCallbackURL() {
        return callbackURL;
    }

    @Override
    public void setRepositoryOwner(String repositoryOwner) {
        this.repositoryOwner = repositoryOwner;
    }

    @Override
    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    @Override
    public void setServerURL(String serverURL) {
        this.serverURL = serverURL;
    }

    @Override
    public void setCallbackURL(String callbackURL) {
        this.callbackURL = callbackURL;
    }

    @Override
    public Collection<Class<? extends SCMSourceTrait>> supportedTraits() {
        return Collections.emptyList();
    }

    @Override
    public void apply(SCMSourceTrait trait) {
    }

    @Override
    public Collection<BitbucketWebHook> retrieve(BitbucketWebhookClient client) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public void register(BitbucketWebhookClient client) throws IOException {
    }

    @Override
    public void remove(BitbucketWebHook payload, BitbucketWebhookClient client) throws IOException {
    }

}
