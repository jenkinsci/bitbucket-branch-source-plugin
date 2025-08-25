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

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.util.SystemProperties;
import org.apache.commons.collections4.MultiValuedMap;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Implementations of this extension point must provide new behaviours to
 * accommodate custom event payloads from webhooks sent from Bitbucket Cloud,
 * Bitbucket Data Center, or installed plugins.
 * <p>
 * There cannot be multiple processors processing the same incoming webhook for
 * a specific event installed on the system, meaning the processor must fit to
 * the incoming request as much as possible or the hook will be rejected in case
 * of multiple matches.
 *
 * @since 937.0.0
 */
@Restricted(Beta.class)
public interface BitbucketWebhookProcessor extends ExtensionPoint {
    static final String SCAN_ON_EMPTY_CHANGES_PROPERTY_NAME = "bitbucket.hooks.processor.scanOnEmptyChanges";

    /**
     * Called by first for this processor that must respond if is able to handle
     * this specific request
     *
     * @param headers request
     * @param parameters request
     * @return {@code true} if this processor is able to handle this hook
     *         request, {@code false} otherwise.
     */
    boolean canHandle(@NonNull Map<String, String> headers, @NonNull MultiValuedMap<String, String> parameters);

    /**
     * Extracts the server URL from where this request coming from, the URL must
     * match one of the configured {@link BitbucketEndpoint}s.
     *
     * @param headers request
     * @param parameters request
     * @return the URL of the server from where this request has been sent.
     */
    @NonNull
    String getServerURL(@NonNull Map<String, String> headers, @NonNull MultiValuedMap<String, String> parameters);

    /**
     * Extracts the event type that represent the payload in the request.
     *
     * @param headers request
     * @param parameters request
     * @return the event type key.
     */
    @NonNull
    String getEventType(Map<String, String> headers, MultiValuedMap<String, String> parameters);

    /**
     * Returns a context for a given request used when process the payload.
     *
     * @param request hook
     * @return a map of information extracted by the given request to be used in
     *         the {@link #process(String, String, Map, BitbucketEndpoint)}
     *         method.
     */
    @NonNull
    default Map<String, Object> buildHookContext(@NonNull HttpServletRequest request) {
        return Map.of("origin", SCMEvent.originOf(request));
    }

    /**
     * The implementation must verify if the incoming request is secured or not
     * eventually gather some settings from the given {@link BitbucketEndpoint}
     * configuration.
     *
     * @param headers request
     * @param payload request
     * @param endpoint configured for the given
     *        {@link #getServerURL(Map, MultiValuedMap)}
     * @throws BitbucketWebhookProcessorException when signature verification fails
     */
    void verifyPayload(@NonNull Map<String, String> headers,
                       @NonNull String payload,
                       @NonNull BitbucketEndpoint endpoint) throws BitbucketWebhookProcessorException;

    /**
     * Settings that will trigger a re-index of the multibranch
     * project/organization folder when the request does not ship any source
     * changes.
     *
     * @return if should perform a reindex of the project or not.
     */
    default boolean reindexOnEmptyChanges() {
        return SystemProperties.getBoolean(SCAN_ON_EMPTY_CHANGES_PROPERTY_NAME, false);
    }

    /**
     * See <a href="https://confluence.atlassian.com/bitbucket/event-payloads-740262817.html">Event
     * Payloads</a> for more information about the payload parameter format.
     *
     * @param eventType the type of hook event.
     * @param payload the hook payload
     * @param context build from incoming request
     * @param endpoint configured in the Jenkins global page
     */
    void process(@NonNull String eventType, @NonNull String payload, @NonNull Map<String, Object> context, @NonNull BitbucketEndpoint endpoint);

    /**
     * Implementations have to call this method when want propagate an
     * {@link SCMHeadEvent} to the scm-api.
     *
     * @param event the to fire
     * @param delaySeconds a delay in seconds to wait before propagate the
     *        event. If the given value is less than 0 than default will be
     *        used.
     */
    default void notifyEvent(SCMHeadEvent<?> event, int delaySeconds) {
        if (delaySeconds == 0) {
            SCMHeadEvent.fireNow(event);
        } else {
            SCMHeadEvent.fireLater(event, delaySeconds > 0 ? delaySeconds : BitbucketSCMSource.getEventDelaySeconds(), TimeUnit.SECONDS);
        }
    }
}
