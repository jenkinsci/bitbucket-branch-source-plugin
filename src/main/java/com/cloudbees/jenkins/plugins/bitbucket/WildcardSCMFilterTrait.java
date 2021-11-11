package com.cloudbees.jenkins.plugins.bitbucket;

import hudson.Extension;
import java.util.regex.Pattern;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;
import jenkins.scm.api.mixin.ChangeRequestSCMHead2;
import jenkins.scm.api.mixin.TagSCMHead;
import jenkins.scm.api.trait.SCMHeadPrefilter;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.trait.Selection;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class WildcardSCMFilterTrait extends SCMSourceTrait {

    /**
     * The branch include rules.
     */
    private final String includes;

    /**
     * The branch exclude rules.
     */
    private final String excludes;

    /**
     * The tag include rules.
     */
    private final String tagIncludes;

    /**
     * The tag exclude rules.
     */
    private final String tagExcludes;

    /**
     * The PR include rules based on origin branch.
     */
    private final String prOriginIncludes;

    /**
     * The PR exclude rules based on origin branch.
     */
    private final String prOriginExcludes;

    /**
     * The PR include rules based on destination branch.
     */
    private final String prDestinationIncludes;

    /**
     * The PR exclude rules based on destination branch.
     */
    private final String prDestinationExcludes;

    /**
     * Stapler constructor.
     *
     * @param includes              the branch include rules
     * @param excludes              the branch exclude rules
     * @param tagIncludes           the tag include rules
     * @param tagExcludes           the tag exclude rules
     * @param prOriginIncludes      the pull request include rules based on origin
     *                              branch
     * @param prOriginExcludes      the pull request exclude rules based on origin
     *                              branch
     * @param prDestinationIncludes the pull request include rules based on
     *                              destination branch
     * @param prDestinationExcludes the pull request exclude rules based on
     *                              destination branch
     */
    @DataBoundConstructor
    public WildcardSCMFilterTrait(String includes, String excludes, String tagIncludes, String tagExcludes,
            String prOriginIncludes, String prOriginExcludes, String prDestinationIncludes,
            String prDestinationExcludes) {
        this.includes = StringUtils.defaultIfBlank(includes, "*");
        this.excludes = StringUtils.defaultIfBlank(excludes, "");
        this.tagIncludes = StringUtils.defaultIfBlank(tagIncludes, "");
        this.tagExcludes = StringUtils.defaultIfBlank(tagExcludes, "");
        this.prOriginIncludes = StringUtils.defaultIfBlank(prOriginIncludes, "");
        this.prOriginExcludes = StringUtils.defaultIfBlank(prOriginExcludes, "");
        this.prDestinationIncludes = StringUtils.defaultIfBlank(prDestinationIncludes, "");
        this.prDestinationExcludes = StringUtils.defaultIfBlank(prDestinationExcludes, "");
    }

    /**
     * Returns the branch include rules.
     *
     * @return the branch include rules.
     */
    public String getIncludes() {
        return includes;
    }

    /**
     * Returns the branch exclude rules.
     *
     * @return the branch exclude rules.
     */
    public String getExcludes() {
        return excludes;
    }

    /**
     * Returns the tag include rules.
     *
     * @return the tag include rules.
     */
    public String getTagIncludes() {
        return tagIncludes;
    }

    /**
     * Returns the tag exclude rules.
     *
     * @return the tag exclude rules.
     */
    public String getTagExcludes() {
        return tagExcludes;
    }

    /**
     * Returns the pull request include rules based on origin branch.
     *
     * @return the pull request include rules.
     */
    public String getPrOriginIncludes() {
        return prOriginIncludes;
    }

    /**
     * Returns the pull request exclude rules based on origin branch.
     *
     * @return the pull request exclude rules.
     */
    public String getPrOriginExcludes() {
        return prOriginExcludes;
    }

    /**
     * Returns the pull request exclude rules based on destination branch.
     *
     * @return the pull request exclude rules.
     */
    public String getPrDestinationIncludes() {
        return prDestinationIncludes;
    }

    /**
     * Returns the pull request exclude rules based on destination branch.
     *
     * @return the pull request exclude rules.
     */
    public String getPrDestinationExcludes() {
        return prDestinationExcludes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        context.withPrefilter(new SCMHeadPrefilter() {
            @Override
            public boolean isExcluded(SCMSource request, SCMHead head) {
                if (head instanceof ChangeRequestSCMHead) {
                    // PR destined to targetName branches
                    String targetName = ((ChangeRequestSCMHead) head).getTarget().getName();
                    return !Pattern.matches(getPattern(getPrDestinationIncludes()), targetName)
                            || Pattern.matches(getPattern(getPrDestinationExcludes()), targetName);
                }

                if (head instanceof ChangeRequestSCMHead2) {
                    // PR originating from originName branches
                    String originName = ((ChangeRequestSCMHead2) head).getOriginName();
                    return !Pattern.matches(getPattern(getPrOriginIncludes()), originName)
                            || Pattern.matches(getPattern(getPrOriginExcludes()), originName);
                }

                if (head instanceof TagSCMHead) {
                    // tag
                    return !Pattern.matches(getPattern(getTagIncludes()), head.getName())
                            || Pattern.matches(getPattern(getTagExcludes()), head.getName());
                } else {
                    // branch
                    return !Pattern.matches(getPattern(getIncludes()), head.getName())
                            || Pattern.matches(getPattern(getExcludes()), head.getName());
                }
            }
        });
    }

    /**
     * Returns the pattern corresponding to the branches containing wildcards.
     *
     * @param branches the names of branches to create a pattern for
     * @return pattern corresponding to the branches containing wildcards
     */
    private String getPattern(String branches) {
        StringBuilder quotedBranches = new StringBuilder();
        for (String wildcard : branches.split(" ")) {
            StringBuilder quotedBranch = new StringBuilder();
            for (String branch : wildcard.split("(?=[*])|(?<=[*])")) {
                if (branch.equals("*")) {
                    quotedBranch.append(".*");
                } else if (!branch.isEmpty()) {
                    quotedBranch.append(Pattern.quote(branch));
                }
            }
            if (quotedBranches.length() > 0) {
                quotedBranches.append("|");
            }
            quotedBranches.append(quotedBranch);
        }
        return quotedBranches.toString();
    }

    @Symbol("SCMWildcardFilter")
    @Extension
    @Selection
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.WildcardSCMFilterTrait_DisplayName();
        }
    }
}
