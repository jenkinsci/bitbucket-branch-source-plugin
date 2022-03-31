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

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

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
        // legacy API
        assertThat(instance.getBitbucketServerUrl(), is(nullValue()));
        assertThat(instance.getCheckoutCredentialsId(), is("SAME"));
        assertThat(instance.getPattern(), is(".*"));
        assertThat(instance.isAutoRegisterHooks(), is(false));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
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
        // legacy API
        assertThat(instance.getBitbucketServerUrl(), is("https://bitbucket.test"));
        assertThat(instance.getCheckoutCredentialsId(), is("8b2e4f77-39c5-41a9-b63b-8d367350bfdf"));
        assertThat(instance.getPattern(), is(".*"));
        assertThat(instance.isAutoRegisterHooks(), is(false));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
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
        // legacy API
        assertThat(instance.getBitbucketServerUrl(), is("https://bitbucket.test"));
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.ANONYMOUS));
        assertThat(instance.getPattern(), is(".*"));
        assertThat(instance.isAutoRegisterHooks(), is(false));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
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
        // legacy API
        assertThat(instance.getBitbucketServerUrl(), is("https://bitbucket.test"));
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getPattern(), is(".*"));
        assertThat(instance.isAutoRegisterHooks(), is(false));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
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
        // legacy API
        assertThat(instance.getBitbucketServerUrl(), is("https://bitbucket.test"));
        assertThat(instance.getCheckoutCredentialsId(), is("8b2e4f77-39c5-41a9-b63b-8d367350bfdf"));
        assertThat(instance.getPattern(), is("limited.*"));
        assertThat(instance.isAutoRegisterHooks(), is(false));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
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
        // legacy API
        assertThat(instance.getBitbucketServerUrl(), is(nullValue()));
        assertThat(instance.getCheckoutCredentialsId(), is("SAME"));
        assertThat(instance.getPattern(), is(".*"));
        assertThat(instance.isAutoRegisterHooks(), is(false));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("main"));
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
        // legacy API
        assertThat(instance.getBitbucketServerUrl(), is(nullValue()));
        assertThat(instance.getCheckoutCredentialsId(), is("SAME"));
        assertThat(instance.getPattern(), is(".*"));
        assertThat(instance.isAutoRegisterHooks(), is(false));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
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
        // legacy API
        assertThat(instance.getBitbucketServerUrl(), is("https://bitbucket.test"));
        assertThat(instance.getCheckoutCredentialsId(), is("8b2e4f77-39c5-41a9-b63b-8d367350bfdf"));
        assertThat(instance.getPattern(), is(".*"));
        assertThat(instance.isAutoRegisterHooks(), is(true));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
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

    @Test
    public void given__instance__when__setBitbucketServerUrl_null__then__cloudUrlApplied() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setBitbucketServerUrl(null);
        assertThat(instance.getServerUrl(), is("https://bitbucket.org"));
        assertThat(instance.getBitbucketServerUrl(), is(nullValue()));
    }

    @Test
    public void given__instance__when__setBitbucketServerUrl_value__then__valueApplied() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setBitbucketServerUrl("https://bitbucket.test");
        assertThat(instance.getServerUrl(), is("https://bitbucket.test"));
        assertThat(instance.getBitbucketServerUrl(), is("https://bitbucket.test"));
    }

    @Test
    public void given__instance__when__setBitbucketServerUrl_value__then__valueNormalized() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setBitbucketServerUrl("https://bitbucket.test/foo/bar/../../");
        assertThat(instance.getServerUrl(), is("https://bitbucket.test"));
        assertThat(instance.getBitbucketServerUrl(), is("https://bitbucket.test"));
    }

    @Test
    public void given__instance__when__setBitbucketServerUrl_cloudUrl__then__valueApplied() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setBitbucketServerUrl("https://bitbucket.org");
        assertThat(instance.getServerUrl(), is("https://bitbucket.org"));
        assertThat(instance.getBitbucketServerUrl(), is(nullValue()));
    }

    @Test
    public void given__legacyCode__when__setPattern_default__then__patternSetAndTraitRemoved() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(new BranchDiscoveryTrait(true, false), new RegexSCMSourceFilterTrait("job.*"),
                new SSHCheckoutTrait("dummy")));
        assertThat(instance.getPattern(), is("job.*"));
        assertThat(instance.getTraits(), hasItem(instanceOf(RegexSCMSourceFilterTrait.class)));
        instance.setPattern(".*");
        assertThat(instance.getPattern(), is(".*"));
        assertThat(instance.getTraits(),
                not(hasItem(instanceOf(RegexSCMSourceFilterTrait.class))));

    }

    @Test
    public void given__legacyCode__when__setPattern_custom__then__patternSetAndTraitAdded() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(
                Arrays.asList(new BranchDiscoveryTrait(true, false), new SSHCheckoutTrait("dummy")));
        assertThat(instance.getPattern(), is(".*"));
        assertThat(instance.getTraits(),
                not(hasItem(instanceOf(RegexSCMSourceFilterTrait.class))));
        instance.setPattern("job.*");
        assertThat(instance.getPattern(), is("job.*"));
        assertThat(instance.getTraits(), hasItem(
                allOf(instanceOf(RegexSCMSourceFilterTrait.class), hasProperty("regex", is("job.*")))));

    }

    @Test
    public void given__legacyCode__when__setPattern_custom__then__patternSetAndTraitUpdated() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(new BranchDiscoveryTrait(true, false), new RegexSCMSourceFilterTrait("job.*"),
                new SSHCheckoutTrait("dummy")));
        assertThat(instance.getPattern(), is("job.*"));
        assertThat(instance.getTraits(), hasItem(instanceOf(RegexSCMSourceFilterTrait.class)));
        instance.setPattern("project.*");
        assertThat(instance.getPattern(), is("project.*"));
        assertThat(instance.getTraits(), not(hasItem(
                allOf(instanceOf(RegexSCMSourceFilterTrait.class), hasProperty("regex", is("job.*"))))));
        assertThat(instance.getTraits(), hasItem(
                allOf(instanceOf(RegexSCMSourceFilterTrait.class), hasProperty("regex", is("project.*")))));

    }

    @Test
    public void given__legacyCode__when__setAutoRegisterHooks_true__then__traitAdded() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(new BranchDiscoveryTrait(true, false), new RegexSCMSourceFilterTrait("job.*"),
                new SSHCheckoutTrait("dummy")));
        assertThat(instance.isAutoRegisterHooks(), is(true));
        assertThat(instance.getTraits(),
                not(hasItem(instanceOf(WebhookRegistrationTrait.class))));
        instance.setAutoRegisterHooks(true);
        assertThat(instance.isAutoRegisterHooks(), is(true));
        assertThat(instance.getTraits(), hasItem(
                allOf(instanceOf(WebhookRegistrationTrait.class), hasProperty("mode", is(WebhookRegistration.ITEM)))));
    }

    @Test
    public void given__legacyCode__when__setAutoRegisterHooks_changes__then__traitUpdated() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(new BranchDiscoveryTrait(true, false), new RegexSCMSourceFilterTrait("job.*"),
                new SSHCheckoutTrait("dummy")));
        assertThat(instance.isAutoRegisterHooks(), is(true));
        assertThat(instance.getTraits(),
                not(hasItem(instanceOf(WebhookRegistrationTrait.class))));
        instance.setAutoRegisterHooks(false);
        assertThat(instance.isAutoRegisterHooks(), is(false));
        assertThat(instance.getTraits(), hasItem(
                allOf(instanceOf(WebhookRegistrationTrait.class),
                        hasProperty("mode", is(WebhookRegistration.DISABLE)))));
    }

    @Test
    public void given__legacyCode__when__setAutoRegisterHooks_false__then__traitAdded() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(new BranchDiscoveryTrait(true, false), new RegexSCMSourceFilterTrait("job.*"),
                new SSHCheckoutTrait("dummy"), new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.isAutoRegisterHooks(), is(true));
        assertThat(instance.getTraits(), hasItem(
                allOf(instanceOf(WebhookRegistrationTrait.class),
                        hasProperty("mode", is(WebhookRegistration.SYSTEM)))));
        instance.setAutoRegisterHooks(true);
        assertThat(instance.isAutoRegisterHooks(), is(true));
        assertThat(instance.getTraits(), hasItem(
                allOf(instanceOf(WebhookRegistrationTrait.class), hasProperty("mode", is(WebhookRegistration.ITEM)))));
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_SAME__then__noTraitAdded() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getTraits(), not(hasItem(instanceOf(SSHCheckoutTrait.class))));
        instance.setCheckoutCredentialsId(BitbucketSCMSource.DescriptorImpl.SAME);
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getTraits(), not(hasItem(instanceOf(SSHCheckoutTrait.class))));
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_SAME__then__traitRemoved() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM),
                new SSHCheckoutTrait("value")));
        assertThat(instance.getCheckoutCredentialsId(), is("value"));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(SSHCheckoutTrait.class),
                hasProperty("credentialsId", is("value"))
        )));
        instance.setCheckoutCredentialsId(BitbucketSCMSource.DescriptorImpl.SAME);
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getTraits(), not(hasItem(instanceOf(SSHCheckoutTrait.class))));
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_null__then__noTraitAdded() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getTraits(), not(hasItem(instanceOf(SSHCheckoutTrait.class))));
        instance.setCheckoutCredentialsId(null);
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getTraits(), not(hasItem(instanceOf(SSHCheckoutTrait.class))));
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_null__then__traitRemoved() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM),
                new SSHCheckoutTrait("value")));
        assertThat(instance.getCheckoutCredentialsId(), is("value"));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(SSHCheckoutTrait.class),
                hasProperty("credentialsId", is("value"))
        )));
        instance.setCheckoutCredentialsId(null);
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getTraits(), not(hasItem(instanceOf(SSHCheckoutTrait.class))));
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_value__then__traitAdded() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getTraits(), not(hasItem(instanceOf(SSHCheckoutTrait.class))));
        instance.setCheckoutCredentialsId("value");
        assertThat(instance.getCheckoutCredentialsId(), is("value"));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(SSHCheckoutTrait.class),
                hasProperty("credentialsId", is("value"))
        )));
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_value__then__traitUpdated() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM),
                new SSHCheckoutTrait(null)));
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.ANONYMOUS));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(SSHCheckoutTrait.class),
                hasProperty("credentialsId", is(nullValue()))
        )));
        instance.setCheckoutCredentialsId("value");
        assertThat(instance.getCheckoutCredentialsId(), is("value"));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(SSHCheckoutTrait.class),
                hasProperty("credentialsId", is("value"))
        )));
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_ANONYMOUS__then__traitAdded() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getTraits(), not(hasItem(instanceOf(SSHCheckoutTrait.class))));
        instance.setCheckoutCredentialsId(BitbucketSCMSource.DescriptorImpl.ANONYMOUS);
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.ANONYMOUS));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(SSHCheckoutTrait.class),
                hasProperty("credentialsId", is(nullValue()))
        )));
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_ANONYMOUS__then__traitUpdated() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM),
                new SSHCheckoutTrait("value")));
        assertThat(instance.getCheckoutCredentialsId(), is("value"));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(SSHCheckoutTrait.class),
                hasProperty("credentialsId", is("value"))
        )));
        instance.setCheckoutCredentialsId(BitbucketSCMSource.DescriptorImpl.ANONYMOUS);
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.ANONYMOUS));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(SSHCheckoutTrait.class),
                hasProperty("credentialsId", is(nullValue()))
        )));
    }

    @Test
    public void given__legacyCode_withoutExcludes__when__setIncludes_default__then__traitRemoved() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WildcardSCMHeadFilterTrait("feature/*", ""),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("feature/*")),
                hasProperty("excludes", is(""))
        )));
        instance.setIncludes("*");
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), not(hasItem(
                instanceOf(WildcardSCMHeadFilterTrait.class)
        )));
    }

    @Test
    public void given__legacyCode_withoutExcludes__when__setIncludes_value__then__traitUpdated() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WildcardSCMHeadFilterTrait("feature/*", ""),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("feature/*")),
                hasProperty("excludes", is(""))
        )));
        instance.setIncludes("bug/*");
        assertThat(instance.getIncludes(), is("bug/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("bug/*")),
                hasProperty("excludes", is(""))
        )));
    }

    @Test
    public void given__legacyCode_withoutTrait__when__setIncludes_value__then__traitAdded() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), not(hasItem(
                instanceOf(WildcardSCMHeadFilterTrait.class)
        )));
        instance.setIncludes("feature/*");
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("feature/*")),
                hasProperty("excludes", is(""))
        )));
    }

    @Test
    public void given__legacyCode_withExcludes__when__setIncludes_default__then__traitUpdated() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WildcardSCMHeadFilterTrait("feature/*", "feature/ignore"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("feature/*")),
                hasProperty("excludes", is("feature/ignore"))
        )));
        instance.setIncludes("*");
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("*")),
                hasProperty("excludes", is("feature/ignore"))
        )));
    }

    @Test
    public void given__legacyCode_withExcludes__when__setIncludes_value__then__traitUpdated() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WildcardSCMHeadFilterTrait("feature/*", "feature/ignore"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("feature/*")),
                hasProperty("excludes", is("feature/ignore"))
        )));
        instance.setIncludes("bug/*");
        assertThat(instance.getIncludes(), is("bug/*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("bug/*")),
                hasProperty("excludes", is("feature/ignore"))
        )));
    }

    @Test
    public void given__legacyCode_withoutIncludes__when__setExcludes_default__then__traitRemoved() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WildcardSCMHeadFilterTrait("*", "feature/ignore"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("*")),
                hasProperty("excludes", is("feature/ignore"))
        )));
        instance.setExcludes("");
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), not(hasItem(
                instanceOf(WildcardSCMHeadFilterTrait.class)
        )));
    }

    @Test
    public void given__legacyCode_withoutIncludes__when__setExcludes_value__then__traitUpdated() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WildcardSCMHeadFilterTrait("*", "feature/ignore"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("*")),
                hasProperty("excludes", is("feature/ignore"))
        )));
        instance.setExcludes("bug/ignore");
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("bug/ignore"));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("*")),
                hasProperty("excludes", is("bug/ignore"))
        )));
    }

    @Test
    public void given__legacyCode_withoutTrait__when__setExcludes_value__then__traitAdded() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), not(hasItem(
                instanceOf(WildcardSCMHeadFilterTrait.class)
        )));
        instance.setExcludes("feature/ignore");
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("*")),
                hasProperty("excludes", is("feature/ignore"))
        )));
    }

    @Test
    public void given__legacyCode_withIncludes__when__setExcludes_default__then__traitUpdated() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WildcardSCMHeadFilterTrait("feature/*", "feature/ignore"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("feature/*")),
                hasProperty("excludes", is("feature/ignore"))
        )));
        instance.setExcludes("");
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("feature/*")),
                hasProperty("excludes", is(""))
        )));
    }

    @Test
    public void given__legacyCode_withIncludes__when__setExcludes_value__then__traitUpdated() {
        BitbucketSCMNavigator instance = new BitbucketSCMNavigator("test");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new RegexSCMSourceFilterTrait("job.*"),
                new WildcardSCMHeadFilterTrait("feature/*", ""),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("feature/*")),
                hasProperty("excludes", is(""))
        )));
        instance.setExcludes("feature/ignore");
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("feature/*")),
                hasProperty("excludes", is("feature/ignore"))
        )));
    }

}
