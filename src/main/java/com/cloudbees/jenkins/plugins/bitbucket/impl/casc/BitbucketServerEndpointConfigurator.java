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

import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketServerEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.server.BitbucketServerWebhookImplementation;
import hudson.Extension;
import io.jenkins.plugins.casc.BaseConfigurator;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorException;
import io.jenkins.plugins.casc.model.Mapping;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * Specialised class to configure how to build a new instance of
 * BitbucketServerEndpoint using {@link ConfigurationAsCode}.
 *
 * @since 937.0.0
 */
@Extension
@Restricted(NoExternalUse.class)
public class BitbucketServerEndpointConfigurator extends BaseConfigurator<BitbucketServerEndpoint> {
    private final static Logger logger = LoggerFactory.getLogger(BitbucketServerEndpointConfigurator.class);

    @Override
    public Class<BitbucketServerEndpoint> getTarget() {
        return BitbucketServerEndpoint.class;
    }

    @Override
    protected BitbucketServerEndpoint instance(Mapping mapping, ConfigurationContext context) throws ConfiguratorException {
        final String displayName = mapping.getScalarValue("displayName");
        final String serverURL;
        if (mapping.containsKey("serverUrl")) {
            serverURL = mapping.getScalarValue("serverUrl");
        } else {
            serverURL = mapping.getScalarValue("serverURL");
        }
        boolean manageHooks = false;
        if (mapping.containsKey("manageHooks")) {
            manageHooks = Boolean.parseBoolean(trimToNull(mapping.getScalarValue("manageHooks")));
        }
        String credentialsId = null;
        if (mapping.containsKey("credentialsId")) {
            credentialsId = mapping.getScalarValue("credentialsId");
        }
        boolean enableHookSignature = false;
        if (mapping.containsKey("enableHookSignature")) {
            enableHookSignature = Boolean.parseBoolean(trimToNull(mapping.getScalarValue("enableHookSignature")));
        }
        String hookSignatureCredentialsId = null;
        if (mapping.containsKey("hookSignatureCredentialsId")) {
            credentialsId = mapping.getScalarValue("hookSignatureCredentialsId");
        }
        String serverVersion = null;
        if (mapping.containsKey("serverVersion")) {
            serverVersion = mapping.getScalarValue("serverVersion");
        }
        BitbucketServerWebhookImplementation webhookImplementation = null;
        if (mapping.containsKey("webhookImplementation")) {
            webhookImplementation = BitbucketServerWebhookImplementation.valueOf(mapping.getScalarValue("webhookImplementation"));
        }
        BitbucketServerEndpoint endpoint = new BitbucketServerEndpoint(displayName, serverURL, manageHooks, credentialsId, enableHookSignature, hookSignatureCredentialsId);
        if (serverVersion != null) {
            endpoint.setServerVersion(serverVersion);
        }
        if (webhookImplementation != null) {
            endpoint.setWebhookImplementation(webhookImplementation);
        }
        // remove unmapped attributes
        if (mapping.containsKey("callCanMerge")) {
            logger.warn("callCanMerge is deprecated and ignore for BitbucketServerEndpoint definition, remove from your CasC definition.");
            mapping.remove("callCanMerge");
        }
        if (mapping.containsKey("callChanges")) {
            logger.warn("callChanges is deprecated and ignore for BitbucketServerEndpoint definition, remove from your CasC definition.");
            mapping.remove("callChanges");
        }
        return endpoint;
    }

}
