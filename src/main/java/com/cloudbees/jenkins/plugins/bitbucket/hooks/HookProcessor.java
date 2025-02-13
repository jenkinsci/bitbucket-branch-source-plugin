/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket.hooks;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import hudson.security.ACL;
import hudson.security.ACLContext;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.SCMSourceOwners;
import jenkins.util.SystemProperties;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Abstract hook processor.
 *
 * Add new hook processors by extending this class and implement {@link #process(HookEventType, String, BitbucketType, String)},
 * extract details from the hook payload and then fire an {@link jenkins.scm.api.SCMEvent} to dispatch it to the SCM API.
 */
public abstract class HookProcessor {

    protected static final String SCAN_ON_EMPTY_CHANGES_PROPERTY_NAME = "bitbucket.hooks.processor.scanOnEmptyChanges";
    protected static final boolean SCAN_ON_EMPTY_CHANGES = SystemProperties.getBoolean(SCAN_ON_EMPTY_CHANGES_PROPERTY_NAME, true);

    private static final Logger LOGGER = Logger.getLogger(HookProcessor.class.getName());

    /**
     * See <a href="https://confluence.atlassian.com/bitbucket/event-payloads-740262817.html">Event Payloads</a> for more
     * information about the payload parameter format.
     * @param payload the hook payload
     * @param instanceType the Bitbucket type that called the hook
     * @deprecated use {@link #process(HookEventType, String, BitbucketType, String)}
     */
    @Deprecated
    @Restricted(NoExternalUse.class) // retained for binary compatibility only
    public void process(String payload, BitbucketType instanceType) {
        // no-op as the only caller tries the new method first and falls back to this only for legacy implementations
        // so either this method will not be called or it is overridden anyway
    }

    /**
     * See <a href="https://confluence.atlassian.com/bitbucket/event-payloads-740262817.html">Event Payloads</a> for more
     * information about the payload parameter format.
     * @param type the type of hook.
     * @param payload the hook payload
     * @param instanceType the Bitbucket type that called the hook
     * @param origin the origin of the event.
     */
    public abstract void process(HookEventType type, String payload, BitbucketType instanceType, String origin);

    /**
     * See <a href="https://confluence.atlassian.com/bitbucket/event-payloads-740262817.html">Event Payloads</a> for more
     * information about the payload parameter format.
     * @param type the type of hook.
     * @param payload the hook payload
     * @param instanceType the Bitbucket type that called the hook
     * @param origin the origin of the event.
     * @param serverUrl special value for native Bitbucket Server hooks which don't expose the server URL in the payload.
     */
    public void process(HookEventType type, String payload, BitbucketType instanceType, String origin, String serverUrl) {
        process(type, payload, instanceType, origin);
    }

    /**
     * To be called by implementations once the owner and the repository have been extracted from the payload.
     *
     * @param owner the repository owner as configured in the SCMSource
     * @param repository the repository name as configured in the SCMSource
     * @param mirrorId the mirror id if applicable, may be null
     */
    protected void scmSourceReIndex(final String owner, final String repository, final String mirrorId) {
        try (ACLContext context = ACL.as2(ACL.SYSTEM2)) {
            boolean reindexed = false;
            for (SCMSourceOwner scmOwner : SCMSourceOwners.all()) {
                List<SCMSource> sources = scmOwner.getSCMSources();
                for (SCMSource source : sources) {
                    // Search for the correct SCM source
                    if (source instanceof BitbucketSCMSource scmSource
                            && StringUtils.equalsIgnoreCase(scmSource.getRepoOwner(), owner)
                            && scmSource.getRepository().equals(repository)
                            && (mirrorId == null || StringUtils.equalsIgnoreCase(mirrorId, scmSource.getMirrorId()))) {
                        LOGGER.log(Level.INFO, "Multibranch project found, reindexing " + scmOwner.getName());
                        // TODO: SCMSourceOwner.onSCMSourceUpdated is deprecated. We may explore options with an
                        //  SCMEventListener extension and firing SCMSourceEvents.
                        scmOwner.onSCMSourceUpdated(source);
                        reindexed = true;
                    }
                }
            }
            if (!reindexed) {
                LOGGER.log(Level.INFO, "No multibranch project matching for reindex on {0}/{1}", new Object[] {owner, repository});
            }
        }
    }

    /**
     * Implementations have to call this method when want propagate an
     * {@link SCMHeadEvent} to the scm-api.
     *
     * @param event the to fire
     * @param delaySeconds a delay in seconds to wait before propagate the
     *        event. If the given value is less than 0 than default will be
     *        used.
     */
    protected void notifyEvent(SCMHeadEvent<?> event, int delaySeconds) {
        if (delaySeconds == 0) {
            SCMHeadEvent.fireNow(event);
        } else {
            SCMHeadEvent.fireLater(event, delaySeconds > 0 ? delaySeconds : BitbucketSCMSource.getEventDelaySeconds(), TimeUnit.SECONDS);
        }
    }
}
