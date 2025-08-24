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
package com.cloudbees.jenkins.plugins.bitbucket.trait;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSourceContext;
import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSourceRequest;
import com.cloudbees.jenkins.plugins.bitbucket.BranchSCMHead;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AuthorizationStrategy;
import hudson.security.SecurityRealm;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.PrintStream;
import java.util.Collections;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.trait.SCMHeadFilter;
import jenkins.scm.api.trait.SCMHeadPrefilter;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matcher;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.springframework.security.access.AccessDeniedException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasToString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assume.assumeThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class BranchDiscoveryTraitTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Test
    public void given__discoverAll__when__appliedToContext__then__noFilter() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(ctx.authorities(), not((Matcher) hasItem(
                instanceOf(BranchDiscoveryTrait.BranchSCMHeadAuthority.class)
        )));
        BranchDiscoveryTrait instance = new BranchDiscoveryTrait(true, true);
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(true));
        assertThat(ctx.wantPRs(), is(false));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assertThat(ctx.authorities(), (Matcher) hasItem(
                instanceOf(BranchDiscoveryTrait.BranchSCMHeadAuthority.class)
        ));
    }

    @Test
    public void given__excludingPRs__when__appliedToContext__then__filter() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(ctx.authorities(), not((Matcher) hasItem(
                instanceOf(BranchDiscoveryTrait.BranchSCMHeadAuthority.class)
        )));
        BranchDiscoveryTrait instance = new BranchDiscoveryTrait(true, false);
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(true));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(),
                contains(instanceOf(BranchDiscoveryTrait.ExcludeOriginPRBranchesSCMHeadFilter.class)));
        assertThat(ctx.authorities(), (Matcher) hasItem(
                instanceOf(BranchDiscoveryTrait.BranchSCMHeadAuthority.class)
        ));
    }

    @Test
    public void given__onlyPRs__when__appliedToContext__then__filter() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());
        assumeThat(ctx.wantBranches(), is(false));
        assumeThat(ctx.wantPRs(), is(false));
        assumeThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assumeThat(ctx.filters(), is(Collections.<SCMHeadFilter>emptyList()));
        assumeThat(ctx.authorities(), not((Matcher) hasItem(
                instanceOf(BranchDiscoveryTrait.BranchSCMHeadAuthority.class)
        )));
        BranchDiscoveryTrait instance = new BranchDiscoveryTrait(false, true);
        instance.decorateContext(ctx);
        assertThat(ctx.wantBranches(), is(true));
        assertThat(ctx.wantPRs(), is(true));
        assertThat(ctx.prefilters(), is(Collections.<SCMHeadPrefilter>emptyList()));
        assertThat(ctx.filters(), contains(instanceOf(BranchDiscoveryTrait.OnlyOriginPRBranchesSCMHeadFilter.class)));
        assertThat(ctx.authorities(), (Matcher) hasItem(
                instanceOf(BranchDiscoveryTrait.BranchSCMHeadAuthority.class)
        ));
    }

    @Test
    public void given__descriptor__when__displayingOptions__then__allThreePresent() {
        ListBoxModel options =
                j.jenkins.getDescriptorByType(BranchDiscoveryTrait.DescriptorImpl.class).doFillStrategyIdItems();
        assertThat(options.size(), is(3));
        assertThat(options.get(0).value, is("1"));
        assertThat(options.get(1).value, is("2"));
        assertThat(options.get(2).value, is("3"));
    }

    @Test
    public void given__context__with__AlwaysIncludePattern__shouldCompile() {
        BranchDiscoveryTrait instance = new BranchDiscoveryTrait(false, true);

        assertThat(instance.getBranchesAlwaysIncludedRegexPattern(), is(nullValue()));
        instance.setBranchesAlwaysIncludedRegex("");
        assertThat(instance.getBranchesAlwaysIncludedRegexPattern(), is(nullValue()));

        instance.setBranchesAlwaysIncludedRegex(".*myBranch");
        assertThat(instance.getBranchesAlwaysIncludedRegexPattern(), hasToString(".*myBranch"));
    }

    @Test
    public void given__excludingPRs__branch__match__alwaysIncludedRegex__shouldNotBeExcluded() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());

        BranchDiscoveryTrait instance = new BranchDiscoveryTrait(true, false);
        instance.setBranchesAlwaysIncludedRegex(".*release$");
        instance.decorateContext(ctx);
        assertThat(ctx.filters(), hasSize(1));
        SCMHeadFilter filter = ctx.filters().get(0);
        assertThat(filter, instanceOf(BranchDiscoveryTrait.ExcludeOriginPRBranchesSCMHeadFilter.class));

        SCMHead head = mock(BranchSCMHead.class);
        when(head.getName()).thenReturn("feature/release");
        BitbucketSCMSourceRequest request = prepareRequest();

        assertThat(filter.isExcluded(request, head), is(false));
        verify(request.listener().getLogger())
            .println("Include branch feature/release because branch name matches always included pattern");
        verifyNoMoreInteractions(request.listener().getLogger());
    }

    @Test
    public void given__onlyPRs__branch__match__alwaysIncludedRegex__shouldNotBeExcluded() throws Exception {
        BitbucketSCMSourceContext ctx = new BitbucketSCMSourceContext(null, SCMHeadObserver.none());

        BranchDiscoveryTrait instance = new BranchDiscoveryTrait(false, true);
        instance.setBranchesAlwaysIncludedRegex(".*release$");
        instance.decorateContext(ctx);
        assertThat(ctx.filters(), hasSize(1));
        SCMHeadFilter filter = ctx.filters().get(0);
        assertThat(filter, instanceOf(BranchDiscoveryTrait.OnlyOriginPRBranchesSCMHeadFilter.class));

        SCMHead head = mock(BranchSCMHead.class);
        when(head.getName()).thenReturn("feature/release");
        BitbucketSCMSourceRequest request = prepareRequest();

        assertThat(filter.isExcluded(request, head), is(false));
        verify(request.listener().getLogger())
            .println("Include branch feature/release because branch name matches always included pattern");
        verifyNoMoreInteractions(request.listener().getLogger());
    }

    @Test
    public void shouldValidateBranchesAlwaysIncludedRegexAsValid() {
        Item item = mock(Item.class);
        BranchDiscoveryTrait.DescriptorImpl descriptor = j.jenkins.getDescriptorByType(BranchDiscoveryTrait.DescriptorImpl.class);

        FormValidation formValidation = descriptor.doCheckBranchesAlwaysIncludedRegex(item, null);
        assertThat(formValidation.kind, is(FormValidation.Kind.OK));

        formValidation = descriptor.doCheckBranchesAlwaysIncludedRegex(item, "");
        assertThat(formValidation.kind, is(FormValidation.Kind.OK));

        formValidation = descriptor.doCheckBranchesAlwaysIncludedRegex(item, "  ");
        assertThat(formValidation.kind, is(FormValidation.Kind.OK));

        formValidation = descriptor.doCheckBranchesAlwaysIncludedRegex(item, ".*myBranch");
        assertThat(formValidation.kind, is(FormValidation.Kind.OK));

        formValidation = descriptor.doCheckBranchesAlwaysIncludedRegex(null, ".*myBranch");
        assertThat(formValidation.kind, is(FormValidation.Kind.OK));

        verify(item, times(4)).checkPermission(Item.CONFIGURE);
    }

    @Test
    public void shouldThrowPermissionDeniedIfNoContextAndNoManagePermission() {
        BranchDiscoveryTrait.DescriptorImpl descriptor = j.jenkins.getDescriptorByType(BranchDiscoveryTrait.DescriptorImpl.class);

        SecurityRealm realm = j.jenkins.getSecurityRealm();
        AuthorizationStrategy strategy = j.jenkins.getAuthorizationStrategy();
        try {
            j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
            MockAuthorizationStrategy mockStrategy = new MockAuthorizationStrategy();
            mockStrategy.grant(Jenkins.READ).onRoot().to("bob");
            j.jenkins.setAuthorizationStrategy(mockStrategy);
            try (ACLContext context = ACL.as(User.getById("bob", false))) {
                Assertions.assertThatThrownBy(() ->descriptor.doCheckBranchesAlwaysIncludedRegex(null, ".*myBranch"))
                    .isInstanceOf(AccessDeniedException.class);
            }
        } finally {
            j.jenkins.setSecurityRealm(realm);
            j.jenkins.setAuthorizationStrategy(strategy);
        }

    }

    @Test
    public void shouldValidateBranchesAlwaysIncludedRegexAsInvalid() {
        Item item = mock(Item.class);
        BranchDiscoveryTrait.DescriptorImpl descriptor = j.jenkins.getDescriptorByType(BranchDiscoveryTrait.DescriptorImpl.class);

        FormValidation formValidation = descriptor.doCheckBranchesAlwaysIncludedRegex(item, "invalidRegex/\\");
        assertThat(formValidation.kind, is(FormValidation.Kind.ERROR));

        verify(item).checkPermission(Item.CONFIGURE);
    }

    private BitbucketSCMSourceRequest prepareRequest() {
        BitbucketSCMSourceRequest request = mock(BitbucketSCMSourceRequest.class);
        TaskListener listener = mock(TaskListener.class);
        when(listener.getLogger()).thenReturn(mock(PrintStream.class));
        when(request.listener()).thenReturn(listener);
        return request;
    }

}
