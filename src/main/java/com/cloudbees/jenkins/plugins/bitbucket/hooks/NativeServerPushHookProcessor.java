/*
 * The MIT License
 *
 * Copyright (c) 2016-2018, Yieldlab AG
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
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.JsonParser;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketServerRepository;
import com.cloudbees.jenkins.plugins.bitbucket.server.events.NativeServerChange;
import com.cloudbees.jenkins.plugins.bitbucket.server.events.NativeServerMirrorRepoSynchronizedEvent;
import com.cloudbees.jenkins.plugins.bitbucket.server.events.NativeServerRefsChangedEvent;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import hudson.RestrictedSince;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMHeadEvent;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
@RestrictedSince("933.3.0")
public class NativeServerPushHookProcessor extends HookProcessor {

    private static final Logger LOGGER = Logger.getLogger(NativeServerPushHookProcessor.class.getName());

    @Override
    public void process(HookEventType hookEvent, String payload, BitbucketType instanceType, String origin) {
        return; // without a server URL, the event wouldn't match anything
    }

    @Override
    public void process(HookEventType hookEvent, String payload, BitbucketType instanceType, String origin, String serverUrl) {
        if (payload == null) {
            return;
        }

        final BitbucketServerRepository repository;
        final List<NativeServerChange> changes;
        final String mirrorId;
        try {
            if (hookEvent == HookEventType.SERVER_REFS_CHANGED) {
                final NativeServerRefsChangedEvent event = JsonParser.toJava(payload, NativeServerRefsChangedEvent.class);
                repository = event.getRepository();
                changes = event.getChanges();
                mirrorId = null;
            } else if (hookEvent == HookEventType.SERVER_MIRROR_REPO_SYNCHRONIZED) {
                final NativeServerMirrorRepoSynchronizedEvent event = JsonParser.toJava(payload, NativeServerMirrorRepoSynchronizedEvent.class);
                repository = event.getRepository();
                changes = event.getChanges();
                mirrorId = event.getMirrorServer().getId();
            } else {
                throw new UnsupportedOperationException("Unsupported hook event " + hookEvent);
            }
        } catch (final IOException e) {
            LOGGER.log(Level.SEVERE, "Can not read hook payload", e);
            return;
        }

        if (changes.isEmpty()) {
            final String owner = repository.getOwnerName();
            final String repositoryName = repository.getRepositoryName();
            LOGGER.log(Level.INFO, "Received hook from Bitbucket. Processing push event on {0}/{1}",
                new Object[] { owner, repositoryName });
            scmSourceReIndex(owner, repositoryName);
            return;
        }

        final Multimap<SCMEvent.Type, NativeServerChange> events = HashMultimap.create();
        for (final NativeServerChange change : changes) {
            final String type = change.getType();
            if ("UPDATE".equals(type)) {
                events.put(SCMEvent.Type.UPDATED, change);
            } else if ("DELETE".equals(type)) {
                events.put(SCMEvent.Type.REMOVED, change);
            } else if ("ADD".equals(type)) {
                events.put(SCMEvent.Type.CREATED, change);
            } else {
                LOGGER.log(Level.INFO, "Unknown change event type of {0} received from Bitbucket Server", type);
            }
        }

        for (final SCMEvent.Type type : events.keySet()) {
            ServerPushEvent headEvent = new ServerPushEvent(serverUrl, type, events.get(type), origin, repository, mirrorId);
            SCMHeadEvent.fireLater(headEvent, BitbucketSCMSource.getEventDelaySeconds(), TimeUnit.SECONDS);
        }
    }

}
