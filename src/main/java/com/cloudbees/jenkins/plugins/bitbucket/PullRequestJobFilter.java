package com.cloudbees.jenkins.plugins.bitbucket;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.views.ViewJobFilter;
import jenkins.scm.api.SCMHead;

public class PullRequestJobFilter extends AbstractBranchJobFilter {
    @DataBoundConstructor
    public PullRequestJobFilter() {}

    @Override
    protected boolean shouldShow(SCMHead head) {
        return head instanceof SCMHeadWithOwnerAndRepo && ((SCMHeadWithOwnerAndRepo) head).getPullRequestId() != null;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ViewJobFilter> {
        @Override
        public String getDisplayName() {
            return "Bitbucket Pull Request Jobs Only";
        }
    }

}
