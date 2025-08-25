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
package com.cloudbees.jenkins.plugins.bitbucket.api.webhook;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticatedClient;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMTrait;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * The implementation is in charge to apply a specific
 * {@link BitbucketWebhookConfiguration} to the Bitbucket registering the
 * webhook commit
 *
 * @author Nikolas Falco
 * @since 937.0.0
 */
@Restricted(Beta.class)
public interface BitbucketWebhookManager extends ExtensionPoint {

    /**
     * The callback URL where send event payload.
     * <p>
     * The method is called with the URL of the default receiver and processed
     * using an appropriate {@link BitbucketWebhookProcessor}. The
     * implementation could decide to ignore given URL and use an own servlet
     * endpoint to process own events.
     *
     * @param callbackURL used to send webhook payload.
     * @param endpoint this webhook is registered for, it could be used to
     *        retrieve additional information to compose the callbackURL
     */
    void setCallbackURL(@NonNull String callbackURL, @NonNull BitbucketEndpoint endpoint);

    /**
     * The configuration that returned this implementation class.
     *
     * @param configuration to apply
     */
    void apply(BitbucketWebhookConfiguration configuration);

    /**
     * A list of traits class that this manager supports to obtain additional
     * configuration options.
     *
     * @return a list of {@link SCMSourceTrait} classes.
     */
    default Collection<Class<? extends SCMSourceTrait>> supportedTraits() {
        return Collections.emptyList();
    }

    /**
     * Trait instance associate to a {@link SCMSource} where gather extra
     * configuration options.
     * <p>
     * Each {@link BitbucketWebhookConfiguration} that would obtain additional
     * configuration options per project must provide an own specific trait
     * implementation.
     *
     * @param trait to apply
     */
    default void apply(SCMSourceTrait trait) {}

    /**
     * Convenient method to apply only supported traits to this customiser.
     *
     * @param traits to apply if supported too
     */
    default void withTraits(List<SCMSourceTrait> traits) {
        supportedTraits().forEach(traitClass -> {
            SCMSourceTrait trait = SCMTrait.find(traits, traitClass);
            if (trait != null) {
                apply(trait);
            }
        });
    }

    /**
     * Returns the list of all registered webhook at this repository related to
     * this Jenkins.
     *
     * @param client authenticated to communicate with Bitbucket
     * @return a list of registered {@link BitbucketWebHook}.
     * @throws IOException in case of communication issue with Bitbucket
     */
    Collection<BitbucketWebHook> read(@NonNull BitbucketAuthenticatedClient client) throws IOException;

    /**
     * Save a webhook (updating or creating a new one) using the actual
     * configuration.
     *
     * @param client authenticated to communicate with Bitbucket
     * @throws IOException in case of communication issue with Bitbucket
     */
    void register(@NonNull BitbucketAuthenticatedClient client) throws IOException;

    /**
     * Remove the webhook from the Bitbucket repository with the given
     * identifier.
     *
     * @param webhookId webhook identifier to delete.
     * @param client authenticated to communicate with Bitbucket
     * @throws IOException in case of communication issue with Bitbucket
     */
    void remove(@NonNull String webhookId, @NonNull BitbucketAuthenticatedClient client) throws IOException;
}
