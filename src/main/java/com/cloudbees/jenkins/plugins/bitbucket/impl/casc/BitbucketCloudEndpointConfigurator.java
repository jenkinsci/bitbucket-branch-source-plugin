/*
 * The MIT License
 *
 * Copyright (c) 2025, Nikolas Falco
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
package com.cloudbees.jenkins.plugins.bitbucket.impl.casc;

import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.cloud.CloudWebhookConfiguration;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.Configurator;
import io.jenkins.plugins.casc.ConfiguratorException;
import io.jenkins.plugins.casc.impl.configurators.DataBoundConfigurator;
import io.jenkins.plugins.casc.model.Mapping;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Specialised class to configure how to build a new instance of
 * BitbucketServerEndpoint using {@link ConfigurationAsCode}.
 *
 * @since 937.0.0
 */
@Extension
@Restricted(NoExternalUse.class)
public class BitbucketCloudEndpointConfigurator extends DataBoundConfigurator<BitbucketCloudEndpoint> {
    private static final Logger logger = Logger.getLogger(BitbucketCloudEndpointConfigurator.class.getName());

    public BitbucketCloudEndpointConfigurator() {
        super(BitbucketCloudEndpoint.class);
    }

    @Override
    protected BitbucketCloudEndpoint instance(@NonNull Mapping mapping, @NonNull ConfigurationContext context) throws ConfiguratorException {
        final Configurator<Boolean> boolConfigurator = context.lookupOrFail(Boolean.class);
        final Configurator<Integer> intConfigurator = context.lookupOrFail(Integer.class);

        boolean enableCache = false;
        if (mapping.containsKey("enableCache")) {
            enableCache = boolConfigurator.configure(mapping.get("enableCache"), context);
            mapping.remove("enableCache");
        }
        int teamCacheDuration = 0;
        if (mapping.containsKey("teamCacheDuration")) {
            teamCacheDuration = intConfigurator.configure(mapping.get("teamCacheDuration"), context);
            mapping.remove("teamCacheDuration");
        }
        int repositoriesCacheDuration = 0;
        if (mapping.containsKey("repositoriesCacheDuration")) {
            repositoriesCacheDuration = intConfigurator.configure(mapping.get("repositoriesCacheDuration"), context);
            mapping.remove("repositoriesCacheDuration");
        }
        BitbucketWebhookConfiguration webhook = getWebhook(mapping, context);
        return new BitbucketCloudEndpoint(enableCache, teamCacheDuration, repositoriesCacheDuration, webhook);
    }

    private BitbucketWebhookConfiguration getWebhook(@NonNull Mapping mapping, @NonNull ConfigurationContext context) {
        final Configurator<String> stringConfigurator = context.lookupOrFail(String.class);
        final Configurator<Boolean> boolConfigurator = context.lookupOrFail(Boolean.class);

        boolean manageHooks = false;
        if (mapping.containsKey("manageHooks")) {
            logger.warning("manageHooks is deprecated, replace from your CasC definition with the appropriate webhook definition.");
            manageHooks = boolConfigurator.configure(mapping.get("manageHooks"), context);
            mapping.remove("manageHooks");
        }
        String credentialsId = null;
        if (mapping.containsKey("credentialsId")) {
            logger.warning("credentialsId is deprecated, replace from your CasC definition with the appropriate webhook definition.");
            credentialsId = stringConfigurator.configure(mapping.get("credentialsId"), context);
            mapping.remove("credentialsId");
        }
        boolean enableHookSignature = false;
        if (mapping.containsKey("enableHookSignature")) {
            logger.warning("enableHookSignature is deprecated, replace from your CasC definition with the appropriate webhook definition.");
            enableHookSignature = boolConfigurator.configure(mapping.get("enableHookSignature"), context);
            mapping.remove("enableHookSignature");
        }
        String hookSignatureCredentialsId = null;
        if (mapping.containsKey("hookSignatureCredentialsId")) {
            logger.warning("hookSignatureCredentialsId is deprecated, replace from your CasC definition with the appropriate webhook definition.");
            hookSignatureCredentialsId = stringConfigurator.configure(mapping.get("hookSignatureCredentialsId"), context);
            mapping.remove("hookSignatureCredentialsId");
        }
        return new CloudWebhookConfiguration(manageHooks, credentialsId, enableHookSignature, hookSignatureCredentialsId);
    }

}