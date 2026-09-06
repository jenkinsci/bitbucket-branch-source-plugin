/*
 * The MIT License
 *
 * Copyright (c) 2026, Nikolas Falco
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
package com.cloudbees.jenkins.plugins.bitbucket.impl.scm;

import com.cloudbees.jenkins.plugins.bitbucket.util.BitbucketCredentialsUtils;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.RelativePath;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.scm.api.SCMSourceOwner;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.interceptor.RequirePOST;

@Restricted(NoExternalUse.class)
public class CredentialsRef implements Describable<CredentialsRef> {

    private final String credentialsId;

    @DataBoundConstructor
    public CredentialsRef(@CheckForNull String credentialsId) {
        this.credentialsId = Util.fixEmptyAndTrim(credentialsId);
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<CredentialsRef> {

        @Override
        public String getDisplayName() {
            return "Credentials Reference";
        }

        @Override
        public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
            return super.configure(req, json);
        }

        @RequirePOST
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context,
                                                     @RelativePath("..") @QueryParameter(fixEmpty = true, value = "serverUrl", required = true) String serverURL) {
            return BitbucketCredentialsUtils.listCredentials(context, serverURL, null);
        }

        public FormValidation doCheckCredentialsId(@CheckForNull @AncestorInPath SCMSourceOwner context,
                                                   @QueryParameter String value,
                                                   @RelativePath("..") @QueryParameter(fixEmpty = true, value = "additionalCredentialsIds", required = true) CredentialsRef additionalCredentials,
                                                   @RelativePath("..") @QueryParameter(fixEmpty = true, value = "serverUrl", required = true) String serverURL) {
            return BitbucketCredentialsUtils.checkCredentialsId(context, serverURL, value);
        }

    }
}