package com.cloudbees.jenkins.plugins.bitbucket.api.webhook;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import java.io.IOException;
import java.util.Collection;
import jenkins.scm.api.trait.SCMSourceTrait;

public interface BitbucketWebhookIntegration extends ExtensionPoint {

    void setRepositoryOwner(@NonNull String repositoryOwner);
    void setRepositoryName(@NonNull String repositoryName);
    void setServerURL(@NonNull String serverURL);
    void setCallbackURL(@NonNull String callbackURL);

    Collection<Class<? extends SCMSourceTrait>> supportedTraits();
    void apply(SCMSourceTrait trait);

    Collection<BitbucketWebHook> retrieve(@NonNull BitbucketWebhookClient client) throws IOException;
    void register(@NonNull BitbucketWebhookClient client) throws IOException;
    void remove(@NonNull BitbucketWebHook payload, @NonNull BitbucketWebhookClient client) throws IOException;
}
