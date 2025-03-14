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
package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import java.util.Arrays;
import java.util.Collections;
import jenkins.model.Jenkins;
import jenkins.scm.impl.trait.RegexSCMSourceFilterTrait;
import jenkins.scm.impl.trait.WildcardSCMHeadFilterTrait;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

public class BitbucketSCMNavigatorTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
    @Rule
    public TestName currentTestName = new TestName();

    private BitbucketSCMNavigator load() {
        return load(currentTestName.getMethodName());
    }

    private BitbucketSCMNavigator load(String dataSet) {
        return (BitbucketSCMNavigator) Jenkins.XSTREAM2.fromXML(
                getClass().getResource(getClass().getSimpleName() + "/" + dataSet + ".xml"));
    }

    @Test
    public void modern() throws Exception {
        BitbucketSCMNavigator instance = load();
        assertThat(instance.id(), is("https://bitbucket.org::cloudbeers"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getServerUrl(), is(BitbucketCloudEndpoint.SERVER_URL));
        assertThat(instance.getCredentialsId(), is("bcaef157-f105-407f-b150-df7722eab6c1"));
        assertThat(instance.getTraits(), is(Collections.emptyList()));
    }

    @Test
    public void basic_cloud() throws Exception {
        BitbucketSCMNavigator instance = load();
        assertThat(instance.id(), is("https://bitbucket.org::cloudbeers"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getServerUrl(), is(BitbucketCloudEndpoint.SERVER_URL));
        assertThat(instance.getCredentialsId(), is("bcaef157-f105-407f-b150-df7722eab6c1"));
        assertThat("SAME checkout credentials should mean no checkout trait",
                instance.getTraits(),
                not(hasItem(instanceOf(SSHCheckoutTrait.class))));
        assertThat(".* as a pattern should mean no RegexSCMSourceFilterTrait",
                instance.getTraits(),
                not(hasItem(instanceOf(RegexSCMSourceFilterTrait.class))));
        assertThat(instance.getTraits(),
                containsInAnyOrder(
                        allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))
                        ),
                        allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2))
                        ),
                        allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2)),
                                hasProperty("trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustEveryone.class))
                        ),
                        instanceOf(PublicRepoPullRequestFilterTrait.class),
                        allOf(
                                instanceOf(WebhookRegistrationTrait.class),
                                hasProperty("mode", is(WebhookRegistration.DISABLE))
                        )
                )
        );
    }

    @Test
    public void cloud_project_key() throws Exception {
        BitbucketSCMNavigator instance = load();
        assertThat(instance.id(), is("https://bitbucket.org::cloudbeers"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getProjectKey(), is("PK"));
        assertThat(instance.getServerUrl(), is(BitbucketCloudEndpoint.SERVER_URL));
        assertThat(instance.getCredentialsId(), is("bcaef157-f105-407f-b150-df7722eab6c1"));
        assertThat("SAME checkout credentials should mean no checkout trait",
            instance.getTraits(),
            not(hasItem(instanceOf(SSHCheckoutTrait.class))));
        assertThat(".* as a pattern should mean no RegexSCMSourceFilterTrait",
            instance.getTraits(),
            not(hasItem(instanceOf(RegexSCMSourceFilterTrait.class))));
        assertThat(instance.getTraits(),
            containsInAnyOrder(
                allOf(
                    instanceOf(BranchDiscoveryTrait.class),
                    hasProperty("buildBranch", is(true)),
                    hasProperty("buildBranchesWithPR", is(true))
                ),
                allOf(
                    instanceOf(OriginPullRequestDiscoveryTrait.class),
                    hasProperty("strategyId", is(2))
                ),
                allOf(
                    instanceOf(ForkPullRequestDiscoveryTrait.class),
                    hasProperty("strategyId", is(2)),
                    hasProperty("trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustEveryone.class))
                ),
                instanceOf(PublicRepoPullRequestFilterTrait.class),
                allOf(
                    instanceOf(WebhookRegistrationTrait.class),
                    hasProperty("mode", is(WebhookRegistration.DISABLE))
                )
            )
        );
    }

    @Test
    public void basic_server() throws Exception {
        BitbucketSCMNavigator instance = load();
        assertThat(instance.id(), is("https://bitbucket.test::DUB"));
        assertThat(instance.getRepoOwner(), is("DUB"));
        assertThat(instance.getServerUrl(), is("https://bitbucket.test"));
        assertThat(instance.getCredentialsId(), is("bitbucket"));
        assertThat("checkout credentials should mean checkout trait",
                instance.getTraits(),
                hasItem(
                        allOf(
                                instanceOf(SSHCheckoutTrait.class),
                                hasProperty("credentialsId", is("8b2e4f77-39c5-41a9-b63b-8d367350bfdf"))
                        )
                )
        );
        assertThat(".* as a pattern should mean no RegexSCMSourceFilterTrait",
                instance.getTraits(),
                not(hasItem(instanceOf(RegexSCMSourceFilterTrait.class))));
        assertThat(instance.getTraits(),
                containsInAnyOrder(
                        allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))
                        ),
                        allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2))
                        ),
                        allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2)),
                                hasProperty("trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustEveryone.class))
                        ),
                        instanceOf(PublicRepoPullRequestFilterTrait.class),
                        allOf(
                                instanceOf(WebhookRegistrationTrait.class),
                                hasProperty("mode", is(WebhookRegistration.DISABLE))
                        ),
                        allOf(
                                instanceOf(SSHCheckoutTrait.class),
                                hasProperty("credentialsId", is("8b2e4f77-39c5-41a9-b63b-8d367350bfdf"))
                        )
                )
        );
    }

    @Test
    public void use_agent_checkout() throws Exception {
        BitbucketSCMNavigator instance = load();
        assertThat(instance.id(), is("https://bitbucket.test::DUB"));
        assertThat(instance.getRepoOwner(), is("DUB"));
        assertThat(instance.getServerUrl(), is("https://bitbucket.test"));
        assertThat(instance.getCredentialsId(), is("bitbucket"));
        assertThat("checkout credentials should mean checkout trait",
                instance.getTraits(),
                hasItem(
                        allOf(
                                instanceOf(SSHCheckoutTrait.class),
                                hasProperty("credentialsId", is(nullValue()))
                        )
                )
        );
        assertThat(".* as a pattern should mean no RegexSCMSourceFilterTrait",
                instance.getTraits(),
                not(hasItem(instanceOf(RegexSCMSourceFilterTrait.class))));
        assertThat(instance.getTraits(),
                containsInAnyOrder(
                        allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))
                        ),
                        allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2))
                        ),
                        allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2)),
                                hasProperty("trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustEveryone.class))
                        ),
                        instanceOf(PublicRepoPullRequestFilterTrait.class),
                        allOf(
                                instanceOf(WebhookRegistrationTrait.class),
                                hasProperty("mode", is(WebhookRegistration.DISABLE))
                        ),
                        allOf(
                                instanceOf(SSHCheckoutTrait.class),
                                hasProperty("credentialsId", is(nullValue()))
                        )
                )
        );
    }

    @Issue("JENKINS-45467")
    @Test
    public void same_checkout_credentials() throws Exception {
        BitbucketSCMNavigator instance = load();
        assertThat(instance.id(), is("https://bitbucket.test::DUB"));
        assertThat(instance.getRepoOwner(), is("DUB"));
        assertThat(instance.getServerUrl(), is("https://bitbucket.test"));
        assertThat(instance.getCredentialsId(), is("bitbucket"));
        assertThat("checkout credentials equal to scan should mean no checkout trait",
                instance.getTraits(),
                not(
                        hasItem(
                                allOf(
                                        instanceOf(SSHCheckoutTrait.class),
                                        hasProperty("credentialsId", is(nullValue()))
                                )
                        )
                )
        );
        assertThat(".* as a pattern should mean no RegexSCMSourceFilterTrait",
                instance.getTraits(),
                not(hasItem(instanceOf(RegexSCMSourceFilterTrait.class))));
        assertThat(instance.getTraits(),
                containsInAnyOrder(
                        allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))
                        ),
                        allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2))
                        ),
                        allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2)),
                                hasProperty("trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustEveryone.class))
                        ),
                        instanceOf(PublicRepoPullRequestFilterTrait.class),
                        allOf(
                                instanceOf(WebhookRegistrationTrait.class),
                                hasProperty("mode", is(WebhookRegistration.DISABLE))
                        )
                )
        );
    }

    @Test
    public void limit_repositories() throws Exception {
        BitbucketSCMNavigator instance = load();
        assertThat(instance.id(), is("https://bitbucket.test::DUB"));
        assertThat(instance.getRepoOwner(), is("DUB"));
        assertThat(instance.getServerUrl(), is("https://bitbucket.test"));
        assertThat(instance.getCredentialsId(), is("bitbucket"));
        assertThat("checkout credentials should mean checkout trait",
                instance.getTraits(),
                hasItem(
                        allOf(
                                instanceOf(SSHCheckoutTrait.class),
                                hasProperty("credentialsId", is("8b2e4f77-39c5-41a9-b63b-8d367350bfdf"))
                        )
                )
        );
        assertThat(instance.getTraits(),
                containsInAnyOrder(
                        allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))
                        ),
                        allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2))
                        ),
                        allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2)),
                                hasProperty("trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustEveryone.class))
                        ),
                        instanceOf(PublicRepoPullRequestFilterTrait.class),
                        allOf(
                                instanceOf(WebhookRegistrationTrait.class),
                                hasProperty("mode", is(WebhookRegistration.DISABLE))
                        ),
                        allOf(
                                instanceOf(SSHCheckoutTrait.class),
                                hasProperty("credentialsId", is("8b2e4f77-39c5-41a9-b63b-8d367350bfdf"))
                        ),
                        allOf(
                                instanceOf(RegexSCMSourceFilterTrait.class),
                                hasProperty("regex", is("limited.*"))
                        )
                )
        );
    }


    @Test
    public void exclude_branches() throws Exception {
        BitbucketSCMNavigator instance = load();
        assertThat(instance.id(), is("https://bitbucket.org::cloudbeers"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getServerUrl(), is(BitbucketCloudEndpoint.SERVER_URL));
        assertThat(instance.getCredentialsId(), is("bcaef157-f105-407f-b150-df7722eab6c1"));
        assertThat(instance.getTraits(),
                containsInAnyOrder(
                        allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))
                        ),
                        allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2))
                        ),
                        allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2)),
                                hasProperty("trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustEveryone.class))
                        ),
                        instanceOf(PublicRepoPullRequestFilterTrait.class),
                        allOf(
                                instanceOf(WildcardSCMHeadFilterTrait.class),
                                hasProperty("includes", is("*")),
                                hasProperty("excludes", is("main"))
                        ),
                        allOf(
                                instanceOf(WebhookRegistrationTrait.class),
                                hasProperty("mode", is(WebhookRegistration.DISABLE))
                        )
                )
        );
    }

    @Test
    public void limit_branches() throws Exception {
        BitbucketSCMNavigator instance = load();
        assertThat(instance.id(), is("https://bitbucket.org::cloudbeers"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getServerUrl(), is(BitbucketCloudEndpoint.SERVER_URL));
        assertThat(instance.getCredentialsId(), is("bcaef157-f105-407f-b150-df7722eab6c1"));
        assertThat(instance.getTraits(),
                containsInAnyOrder(
                        allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))
                        ),
                        allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2))
                        ),
                        allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2)),
                                hasProperty("trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustEveryone.class))
                        ),
                        instanceOf(PublicRepoPullRequestFilterTrait.class),
                        allOf(
                                instanceOf(WildcardSCMHeadFilterTrait.class),
                                hasProperty("includes", is("feature/*")),
                                hasProperty("excludes", is(""))
                        ),
                        allOf(
                                instanceOf(WebhookRegistrationTrait.class),
                                hasProperty("mode", is(WebhookRegistration.DISABLE))
                        )
                )
        );
    }

    @Test
    public void register_hooks() throws Exception {
        BitbucketSCMNavigator instance = load();
        assertThat(instance.id(), is("https://bitbucket.test::DUB"));
        assertThat(instance.getRepoOwner(), is("DUB"));
        assertThat(instance.getServerUrl(), is("https://bitbucket.test"));
        assertThat(instance.getCredentialsId(), is("bitbucket"));
        assertThat("checkout credentials should mean checkout trait",
                instance.getTraits(),
                hasItem(
                        allOf(
                                instanceOf(SSHCheckoutTrait.class),
                                hasProperty("credentialsId", is("8b2e4f77-39c5-41a9-b63b-8d367350bfdf"))
                        )
                )
        );
        assertThat(".* as a pattern should mean no RegexSCMSourceFilterTrait",
                instance.getTraits(),
                not(hasItem(instanceOf(RegexSCMSourceFilterTrait.class))));
        assertThat(instance.getTraits(),
                containsInAnyOrder(
                        allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))
                        ),
                        allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2))
                        ),
                        allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2)),
                                hasProperty("trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustEveryone.class))
                        ),
                        instanceOf(PublicRepoPullRequestFilterTrait.class),
                        allOf(
                                instanceOf(WebhookRegistrationTrait.class),
                                hasProperty("mode", is(WebhookRegistration.ITEM))
                        ),
                        allOf(
                                instanceOf(SSHCheckoutTrait.class),
                                hasProperty("credentialsId", is("8b2e4f77-39c5-41a9-b63b-8d367350bfdf"))
                        )
                )
        );
    }

    @Test
    public void given__instance__when__setTraits_empty__then__traitsEmpty() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Collections.emptyList());
        assertThat(instance.getTraits(), is(Collections.emptyList()));
    }

    @Test
    public void given__instance__when__setTraits__then__traitsSet() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(new BranchDiscoveryTrait(1),
                new WebhookRegistrationTrait(WebhookRegistration.DISABLE)));
        assertThat(instance.getTraits(),
                containsInAnyOrder(
                        allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(false))
                        ),
                        allOf(
                                instanceOf(WebhookRegistrationTrait.class),
                                hasProperty("mode", is(WebhookRegistration.DISABLE))
                        )
                )
        );
    }

    @Test
    public void given__instance__when__setServerUrl__then__urlNormalized() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setServerUrl("https://bitbucket.org:443/foo/../bar/../");
        assertThat(instance.getServerUrl(), is("https://bitbucket.org"));
    }

    @Test
    public void given__instance__when__setCredentials_empty__then__credentials_null() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setCredentialsId("");
        assertThat(instance.getCredentialsId(), is(nullValue()));
    }

    @Test
    public void given__instance__when__setCredentials_null__then__credentials_null() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setCredentialsId("");
        assertThat(instance.getCredentialsId(), is(nullValue()));
    }

    @Test
    public void given__instance__when__setCredentials__then__credentials_set() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setCredentialsId("test");
        assertThat(instance.getCredentialsId(), is("test"));
    }

}
