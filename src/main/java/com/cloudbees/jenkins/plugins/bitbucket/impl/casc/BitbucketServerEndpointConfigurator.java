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
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketServerEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.plugin.PluginWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.server.ServerWebhookConfiguration;
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
public class BitbucketServerEndpointConfigurator extends DataBoundConfigurator<BitbucketServerEndpoint> {
    private static final Logger logger = Logger.getLogger(BitbucketServerEndpointConfigurator.class.getName());

    public BitbucketServerEndpointConfigurator() {
        super(BitbucketServerEndpoint.class);
    }

    @Override
    protected BitbucketServerEndpoint instance(@NonNull Mapping mapping, @NonNull ConfigurationContext context) throws ConfiguratorException {
        final Configurator<String> stringConfigurator = context.lookupOrFail(String.class);

        final String displayName = stringConfigurator.configure(mapping.get("displayName"), context);
        mapping.remove("displayName");

        final String serverURL;
        if (mapping.containsKey("serverUrl")) {
            serverURL = stringConfigurator.configure(mapping.get("serverUrl"), context);
            mapping.remove("serverUrl");
        } else {
            serverURL = stringConfigurator.configure(mapping.get("serverURL"), context);
            mapping.remove("serverURL");
        }
        String serverVersion = null;
        if (mapping.containsKey("serverVersion")) {
            serverVersion = stringConfigurator.configure(mapping.get("serverVersion"), context);
            mapping.remove("serverVersion");
        }
        BitbucketWebhookConfiguration webhook = getWebhook(mapping, context);
        BitbucketServerEndpoint endpoint = new BitbucketServerEndpoint(displayName, serverURL, webhook );
        if (serverVersion != null) {
            endpoint.setServerVersion(serverVersion);
        }
        // remove unmapped attributes
        if (mapping.containsKey("callCanMerge")) {
            logger.warning("callCanMerge is deprecated and ignored for BitbucketServerEndpoint definition, remove from your CasC definition.");
            mapping.remove("callCanMerge");
        }
        if (mapping.containsKey("callChanges")) {
            logger.warning("callChanges is deprecated and ignored for BitbucketServerEndpoint definition, remove from your CasC definition.");
            mapping.remove("callChanges");
        }
        return endpoint;
    }

    @SuppressWarnings("deprecation")
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
        String webhookImplementation = null;
        if (mapping.containsKey("webhookImplementation")) {
            logger.warning("webhookImplementation is deprecated, replace from your CasC definition with the appropriate webhook definition.");
            webhookImplementation = stringConfigurator.configure(mapping.get("webhookImplementation"), context);
            mapping.remove("webhookImplementation");
        }
        BitbucketWebhookConfiguration webhook;
        if ("NATIVE".equals(webhookImplementation)) {
            webhook = new ServerWebhookConfiguration(manageHooks, credentialsId, enableHookSignature, hookSignatureCredentialsId);
        } else {
            // old default was plugin
            webhook = new PluginWebhookConfiguration(manageHooks, credentialsId);
        }
        return webhook;
    }

}
