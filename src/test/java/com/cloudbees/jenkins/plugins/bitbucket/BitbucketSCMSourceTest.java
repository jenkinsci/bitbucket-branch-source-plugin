package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.endpoints.AbstractBitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.impl.trait.WildcardSCMHeadFilterTrait;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.displayurlapi.ClassicDisplayURLProvider;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class BitbucketSCMSourceTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
    @Rule
    public TestName currentTestName = new TestName();

    private BitbucketSCMSource load() {
        return load(currentTestName.getMethodName());
    }

    private BitbucketSCMSource load(String dataSet) {
        String path = getClass().getSimpleName() + "/" + dataSet + ".xml";
        URL url = getClass().getResource(path);
        BitbucketSCMSource bss =
                (BitbucketSCMSource) Jenkins.XSTREAM2.fromXML(url);
        return bss;
    }

    // Also initialize the external endpoint configuration storage for some
    // tests. Relevant XMLs are in a subdir of this class' fixtures.
    private void loadBEC() {
        BitbucketEndpointConfiguration bec = loadBEC(currentTestName.getMethodName());
        for (AbstractBitbucketEndpoint abe : bec.getEndpoints()) {
            if (abe != null) {
                BitbucketEndpointConfiguration.get().updateEndpoint(abe);
            }
        }
    }

    private BitbucketEndpointConfiguration loadBEC(String dataSet) {
        // Note to use original BitbucketSCMSourceTest::getClass() here to get proper paths
        String path = getClass().getSimpleName() + "/" +
                BitbucketEndpointConfiguration.class.getSimpleName() +
                "/" + dataSet + ".xml";
        URL url = getClass().getResource(path);
        BitbucketEndpointConfiguration bec =
                (BitbucketEndpointConfiguration) Jenkins.XSTREAM2.fromXML(url);
        return bec;
    }

    @Test
    public void modern() throws Exception {
        BitbucketSCMSource instance = load();
        assertThat(instance.getId(), is("e4d8c11a-0d24-472f-b86b-4b017c160e9a"));
        assertThat(instance.getServerUrl(), is("https://bitbucket.org"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getRepository(), is("stunning-adventure"));
        assertThat(instance.getCredentialsId(), is("curl"));
        assertThat(instance.getTraits(), is(Collections.<SCMSourceTrait>emptyList()));
        // Legacy API
        assertThat(instance.getBitbucketServerUrl(), is(nullValue()));
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.isAutoRegisterHook(), is(true));
    }

    @Test
    public void basic_cloud_git() throws Exception {
        BitbucketSCMSource instance = load();
        assertThat(instance.getId(), is("com.cloudbees.jenkins.plugins.bitbucket"
                + ".BitbucketSCMNavigator::https://bitbucket.org::cloudbeers::stunning-adventure"));
        assertThat(instance.getServerUrl(), is("https://bitbucket.org"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getRepository(), is("stunning-adventure"));
        assertThat(instance.getCredentialsId(), is("bitbucket-cloud"));
        assertThat(instance.getTraits(),
                containsInAnyOrder(
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2))
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2)),
                                hasProperty("trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustEveryone.class))
                        ),
                        Matchers.<SCMSourceTrait>instanceOf(PublicRepoPullRequestFilterTrait.class),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(WebhookRegistrationTrait.class),
                                hasProperty("mode", is(WebhookRegistration.DISABLE))
                        )
                )
        );
        // Legacy API
        assertThat(instance.getBitbucketServerUrl(), is(nullValue()));
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.isAutoRegisterHook(), is(false));
    }

    @Test
    public void basic_server() throws Exception {
        BitbucketSCMSource instance = load();
        assertThat(instance.getId(), is("com.cloudbees.jenkins.plugins.bitbucket"
                + ".BitbucketSCMNavigator::https://bitbucket.test::DUB::stunning-adventure"));
        assertThat(instance.getServerUrl(), is("https://bitbucket.test"));
        assertThat(instance.getRepoOwner(), is("DUB"));
        assertThat(instance.getRepository(), is("stunning-adventure"));
        assertThat(instance.getCredentialsId(), is("bb-beescloud"));
        assertThat(instance.getTraits(),
                containsInAnyOrder(
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2))
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2)),
                                hasProperty("trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustEveryone.class))
                        ),
                        Matchers.<SCMSourceTrait>instanceOf(PublicRepoPullRequestFilterTrait.class),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(WebhookRegistrationTrait.class),
                                hasProperty("mode", is(WebhookRegistration.DISABLE))
                        )
                )
        );
        // Legacy API
        assertThat(instance.getBitbucketServerUrl(), is("https://bitbucket.test"));
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.isAutoRegisterHook(), is(false));
    }

    @Test
    public void custom_checkout_credentials() throws Exception {
        BitbucketSCMSource instance = load();
        assertThat(instance.getId(), is("com.cloudbees.jenkins.plugins.bitbucket"
                + ".BitbucketSCMNavigator::https://bitbucket.org::cloudbeers::stunning-adventure"));
        assertThat(instance.getServerUrl(), is("https://bitbucket.org"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getRepository(), is("stunning-adventure"));
        assertThat(instance.getCredentialsId(), is("bitbucket-cloud"));
        assertThat(instance.getTraits(),
                containsInAnyOrder(
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2))
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2)),
                                hasProperty("trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustEveryone.class))
                        ),
                        Matchers.<SCMSourceTrait>instanceOf(PublicRepoPullRequestFilterTrait.class),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(WebhookRegistrationTrait.class),
                                hasProperty("mode", is(WebhookRegistration.DISABLE))
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                Matchers.instanceOf(SSHCheckoutTrait.class),
                                hasProperty("credentialsId", is("other-credentials"))
                        )
                )
        );
        // Legacy API
        assertThat(instance.getBitbucketServerUrl(), is(nullValue()));
        assertThat(instance.getCheckoutCredentialsId(), is("other-credentials"));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.isAutoRegisterHook(), is(false));
    }

    @Issue("JENKINS-45467")
    @Test
    public void same_checkout_credentials() throws Exception {
        BitbucketSCMSource instance = load();
        assertThat(instance.getId(), is("com.cloudbees.jenkins.plugins.bitbucket"
                + ".BitbucketSCMNavigator::https://bitbucket.org::cloudbeers::stunning-adventure"));
        assertThat(instance.getServerUrl(), is("https://bitbucket.org"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getRepository(), is("stunning-adventure"));
        assertThat(instance.getCredentialsId(), is("bitbucket-cloud"));
        assertThat(instance.getTraits(),
                containsInAnyOrder(
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2))
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2)),
                                hasProperty("trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustEveryone.class))
                        ),
                        Matchers.<SCMSourceTrait>instanceOf(PublicRepoPullRequestFilterTrait.class),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(WebhookRegistrationTrait.class),
                                hasProperty("mode", is(WebhookRegistration.DISABLE))
                        )
                )
        );
        // Legacy API
        assertThat(instance.getBitbucketServerUrl(), is(nullValue()));
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.isAutoRegisterHook(), is(false));
    }

    @Test
    public void exclude_branches() throws Exception {
        BitbucketSCMSource instance = load();
        assertThat(instance.getId(), is("com.cloudbees.jenkins.plugins.bitbucket"
                + ".BitbucketSCMNavigator::https://bitbucket.org::cloudbeers::stunning-adventure"));
        assertThat(instance.getServerUrl(), is("https://bitbucket.org"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getRepository(), is("stunning-adventure"));
        assertThat(instance.getCredentialsId(), is("bitbucket-cloud"));
        assertThat(instance.getTraits(),
                containsInAnyOrder(
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2))
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2)),
                                hasProperty("trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustEveryone.class))
                        ),
                        Matchers.<SCMSourceTrait>instanceOf(PublicRepoPullRequestFilterTrait.class),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(WildcardSCMHeadFilterTrait.class),
                                hasProperty("includes", is("*")),
                                hasProperty("excludes", is("main"))
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(WebhookRegistrationTrait.class),
                                hasProperty("mode", is(WebhookRegistration.DISABLE))
                        )
                )
        );
        // Legacy API
        assertThat(instance.getBitbucketServerUrl(), is(nullValue()));
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("main"));
        assertThat(instance.isAutoRegisterHook(), is(false));
    }

    @Test
    public void limit_branches() throws Exception {
        BitbucketSCMSource instance = load();
        assertThat(instance.getId(), is("com.cloudbees.jenkins.plugins.bitbucket"
                + ".BitbucketSCMNavigator::https://bitbucket.org::cloudbeers::stunning-adventure"));
        assertThat(instance.getServerUrl(), is("https://bitbucket.org"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getRepository(), is("stunning-adventure"));
        assertThat(instance.getCredentialsId(), is("bitbucket-cloud"));
        assertThat(instance.getTraits(),
                containsInAnyOrder(
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2))
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2)),
                                hasProperty("trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustEveryone.class))
                        ),
                        Matchers.<SCMSourceTrait>instanceOf(PublicRepoPullRequestFilterTrait.class),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(WildcardSCMHeadFilterTrait.class),
                                hasProperty("includes", is("feature/*")),
                                hasProperty("excludes", is(""))
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(WebhookRegistrationTrait.class),
                                hasProperty("mode", is(WebhookRegistration.DISABLE))
                        )
                )
        );
        // Legacy API
        assertThat(instance.getBitbucketServerUrl(), is(nullValue()));
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.isAutoRegisterHook(), is(false));
    }

    @Test
    public void register_hooks() throws Exception {
        BitbucketSCMSource instance = load();
        assertThat(instance.getId(), is("com.cloudbees.jenkins.plugins.bitbucket"
                + ".BitbucketSCMNavigator::https://bitbucket.org::cloudbeers::stunning-adventure"));
        assertThat(instance.getServerUrl(), is("https://bitbucket.org"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getRepository(), is("stunning-adventure"));
        assertThat(instance.getCredentialsId(), is("bitbucket-cloud"));
        assertThat(instance.getTraits(),
                containsInAnyOrder(
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2))
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2)),
                                hasProperty("trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustEveryone.class))
                        ),
                        Matchers.<SCMSourceTrait>instanceOf(PublicRepoPullRequestFilterTrait.class),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(WebhookRegistrationTrait.class),
                                hasProperty("mode", is(WebhookRegistration.ITEM))
                        )
                )
        );
        // Legacy API
        assertThat(instance.getBitbucketServerUrl(), is(nullValue()));
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.isAutoRegisterHook(), is(true));
    }

    @Test
    public void use_agent_checkout() throws Exception {
        BitbucketSCMSource instance = load();
        assertThat(instance.getId(), is("com.cloudbees.jenkins.plugins.bitbucket"
                + ".BitbucketSCMNavigator::https://bitbucket.org::cloudbeers::stunning-adventure"));
        assertThat(instance.getServerUrl(), is("https://bitbucket.org"));
        assertThat(instance.getRepoOwner(), is("cloudbeers"));
        assertThat(instance.getRepository(), is("stunning-adventure"));
        assertThat(instance.getCredentialsId(), is("bitbucket-cloud"));
        assertThat(instance.getTraits(),
                containsInAnyOrder(
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(true))
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(OriginPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2))
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(ForkPullRequestDiscoveryTrait.class),
                                hasProperty("strategyId", is(2)),
                                hasProperty("trust", instanceOf(ForkPullRequestDiscoveryTrait.TrustEveryone.class))
                        ),
                        Matchers.<SCMSourceTrait>instanceOf(PublicRepoPullRequestFilterTrait.class),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(WebhookRegistrationTrait.class),
                                hasProperty("mode", is(WebhookRegistration.DISABLE))
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                Matchers.instanceOf(SSHCheckoutTrait.class),
                                hasProperty("credentialsId", is(nullValue()))
                        )
                )
        );
        // Legacy API
        assertThat(instance.getBitbucketServerUrl(), is(nullValue()));
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.ANONYMOUS));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.isAutoRegisterHook(), is(false));
    }

    @Test
    public void given__instance__when__setTraits_empty__then__traitsEmpty() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Collections.<SCMSourceTrait>emptyList());
        assertThat(instance.getTraits(), is(Collections.<SCMSourceTrait>emptyList()));
    }

    @Test
    public void given__instance__when__setTraits__then__traitsSet() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(new BranchDiscoveryTrait(1),
                new WebhookRegistrationTrait(WebhookRegistration.DISABLE)));
        assertThat(instance.getTraits(),
                containsInAnyOrder(
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(BranchDiscoveryTrait.class),
                                hasProperty("buildBranch", is(true)),
                                hasProperty("buildBranchesWithPR", is(false))
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(WebhookRegistrationTrait.class),
                                hasProperty("mode", is(WebhookRegistration.DISABLE))
                        )
                )
        );
    }

    @Test
    public void given__instance__when__setServerUrl__then__urlNormalized() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setServerUrl("https://bitbucket.org:443/foo/../bar/../");
        assertThat(instance.getServerUrl(), is("https://bitbucket.org"));
    }

    @Test
    public void given__instance__when__setCredentials_empty__then__credentials_null() {
        BitbucketSCMSource instance = new BitbucketSCMSource( "testing", "test-repo");
        instance.setCredentialsId("");
        assertThat(instance.getCredentialsId(), is(nullValue()));
    }

    @Test
    public void given__instance__when__setCredentials_null__then__credentials_null() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setCredentialsId("");
        assertThat(instance.getCredentialsId(), is(nullValue()));
    }

    @Test
    public void given__instance__when__setCredentials__then__credentials_set() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setCredentialsId("test");
        assertThat(instance.getCredentialsId(), is("test"));
    }

    @Test
    public void given__instance__when__setBitbucketServerUrl_null__then__cloudUrlApplied() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setBitbucketServerUrl(null);
        assertThat(instance.getServerUrl(), is("https://bitbucket.org"));
        assertThat(instance.getBitbucketServerUrl(), is(nullValue()));
    }

    @Test
    public void given__instance__when__setBitbucketServerUrl_value__then__valueApplied() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setBitbucketServerUrl("https://bitbucket.test");
        assertThat(instance.getServerUrl(), is("https://bitbucket.test"));
        assertThat(instance.getBitbucketServerUrl(), is("https://bitbucket.test"));
    }

    @Test
    public void given__instance__when__setBitbucketServerUrl_value__then__valueNormalized() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setBitbucketServerUrl("https://bitbucket.test/foo/bar/../../");
        assertThat(instance.getServerUrl(), is("https://bitbucket.test"));
        assertThat(instance.getBitbucketServerUrl(), is("https://bitbucket.test"));
    }

    @Test
    public void given__instance__when__setBitbucketServerUrl_cloudUrl__then__valueApplied() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setBitbucketServerUrl("https://bitbucket.org");
        assertThat(instance.getServerUrl(), is("https://bitbucket.org"));
        assertThat(instance.getBitbucketServerUrl(), is(nullValue()));
    }

    @Test
    public void given__legacyCode__when__setAutoRegisterHook_true__then__traitAdded() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new SSHCheckoutTrait("dummy")));
        assertThat(instance.isAutoRegisterHook(), is(true));
        assertThat(instance.getTraits(),
                not(Matchers.<SCMSourceTrait>hasItem(instanceOf(WebhookRegistrationTrait.class))));
        instance.setAutoRegisterHook(true);
        assertThat(instance.isAutoRegisterHook(), is(true));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(
                allOf(instanceOf(WebhookRegistrationTrait.class), hasProperty("mode", is(WebhookRegistration.ITEM)))));
    }

    @Test
    public void given__legacyCode__when__setAutoRegisterHook_changes__then__traitUpdated() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(new BranchDiscoveryTrait(true, false),
                new SSHCheckoutTrait("dummy")));
        assertThat(instance.isAutoRegisterHook(), is(true));
        assertThat(instance.getTraits(),
                not(Matchers.<SCMSourceTrait>hasItem(instanceOf(WebhookRegistrationTrait.class))));
        instance.setAutoRegisterHook(false);
        assertThat(instance.isAutoRegisterHook(), is(false));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(
                allOf(instanceOf(WebhookRegistrationTrait.class),
                        hasProperty("mode", is(WebhookRegistration.DISABLE)))));
    }

    @Test
    public void given__legacyCode__when__setAutoRegisterHook_false__then__traitAdded() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(new BranchDiscoveryTrait(true, false),
                new SSHCheckoutTrait("dummy"), new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.isAutoRegisterHook(), is(true));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(
                allOf(instanceOf(WebhookRegistrationTrait.class),
                        hasProperty("mode", is(WebhookRegistration.SYSTEM)))));
        instance.setAutoRegisterHook(true);
        assertThat(instance.isAutoRegisterHook(), is(true));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(
                allOf(instanceOf(WebhookRegistrationTrait.class), hasProperty("mode", is(WebhookRegistration.ITEM)))));
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_SAME__then__noTraitAdded() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getTraits(), not(Matchers.<SCMSourceTrait>hasItem(instanceOf(SSHCheckoutTrait.class))));
        instance.setCheckoutCredentialsId(BitbucketSCMSource.DescriptorImpl.SAME);
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getTraits(), not(Matchers.<SCMSourceTrait>hasItem(instanceOf(SSHCheckoutTrait.class))));
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_SAME__then__traitRemoved() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM),
                new SSHCheckoutTrait("value")));
        assertThat(instance.getCheckoutCredentialsId(), is("value"));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(SSHCheckoutTrait.class),
                hasProperty("credentialsId", is("value"))
        )));
        instance.setCheckoutCredentialsId(BitbucketSCMSource.DescriptorImpl.SAME);
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getTraits(), not(Matchers.<SCMSourceTrait>hasItem(instanceOf(SSHCheckoutTrait.class))));
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_null__then__noTraitAdded() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getTraits(), not(Matchers.<SCMSourceTrait>hasItem(instanceOf(SSHCheckoutTrait.class))));
        instance.setCheckoutCredentialsId(null);
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getTraits(), not(Matchers.<SCMSourceTrait>hasItem(instanceOf(SSHCheckoutTrait.class))));
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_null__then__traitRemoved() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM),
                new SSHCheckoutTrait("value")));
        assertThat(instance.getCheckoutCredentialsId(), is("value"));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(SSHCheckoutTrait.class),
                hasProperty("credentialsId", is("value"))
        )));
        instance.setCheckoutCredentialsId(null);
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getTraits(), not(Matchers.<SCMSourceTrait>hasItem(instanceOf(SSHCheckoutTrait.class))));
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_value__then__traitAdded() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getTraits(), not(Matchers.<SCMSourceTrait>hasItem(instanceOf(SSHCheckoutTrait.class))));
        instance.setCheckoutCredentialsId("value");
        assertThat(instance.getCheckoutCredentialsId(), is("value"));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(SSHCheckoutTrait.class),
                hasProperty("credentialsId", is("value"))
        )));
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_value__then__traitUpdated() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM),
                new SSHCheckoutTrait(null)));
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.ANONYMOUS));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(SSHCheckoutTrait.class),
                hasProperty("credentialsId", is(nullValue()))
        )));
        instance.setCheckoutCredentialsId("value");
        assertThat(instance.getCheckoutCredentialsId(), is("value"));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(SSHCheckoutTrait.class),
                hasProperty("credentialsId", is("value"))
        )));
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_ANONYMOUS__then__traitAdded() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.SAME));
        assertThat(instance.getTraits(), not(Matchers.<SCMSourceTrait>hasItem(instanceOf(SSHCheckoutTrait.class))));
        instance.setCheckoutCredentialsId(BitbucketSCMSource.DescriptorImpl.ANONYMOUS);
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.ANONYMOUS));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(SSHCheckoutTrait.class),
                hasProperty("credentialsId", is(nullValue()))
        )));
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_ANONYMOUS__then__traitUpdated() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM),
                new SSHCheckoutTrait("value")));
        assertThat(instance.getCheckoutCredentialsId(), is("value"));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(SSHCheckoutTrait.class),
                hasProperty("credentialsId", is("value"))
        )));
        instance.setCheckoutCredentialsId(BitbucketSCMSource.DescriptorImpl.ANONYMOUS);
        assertThat(instance.getCheckoutCredentialsId(), is(BitbucketSCMSource.DescriptorImpl.ANONYMOUS));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(SSHCheckoutTrait.class),
                hasProperty("credentialsId", is(nullValue()))
        )));
    }

    @Test
    public void given__legacyCode_withoutExcludes__when__setIncludes_default__then__traitRemoved() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WildcardSCMHeadFilterTrait("feature/*", ""),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("feature/*")),
                hasProperty("excludes", is(""))
        )));
        instance.setIncludes("*");
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), not(Matchers.<SCMSourceTrait>hasItem(
                instanceOf(WildcardSCMHeadFilterTrait.class)
        )));
    }

    @Test
    public void given__legacyCode_withoutExcludes__when__setIncludes_value__then__traitUpdated() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WildcardSCMHeadFilterTrait("feature/*", ""),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("feature/*")),
                hasProperty("excludes", is(""))
        )));
        instance.setIncludes("bug/*");
        assertThat(instance.getIncludes(), is("bug/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("bug/*")),
                hasProperty("excludes", is(""))
        )));
    }

    @Test
    public void given__legacyCode_withoutTrait__when__setIncludes_value__then__traitAdded() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), not(Matchers.<SCMSourceTrait>hasItem(
                instanceOf(WildcardSCMHeadFilterTrait.class)
        )));
        instance.setIncludes("feature/*");
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("feature/*")),
                hasProperty("excludes", is(""))
        )));
    }

    @Test
    public void given__legacyCode_withExcludes__when__setIncludes_default__then__traitUpdated() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WildcardSCMHeadFilterTrait("feature/*", "feature/ignore"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("feature/*")),
                hasProperty("excludes", is("feature/ignore"))
        )));
        instance.setIncludes("*");
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("*")),
                hasProperty("excludes", is("feature/ignore"))
        )));
    }

    @Test
    public void given__legacyCode_withExcludes__when__setIncludes_value__then__traitUpdated() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WildcardSCMHeadFilterTrait("feature/*", "feature/ignore"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("feature/*")),
                hasProperty("excludes", is("feature/ignore"))
        )));
        instance.setIncludes("bug/*");
        assertThat(instance.getIncludes(), is("bug/*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("bug/*")),
                hasProperty("excludes", is("feature/ignore"))
        )));
    }

    @Test
    public void given__legacyCode_withoutIncludes__when__setExcludes_default__then__traitRemoved() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WildcardSCMHeadFilterTrait("*", "feature/ignore"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("*")),
                hasProperty("excludes", is("feature/ignore"))
        )));
        instance.setExcludes("");
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), not(Matchers.<SCMSourceTrait>hasItem(
                instanceOf(WildcardSCMHeadFilterTrait.class)
        )));
    }

    @Test
    public void given__legacyCode_withoutIncludes__when__setExcludes_value__then__traitUpdated() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WildcardSCMHeadFilterTrait("*", "feature/ignore"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("*")),
                hasProperty("excludes", is("feature/ignore"))
        )));
        instance.setExcludes("bug/ignore");
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("bug/ignore"));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("*")),
                hasProperty("excludes", is("bug/ignore"))
        )));
    }

    @Test
    public void given__legacyCode_withoutTrait__when__setExcludes_value__then__traitAdded() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), not(Matchers.<SCMSourceTrait>hasItem(
                instanceOf(WildcardSCMHeadFilterTrait.class)
        )));
        instance.setExcludes("feature/ignore");
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("*")),
                hasProperty("excludes", is("feature/ignore"))
        )));
    }

    @Test
    public void given__legacyCode_withIncludes__when__setExcludes_default__then__traitUpdated() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WildcardSCMHeadFilterTrait("feature/*", "feature/ignore"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("feature/*")),
                hasProperty("excludes", is("feature/ignore"))
        )));
        instance.setExcludes("");
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("feature/*")),
                hasProperty("excludes", is(""))
        )));
    }

    @Test
    public void given__legacyCode_withIncludes__when__setExcludes_value__then__traitUpdated() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Arrays.asList(
                new BranchDiscoveryTrait(true, false),
                new WildcardSCMHeadFilterTrait("feature/*", ""),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("feature/*")),
                hasProperty("excludes", is(""))
        )));
        instance.setExcludes("feature/ignore");
        assertThat(instance.getIncludes(), is("feature/*"));
        assertThat(instance.getExcludes(), is("feature/ignore"));
        assertThat(instance.getTraits(), Matchers.<SCMSourceTrait>hasItem(allOf(
                instanceOf(WildcardSCMHeadFilterTrait.class),
                hasProperty("includes", is("feature/*")),
                hasProperty("excludes", is("feature/ignore"))
        )));
    }

    // NOTE: The tests below require that a BitbucketEndpointConfiguration with
    // expected BB server and J root URLs exists, otherwise a dummy one is
    // instantiated via readResolveServerUrl() in BitbucketSCMSource::readResolve()
    // and then causes readResolve() call stack to revert the object from the
    // properly loaded values (from XML fixtures) into the default Root URL lookup,
    // as coded and intended (config does exist, so we honor it).
    @Test
    public void bitbucketJenkinsRootUrl_emptyDefaulted() throws Exception {
        loadBEC();
        BitbucketSCMSource instance = load();
        assertThat(instance.getEndpointJenkinsRootUrl(), is(ClassicDisplayURLProvider.get().getRoot()));

        // Verify that an empty custom URL keeps returning the
        // current global root URL (ending with a slash),
        // meaning "current value at the moment when we ask".
        JenkinsLocationConfiguration.get().setUrl("http://localjenkins:80");
        assertThat(instance.getEndpointJenkinsRootUrl(), is("http://localjenkins:80/"));

        JenkinsLocationConfiguration.get().setUrl("https://ourjenkins.master:8443/ci");
        assertThat(instance.getEndpointJenkinsRootUrl(), is("https://ourjenkins.master:8443/ci/"));
    }

    @Test
    public void bitbucketJenkinsRootUrl_goodAsIs() throws Exception {
        loadBEC();
        BitbucketSCMSource instance = load();
        assertThat(instance.getEndpointJenkinsRootUrl(), is("http://jenkins.test:8080/"));
    }

    @Test
    public void bitbucketJenkinsRootUrl_normalized() throws Exception {
        loadBEC();
        BitbucketSCMSource instance = load();
        assertThat(instance.getEndpointJenkinsRootUrl(), is("https://jenkins.test/"));
    }

    @Test
    public void bitbucketJenkinsRootUrl_slashed() throws Exception {
        loadBEC();
        BitbucketSCMSource instance = load();
        assertThat(instance.getEndpointJenkinsRootUrl(), is("https://jenkins.test/jenkins/"));
    }

    @Test
    public void bitbucketJenkinsRootUrl_notslashed() throws Exception {
        loadBEC();
        BitbucketSCMSource instance = load();
        assertThat(instance.getEndpointJenkinsRootUrl(), is("https://jenkins.test/jenkins/"));
    }
}
