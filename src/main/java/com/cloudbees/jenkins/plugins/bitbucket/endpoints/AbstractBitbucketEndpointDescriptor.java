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

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
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
        Jenkins jenkins = Jenkins.getActiveInstance();
        jenkins.checkPermission(Jenkins.ADMINISTER);
        StandardListBoxModel result = new StandardListBoxModel();
        result.includeMatchingAs(
                ACL.SYSTEM,
                jenkins,
                StandardUsernameCredentials.class,
                URIRequirementBuilder.fromUri(serverUrl).build(),
                CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class)
        );
        return result;
    }

    @Restricted(NoExternalUse.class) //stapler
    @SuppressWarnings("unused")
    public ListBoxModel doFillCertificateCredentialsIdItems(@QueryParameter String serverUrl) {
        Jenkins jenkins = Jenkins.getActiveInstance();
        jenkins.checkPermission(Jenkins.ADMINISTER);
        StandardListBoxModel result = new StandardListBoxModel();
        result.includeMatchingAs(
                ACL.SYSTEM,
                jenkins,
                StandardCertificateCredentials.class,
                URIRequirementBuilder.fromUri(serverUrl).build(),
                CredentialsMatchers.instanceOf(StandardCertificateCredentials.class)
        );
        return result;
    }
}
