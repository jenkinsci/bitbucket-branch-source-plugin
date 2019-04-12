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
import hudson.EnvVars;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.DescribableList;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.SCMSourceOwners;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Abstract hook processor.
 *
 * Add new hook processors by extending this class and implement {@link #process(HookEventType, String, BitbucketType, String)},
 * extract owner and repository name from the hook payload and then call {@link #scmSourceReIndex(String, String)}
 * to launch a branch/PR reindexing on the matching SCMSource.
 */
public abstract class HookProcessor {

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
     */
    protected void scmSourceReIndex(final String owner, final String repository) {
        try (ACLContext context = ACL.as(ACL.SYSTEM)) {
            boolean reindexed = false;
            for (SCMSourceOwner scmOwner : SCMSourceOwners.all()) {
                List<SCMSource> sources = scmOwner.getSCMSources();
                for (SCMSource source : sources) {
                    // Search for the correct SCM source
                    if (source instanceof BitbucketSCMSource && ((BitbucketSCMSource) source).getRepoOwner().equalsIgnoreCase(owner)
                            && ((BitbucketSCMSource) source).getRepository().equals(repository)) {
                        LOGGER.log(Level.INFO, "Multibranch project found, reindexing " + scmOwner.getName());
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
     * Set global Jenkins environment variables
     *
     * @param key of the environment variable
     * @param value of the environment variable
     */
    protected void putEnvVar(String key, String value) throws IOException {
        Jenkins jenkins = Jenkins.getInstance();
        DescribableList<NodeProperty<?>, NodePropertyDescriptor> globalNodeProperties = jenkins.getGlobalNodeProperties();
        List<EnvironmentVariablesNodeProperty> envVarsNodePropertyList = globalNodeProperties.getAll(EnvironmentVariablesNodeProperty.class);

        EnvironmentVariablesNodeProperty newEnvVarsNodeProperty = null;
        EnvVars envVars = null;

        if (envVarsNodePropertyList == null || envVarsNodePropertyList.isEmpty()) {
            newEnvVarsNodeProperty = new EnvironmentVariablesNodeProperty();
            globalNodeProperties.add(newEnvVarsNodeProperty);
            envVars = newEnvVarsNodeProperty.getEnvVars();
        } else {
            envVars = envVarsNodePropertyList.get(0).getEnvVars();
        }
        envVars.put(key, value);
    }
}
