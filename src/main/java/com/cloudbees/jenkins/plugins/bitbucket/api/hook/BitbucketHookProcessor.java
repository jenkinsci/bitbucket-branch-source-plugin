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
package com.cloudbees.jenkins.plugins.bitbucket.api.hook;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.ExtensionPoint;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.util.SystemProperties;
import org.apache.commons.collections4.MultiValuedMap;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public interface BitbucketHookProcessor extends ExtensionPoint {
    static final String SCAN_ON_EMPTY_CHANGES_PROPERTY_NAME = "bitbucket.hooks.processor.scanOnEmptyChanges";

    boolean canHandle(@NonNull Map<String, String> headers, @NonNull MultiValuedMap<String, String> parameters);

    @NonNull
    String getServerURL(@NonNull Map<String, String> headers, @NonNull MultiValuedMap<String, String> parameters);

    @NonNull
    String getEventType(Map<String, String> headers, MultiValuedMap<String, String> parameters);

    void verifySignature(@NonNull Map<String, String> headers,
                         @NonNull String body,
                         @NonNull BitbucketEndpoint endpoint) throws BitbucketHookProcessorException;

    default boolean reindexOnEmptyChanges() {
        return SystemProperties.getBoolean(SCAN_ON_EMPTY_CHANGES_PROPERTY_NAME, false);
    }

    /**
     * See <a href="https://confluence.atlassian.com/bitbucket/event-payloads-740262817.html">Event
     * Payloads</a> for more information about the payload parameter format.
     *
     * @param eventType the type of hook event.
     * @param payload the hook payload
     * @param origin the origin of the event.
     * @param endpoint configured in the Jenkins global page
     */
    void process(@NonNull String eventType, @NonNull String payload, @Nullable String origin, @NonNull BitbucketEndpoint endpoint);

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
