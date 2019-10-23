/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket.endpoints;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.net.MalformedURLException;
import java.net.URL;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;

/**
 * {@link Descriptor} base class for {@link AbstractBitbucketEndpoint} subclasses.
 *
 * @since 2.2.0
 */
public abstract class AbstractBitbucketEndpointDescriptor extends Descriptor<AbstractBitbucketEndpoint> {
    /**
     * Stapler form completion.
     *
     * @param serverUrl the server URL.
     * @return the available credentials.
     */
    @Restricted(NoExternalUse.class) // stapler
    @SuppressWarnings("unused")
    public ListBoxModel doFillCredentialsIdItems(@QueryParameter String serverUrl) {
        Jenkins jenkins = Jenkins.get();
        jenkins.checkPermission(Jenkins.ADMINISTER);
        StandardListBoxModel result = new StandardListBoxModel();
        result.includeMatchingAs(
                ACL.SYSTEM,
                jenkins,
                StandardCredentials.class,
                URIRequirementBuilder.fromUri(serverUrl).build(),
                AuthenticationTokens.matcher(BitbucketAuthenticator.authenticationContext(serverUrl))
        );
        return result;
    }

    @Restricted(NoExternalUse.class)
    @SuppressWarnings("unused") // stapler
    public static FormValidation doCheckBitbucketJenkinsRootUrl(@QueryParameter String value) {
        String url = Util.fixEmptyAndTrim(value);
        if (url == null) {
            return FormValidation.ok();
        }
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            return FormValidation.error("Invalid URL: " +  e.getMessage());
        }
        return FormValidation.ok();
    }
}
