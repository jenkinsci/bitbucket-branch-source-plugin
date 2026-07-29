package com.cloudbees.jenkins.plugins.bitbucket.impl.scm;

import com.cloudbees.jenkins.plugins.bitbucket.util.BitbucketCredentialsUtils;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.RelativePath;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.util.ListBoxModel;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
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

        @RequirePOST
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context,
                                                     @RelativePath("..") @QueryParameter(fixEmpty = true, value = "serverUrl", required = true) String serverURL) {
            return BitbucketCredentialsUtils.listCredentials(context, serverURL, null);
        }
    }
}