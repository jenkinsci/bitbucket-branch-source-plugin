package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketPullRequestCommit;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.AbstractBitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.scm.SCM;
import java.net.URL;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.impl.trait.WildcardSCMHeadFilterTrait;
import org.assertj.core.api.ThrowingConsumer;
import org.jenkinsci.plugins.displayurlapi.ClassicDisplayURLProvider;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.assertj.core.api.Assertions.anyOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class BitbucketSCMSourceTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
    @Rule
    public TestName currentTestName = new TestName();

    private ThrowingConsumer<SCMSourceTrait> hasForkPRTrait = t -> assertThat(t)
            .isInstanceOfSatisfying(ForkPullRequestDiscoveryTrait.class, trait -> { //
                assertThat(trait.getStrategyId()).isEqualTo(2); //
                assertThat(trait.getTrust()).isInstanceOf(ForkPullRequestDiscoveryTrait.TrustEveryone.class); //
            });
    private ThrowingConsumer<SCMSourceTrait> hasBranchDiscoveryTrait = t -> assertThat(t)
            .isInstanceOfSatisfying(BranchDiscoveryTrait.class, trait -> { //
                assertThat(trait.isBuildBranch()).isTrue(); //
                assertThat(trait.isBuildBranchesWithPR()).isTrue(); //
            });
    private ThrowingConsumer<SCMSourceTrait> hasOriginPRTrait = t -> assertThat(t)
            .isInstanceOfSatisfying(OriginPullRequestDiscoveryTrait.class, trait -> assertThat(trait.getStrategyId()).isEqualTo(2));
    private ThrowingConsumer<SCMSourceTrait> hasPublicRepoTrait = trait -> assertThat(trait).isInstanceOf(PublicRepoPullRequestFilterTrait.class);
    private ThrowingConsumer<SCMSourceTrait> hasWebhookDisabledTrait = t -> assertThat(t)
            .isInstanceOfSatisfying(WebhookRegistrationTrait.class, trait -> assertThat(trait.getMode()).isEqualTo(WebhookRegistration.DISABLE));
    private ThrowingConsumer<SCMSourceTrait> hasWebhookItemTrait = t -> assertThat(t)
            .isInstanceOfSatisfying(WebhookRegistrationTrait.class, trait -> assertThat(trait.getMode()).isEqualTo(WebhookRegistration.ITEM));
    private ThrowingConsumer<SCMSourceTrait> hasWebhookSystemTrait = t -> assertThat(t)
            .isInstanceOfSatisfying(WebhookRegistrationTrait.class, trait -> assertThat(trait.getMode()).isEqualTo(WebhookRegistration.SYSTEM));
    private ThrowingConsumer<SCMSourceTrait> hasSSHTrait = t -> assertThat(t)
            .isInstanceOfSatisfying(SSHCheckoutTrait.class, trait -> assertThat(trait.getCredentialsId()).isEqualTo("other-credentials"));
    private ThrowingConsumer<SCMSourceTrait> hasSSHAnonymousTrait = t -> assertThat(t)
            .isInstanceOfSatisfying(SSHCheckoutTrait.class, trait -> assertThat(trait.getCredentialsId()).isNull());

    private BitbucketSCMSource load() {
        return load(currentTestName.getMethodName());
    }

    private BitbucketSCMSource load(String dataSet) {
        String path = getClass().getSimpleName() + "/" + dataSet + ".xml";
        URL url = getClass().getResource(path);
        BitbucketSCMSource bss = (BitbucketSCMSource) Jenkins.XSTREAM2.fromXML(url);
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
        assertThat(instance.getId()).isEqualTo("e4d8c11a-0d24-472f-b86b-4b017c160e9a");
        assertThat(instance.getServerUrl()).isEqualTo("https://bitbucket.org");
        assertThat(instance.getRepoOwner()).isEqualTo("cloudbeers");
        assertThat(instance.getRepository()).isEqualTo("stunning-adventure");
        assertThat(instance.getCredentialsId()).isEqualTo("curl");
        assertThat(instance.getTraits()).isEmpty();
        // Legacy API
        assertThat(instance.getBitbucketServerUrl()).isNull();
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo(BitbucketSCMSource.DescriptorImpl.SAME);
        assertThat(instance.getIncludes()).isEqualTo("*");
        assertThat(instance.getExcludes()).isEmpty();
        assertThat(instance.isAutoRegisterHook()).isTrue();
    }

    @Test
    public void basic_cloud_git() throws Exception {
        BitbucketSCMSource instance = load();
        assertThat(instance.getId()).isEqualTo("com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMNavigator::https://bitbucket.org::cloudbeers::stunning-adventure");
        assertThat(instance.getServerUrl()).isEqualTo("https://bitbucket.org");
        assertThat(instance.getRepoOwner()).isEqualTo("cloudbeers");
        assertThat(instance.getRepository()).isEqualTo("stunning-adventure");
        assertThat(instance.getCredentialsId()).isEqualTo("bitbucket-cloud");
        assertThat(instance.getTraits()).allSatisfy(anyOf( //
                hasBranchDiscoveryTrait, //
                hasOriginPRTrait, //
                hasPublicRepoTrait, //
                hasWebhookDisabledTrait, //
                hasForkPRTrait));
        // Legacy API
        assertThat(instance.getBitbucketServerUrl()).isNull();
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo(BitbucketSCMSource.DescriptorImpl.SAME);
        assertThat(instance.getIncludes()).isEqualTo("*");
        assertThat(instance.getExcludes()).isEmpty();
        assertThat(instance.isAutoRegisterHook()).isFalse();
    }

    @Test
    public void basic_server() throws Exception {
        BitbucketSCMSource instance = load();
        assertThat(instance.getId()).isEqualTo("com.cloudbees.jenkins.plugins.bitbucket"
                + ".BitbucketSCMNavigator::https://bitbucket.test::DUB::stunning-adventure");
        assertThat(instance.getServerUrl()).isEqualTo("https://bitbucket.test");
        assertThat(instance.getRepoOwner()).isEqualTo("DUB");
        assertThat(instance.getRepository()).isEqualTo("stunning-adventure");
        assertThat(instance.getCredentialsId()).isEqualTo("bb-beescloud");
        assertThat(instance.getTraits()).allSatisfy(anyOf( //
                hasBranchDiscoveryTrait, //
                hasOriginPRTrait, //
                hasPublicRepoTrait, //
                hasWebhookDisabledTrait, //
                hasForkPRTrait));
        // Legacy API
        assertThat(instance.getBitbucketServerUrl()).isEqualTo("https://bitbucket.test");
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo(BitbucketSCMSource.DescriptorImpl.SAME);
        assertThat(instance.getIncludes()).isEqualTo("*");
        assertThat(instance.getExcludes()).isEmpty();
        assertThat(instance.isAutoRegisterHook()).isFalse();
    }

    @Test
    public void custom_checkout_credentials() throws Exception {
        BitbucketSCMSource instance = load();
        assertThat(instance.getId()).isEqualTo("com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMNavigator::https://bitbucket.org::cloudbeers::stunning-adventure");
        assertThat(instance.getServerUrl()).isEqualTo("https://bitbucket.org");
        assertThat(instance.getRepoOwner()).isEqualTo("cloudbeers");
        assertThat(instance.getRepository()).isEqualTo("stunning-adventure");
        assertThat(instance.getCredentialsId()).isEqualTo("bitbucket-cloud");
        assertThat(instance.getTraits()).allSatisfy(anyOf( //
                hasBranchDiscoveryTrait, //
                hasOriginPRTrait, //
                hasPublicRepoTrait, //
                hasWebhookDisabledTrait, //
                hasForkPRTrait, //
                hasSSHTrait));
        // Legacy API
        assertThat(instance.getBitbucketServerUrl()).isNull();
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo("other-credentials");
        assertThat(instance.getIncludes()).isEqualTo("*");
        assertThat(instance.getExcludes()).isEmpty();
        assertThat(instance.isAutoRegisterHook()).isFalse();
    }

    @Issue("JENKINS-45467")
    @Test
    public void same_checkout_credentials() throws Exception {
        BitbucketSCMSource instance = load();
        assertThat(instance.getId()).isEqualTo("com.cloudbees.jenkins.plugins.bitbucket"
                + ".BitbucketSCMNavigator::https://bitbucket.org::cloudbeers::stunning-adventure");
        assertThat(instance.getServerUrl()).isEqualTo("https://bitbucket.org");
        assertThat(instance.getRepoOwner()).isEqualTo("cloudbeers");
        assertThat(instance.getRepository()).isEqualTo("stunning-adventure");
        assertThat(instance.getCredentialsId()).isEqualTo("bitbucket-cloud");
        assertThat(instance.getTraits()).allSatisfy(anyOf( //
                hasBranchDiscoveryTrait, //
                hasOriginPRTrait, //
                hasPublicRepoTrait, //
                hasWebhookDisabledTrait, //
                hasForkPRTrait));
        // Legacy API
        assertThat(instance.getBitbucketServerUrl()).isNull();
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo(BitbucketSCMSource.DescriptorImpl.SAME);
        assertThat(instance.getIncludes()).isEqualTo("*");
        assertThat(instance.getExcludes()).isEmpty();
        assertThat(instance.isAutoRegisterHook()).isFalse();
    }

    @Test
    public void exclude_branches() throws Exception {
        BitbucketSCMSource instance = load();
        assertThat(instance.getId()).isEqualTo("com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMNavigator::https://bitbucket.org::cloudbeers::stunning-adventure");
        assertThat(instance.getServerUrl()).isEqualTo("https://bitbucket.org");
        assertThat(instance.getRepoOwner()).isEqualTo("cloudbeers");
        assertThat(instance.getRepository()).isEqualTo("stunning-adventure");
        assertThat(instance.getCredentialsId()).isEqualTo("bitbucket-cloud");
        assertThat(instance.getTraits()).allSatisfy(anyOf( //
                hasBranchDiscoveryTrait, //
                hasOriginPRTrait, //
                hasPublicRepoTrait, //
                hasWebhookDisabledTrait, //
                hasForkPRTrait,
                trait -> assertThat(trait)
                .isInstanceOfSatisfying(WildcardSCMHeadFilterTrait.class, wildcardTrait -> {
                    assertThat(wildcardTrait.getIncludes()).isEqualTo("*");
                    assertThat(wildcardTrait.getExcludes()).isEqualTo("main");
                })));
        // Legacy API
        assertThat(instance.getBitbucketServerUrl()).isNull();
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo(BitbucketSCMSource.DescriptorImpl.SAME);
        assertThat(instance.getIncludes()).isEqualTo("*");
        assertThat(instance.getExcludes()).isEqualTo("main");
        assertThat(instance.isAutoRegisterHook()).isFalse();
    }

    @Test
    public void limit_branches() throws Exception {
        BitbucketSCMSource instance = load();
        assertThat(instance.getId()).isEqualTo("com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMNavigator::https://bitbucket.org::cloudbeers::stunning-adventure");
        assertThat(instance.getServerUrl()).isEqualTo("https://bitbucket.org");
        assertThat(instance.getRepoOwner()).isEqualTo("cloudbeers");
        assertThat(instance.getRepository()).isEqualTo("stunning-adventure");
        assertThat(instance.getCredentialsId()).isEqualTo("bitbucket-cloud");
        assertThat(instance.getTraits()).allSatisfy(anyOf( //
                hasBranchDiscoveryTrait, //
                hasOriginPRTrait, //
                hasPublicRepoTrait, //
                hasWebhookDisabledTrait, //
                hasForkPRTrait,
                trait -> assertThat(trait)
                .isInstanceOfSatisfying(WildcardSCMHeadFilterTrait.class, wildcardTrait -> {
                    assertThat(wildcardTrait.getIncludes()).isEqualTo("feature/*");
                    assertThat(wildcardTrait.getExcludes()).isEmpty();
                })));
        // Legacy API
        assertThat(instance.getBitbucketServerUrl()).isNull();
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo(BitbucketSCMSource.DescriptorImpl.SAME);
        assertThat(instance.getIncludes()).isEqualTo("feature/*");
        assertThat(instance.getExcludes()).isEmpty();
        assertThat(instance.isAutoRegisterHook()).isFalse();
    }

    @Test
    public void register_hooks() throws Exception {
        BitbucketSCMSource instance = load();
        assertThat(instance.getId()).isEqualTo("com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMNavigator::https://bitbucket.org::cloudbeers::stunning-adventure");
        assertThat(instance.getServerUrl()).isEqualTo("https://bitbucket.org");
        assertThat(instance.getRepoOwner()).isEqualTo("cloudbeers");
        assertThat(instance.getRepository()).isEqualTo("stunning-adventure");
        assertThat(instance.getCredentialsId()).isEqualTo("bitbucket-cloud");
        assertThat(instance.getTraits()).allSatisfy(anyOf( //
                hasBranchDiscoveryTrait, //
                hasOriginPRTrait, //
                hasForkPRTrait,
                hasPublicRepoTrait, //
                hasWebhookItemTrait));
        // Legacy API
        assertThat(instance.getBitbucketServerUrl()).isNull();
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo(BitbucketSCMSource.DescriptorImpl.SAME);
        assertThat(instance.getIncludes()).isEqualTo("*");
        assertThat(instance.getExcludes()).isEmpty();
        assertThat(instance.isAutoRegisterHook()).isTrue();
    }

    @Test
    public void use_agent_checkout() {
        BitbucketSCMSource instance = load();
        assertThat(instance.getId()).isEqualTo("com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMNavigator::https://bitbucket.org::cloudbeers::stunning-adventure");
        assertThat(instance.getServerUrl()).isEqualTo("https://bitbucket.org");
        assertThat(instance.getRepoOwner()).isEqualTo("cloudbeers");
        assertThat(instance.getRepository()).isEqualTo("stunning-adventure");
        assertThat(instance.getCredentialsId()).isEqualTo("bitbucket-cloud");
        assertThat(instance.getTraits()).allSatisfy(anyOf( //
                hasBranchDiscoveryTrait, //
                hasOriginPRTrait, //
                hasForkPRTrait, //
                hasPublicRepoTrait, //
                hasWebhookDisabledTrait, //
                hasSSHAnonymousTrait));
        // Legacy API
        assertThat(instance.getBitbucketServerUrl()).isNull();
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo(BitbucketSCMSource.DescriptorImpl.ANONYMOUS);
        assertThat(instance.getIncludes()).isEqualTo("*");
        assertThat(instance.getExcludes()).isEmpty();
        assertThat(instance.isAutoRegisterHook()).isFalse();
    }

    @Test
    public void test_that_clone_url_does_not_contains_username() {
        BranchSCMHead head = new BranchSCMHead("master");
        BitbucketPullRequestCommit commit = new BitbucketPullRequestCommit();
        commit.setHash("046d9a3c1532acf4cf08fe93235c00e4d673c1d2");
        commit.setDate(new Date());

        BitbucketSCMSource instance = new BitbucketSCMSource("amuniz", "test-repo");
        BitbucketMockApiFactory.add(instance.getServerUrl(), BitbucketIntegrationClientFactory.getApiMockClient(instance.getServerUrl()));
        SCM scm = instance.build(head, new BitbucketGitSCMRevision(head, commit));
        assertThat(scm).isInstanceOf(GitSCM.class);
        GitSCM gitSCM = (GitSCM) scm;
        assertThat(gitSCM.getUserRemoteConfigs()).isNotEmpty()
            .element(0)
            .satisfies(urc -> {
                assertThat(urc.getUrl()).isEqualTo("https://bitbucket.org/amuniz/test-repos.git");
            });
    }

    @Test
    public void given__instance__when__setTraits_empty__then__traitsEmpty() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(Collections.<SCMSourceTrait>emptyList());
        assertThat(instance.getTraits()).isEmpty();
    }

    @Test
    public void given__instance__when__setTraits__then__traitsSet() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(List.of(new BranchDiscoveryTrait(1),
                new WebhookRegistrationTrait(WebhookRegistration.DISABLE)));
        assertThat(instance.getTraits()).allSatisfy(anyOf( //
                trait -> assertThat(trait)
                .isInstanceOfSatisfying(BranchDiscoveryTrait.class, branchTrait -> { //
                    assertThat(branchTrait.isBuildBranch()).isTrue(); //
                    assertThat(branchTrait.isBuildBranchesWithPR()).isFalse(); //
                }), //
                hasWebhookDisabledTrait));
    }

    @Test
    public void given__instance__when__setServerUrl__then__urlNormalized() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setServerUrl("https://bitbucket.org:443/foo/../bar/../");
        assertThat(instance.getServerUrl()).isEqualTo("https://bitbucket.org");
    }

    @Test
    public void given__instance__when__setCredentials_empty__then__credentials_null() {
        BitbucketSCMSource instance = new BitbucketSCMSource( "testing", "test-repo");
        instance.setCredentialsId("");
        assertThat(instance.getCredentialsId()).isNull();
    }

    @Test
    public void given__instance__when__setCredentials_null__then__credentials_null() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setCredentialsId("");
        assertThat(instance.getCredentialsId()).isNull();
    }

    @Test
    public void given__instance__when__setCredentials__then__credentials_set() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setCredentialsId("test");
        assertThat(instance.getCredentialsId()).isEqualTo("test");
    }

    @Test
    public void given__instance__when__setBitbucketServerUrl_null__then__cloudUrlApplied() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setBitbucketServerUrl(null);
        assertThat(instance.getServerUrl()).isEqualTo("https://bitbucket.org");
        assertThat(instance.getBitbucketServerUrl()).isNull();
    }

    @Test
    public void given__instance__when__setBitbucketServerUrl_value__then__valueApplied() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setBitbucketServerUrl("https://bitbucket.test");
        assertThat(instance.getServerUrl()).isEqualTo("https://bitbucket.test");
        assertThat(instance.getBitbucketServerUrl()).isEqualTo("https://bitbucket.test");
    }

    @Test
    public void given__instance__when__setBitbucketServerUrl_value__then__valueNormalized() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setBitbucketServerUrl("https://bitbucket.test/foo/bar/../../");
        assertThat(instance.getServerUrl()).isEqualTo("https://bitbucket.test");
        assertThat(instance.getBitbucketServerUrl()).isEqualTo("https://bitbucket.test");
    }

    @Test
    public void given__instance__when__setBitbucketServerUrl_cloudUrl__then__valueApplied() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setBitbucketServerUrl("https://bitbucket.org");
        assertThat(instance.getServerUrl()).isEqualTo("https://bitbucket.org");
        assertThat(instance.getBitbucketServerUrl()).isNull();
    }

    @Test
    public void given__legacyCode__when__setAutoRegisterHook_true__then__traitAdded() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(List.of(
                new BranchDiscoveryTrait(true, false),
                new SSHCheckoutTrait("dummy")));
        assertThat(instance.isAutoRegisterHook()).isEqualTo(true);
        assertThat(instance.getTraits()).doesNotHaveAnyElementsOfTypes(WebhookRegistrationTrait.class);
        instance.setAutoRegisterHook(true);
        assertThat(instance.isAutoRegisterHook()).isEqualTo(true);
        assertThat(instance.getTraits()).anySatisfy(hasWebhookItemTrait);
    }

    @Test
    public void given__legacyCode__when__setAutoRegisterHook_changes__then__traitUpdated() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(List.of(new BranchDiscoveryTrait(true, false),
                new SSHCheckoutTrait("dummy")));
        assertThat(instance.isAutoRegisterHook()).isEqualTo(true);
        assertThat(instance.getTraits()).doesNotHaveAnyElementsOfTypes(WebhookRegistrationTrait.class);
        instance.setAutoRegisterHook(false);
        assertThat(instance.isAutoRegisterHook()).isEqualTo(false);
        assertThat(instance.getTraits()).anySatisfy(hasWebhookDisabledTrait);
    }

    @Test
    public void given__legacyCode__when__setAutoRegisterHook_false__then__traitAdded() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(List.of(new BranchDiscoveryTrait(true, false),
                new SSHCheckoutTrait("dummy"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.isAutoRegisterHook()).isEqualTo(true);
        assertThat(instance.getTraits()).anySatisfy(hasWebhookSystemTrait);
        instance.setAutoRegisterHook(true);
        assertThat(instance.isAutoRegisterHook()).isEqualTo(true);
        assertThat(instance.getTraits()).anySatisfy(hasWebhookItemTrait);
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_SAME__then__noTraitAdded() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(List.of(
                new BranchDiscoveryTrait(true, false),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo(BitbucketSCMSource.DescriptorImpl.SAME);
        assertThat(instance.getTraits()).doesNotHaveAnyElementsOfTypes(SSHCheckoutTrait.class);
        instance.setCheckoutCredentialsId(BitbucketSCMSource.DescriptorImpl.SAME);
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo(BitbucketSCMSource.DescriptorImpl.SAME);
        assertThat(instance.getTraits()).doesNotHaveAnyElementsOfTypes(SSHCheckoutTrait.class);
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_SAME__then__traitRemoved() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(List.of(
                new BranchDiscoveryTrait(true, false),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM),
                new SSHCheckoutTrait("value")));
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo("value");
        assertThat(instance.getTraits()).anySatisfy(trait -> assertThat(trait)
            .isInstanceOfSatisfying(SSHCheckoutTrait.class, sshTrait -> assertThat(sshTrait.getCredentialsId()).isEqualTo("value")));
        instance.setCheckoutCredentialsId(BitbucketSCMSource.DescriptorImpl.SAME);
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo(BitbucketSCMSource.DescriptorImpl.SAME);
        assertThat(instance.getTraits()).doesNotHaveAnyElementsOfTypes(SSHCheckoutTrait.class);
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_null__then__noTraitAdded() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(List.of(
                new BranchDiscoveryTrait(true, false),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo(BitbucketSCMSource.DescriptorImpl.SAME);
        assertThat(instance.getTraits()).doesNotHaveAnyElementsOfTypes(SSHCheckoutTrait.class);
        instance.setCheckoutCredentialsId(null);
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo(BitbucketSCMSource.DescriptorImpl.SAME);
        assertThat(instance.getTraits()).doesNotHaveAnyElementsOfTypes(SSHCheckoutTrait.class);
        assertThat(instance.getTraits()).doesNotHaveAnyElementsOfTypes(SSHCheckoutTrait.class);
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_null__then__traitRemoved() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(List.of(
                new BranchDiscoveryTrait(true, false),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM),
                new SSHCheckoutTrait("other-credentials")));
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo("other-credentials");
        assertThat(instance.getTraits()).anySatisfy(hasSSHTrait);
        instance.setCheckoutCredentialsId(null);
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo(BitbucketSCMSource.DescriptorImpl.SAME);
        assertThat(instance.getTraits()).doesNotHaveAnyElementsOfTypes(SSHCheckoutTrait.class);
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_value__then__traitAdded() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(List.of(
                new BranchDiscoveryTrait(true, false),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo(BitbucketSCMSource.DescriptorImpl.SAME);
        assertThat(instance.getTraits()).doesNotHaveAnyElementsOfTypes(SSHCheckoutTrait.class);
        instance.setCheckoutCredentialsId("other-credentials");
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo("other-credentials");
        assertThat(instance.getTraits()).anySatisfy(hasSSHTrait);
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_value__then__traitUpdated() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(List.of(
                new BranchDiscoveryTrait(true, false),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM),
                new SSHCheckoutTrait(null)));
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo(BitbucketSCMSource.DescriptorImpl.ANONYMOUS);
        assertThat(instance.getTraits()).anySatisfy(hasSSHAnonymousTrait);
        instance.setCheckoutCredentialsId("other-credentials");
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo("other-credentials");
        assertThat(instance.getTraits()).anySatisfy(hasSSHTrait);
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_ANONYMOUS__then__traitAdded() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(List.of(
                new BranchDiscoveryTrait(true, false),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo(BitbucketSCMSource.DescriptorImpl.SAME);
        assertThat(instance.getTraits()).doesNotHaveAnyElementsOfTypes(SSHCheckoutTrait.class);
        instance.setCheckoutCredentialsId(BitbucketSCMSource.DescriptorImpl.ANONYMOUS);
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo(BitbucketSCMSource.DescriptorImpl.ANONYMOUS);
        assertThat(instance.getTraits()).anySatisfy(hasSSHAnonymousTrait);
    }

    @Test
    public void given__legacyCode__when__setCheckoutCredentials_ANONYMOUS__then__traitUpdated() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(List.of(
                new BranchDiscoveryTrait(true, false),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM),
                new SSHCheckoutTrait("other-credentials")));
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo("other-credentials");
        assertThat(instance.getTraits()).anySatisfy(hasSSHTrait);
        instance.setCheckoutCredentialsId(BitbucketSCMSource.DescriptorImpl.ANONYMOUS);
        assertThat(instance.getCheckoutCredentialsId()).isEqualTo(BitbucketSCMSource.DescriptorImpl.ANONYMOUS);
        assertThat(instance.getTraits()).anySatisfy(hasSSHAnonymousTrait);

    }

    @Test
    public void given__legacyCode_withoutExcludes__when__setIncludes_default__then__traitRemoved() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(List.of(
                new BranchDiscoveryTrait(true, false),
                new WildcardSCMHeadFilterTrait("feature/*", ""),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes()).isEqualTo("feature/*");
        assertThat(instance.getExcludes()).isEmpty();
        assertThat(instance.getTraits()).anySatisfy(trait -> assertThat(trait)
            .isInstanceOfSatisfying(WildcardSCMHeadFilterTrait.class, wildcarTrait -> { //
                assertThat(wildcarTrait.getIncludes()).isEqualTo("feature/*"); //
                assertThat(wildcarTrait.getExcludes()).isEmpty(); //
            }));
        instance.setIncludes("*");
        assertThat(instance.getIncludes()).isEqualTo("*");
        assertThat(instance.getExcludes()).isEmpty();
        assertThat(instance.getTraits()).doesNotHaveAnyElementsOfTypes(WildcardSCMHeadFilterTrait.class);
    }

    @Test
    public void given__legacyCode_withoutExcludes__when__setIncludes_value__then__traitUpdated() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(List.of(
                new BranchDiscoveryTrait(true, false),
                new WildcardSCMHeadFilterTrait("feature/*", ""),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes()).isEqualTo("feature/*");
        assertThat(instance.getExcludes()).isEmpty();
        assertThat(instance.getTraits()).anySatisfy(trait -> assertThat(trait)
                .isInstanceOfSatisfying(WildcardSCMHeadFilterTrait.class, wildcarTrait -> { //
                    assertThat(wildcarTrait.getIncludes()).isEqualTo("feature/*"); //
                    assertThat(wildcarTrait.getExcludes()).isEmpty(); //
                }));
        instance.setIncludes("bug/*");
        assertThat(instance.getIncludes()).isEqualTo("bug/*");
        assertThat(instance.getExcludes()).isEmpty();
        assertThat(instance.getTraits()).anySatisfy(trait -> assertThat(trait)
                .isInstanceOfSatisfying(WildcardSCMHeadFilterTrait.class, wildcarTrait -> { //
                    assertThat(wildcarTrait.getIncludes()).isEqualTo("bug/*"); //
                    assertThat(wildcarTrait.getExcludes()).isEmpty(); //
                }));
    }

    @Test
    public void given__legacyCode_withoutTrait__when__setIncludes_value__then__traitAdded() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(List.of(
                new BranchDiscoveryTrait(true, false),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes()).isEqualTo("*");
        assertThat(instance.getExcludes()).isEmpty();
        assertThat(instance.getTraits()).doesNotHaveAnyElementsOfTypes(WildcardSCMHeadFilterTrait.class);
        instance.setIncludes("feature/*");
        assertThat(instance.getIncludes()).isEqualTo("feature/*");
        assertThat(instance.getExcludes()).isEqualTo("");
        assertThat(instance.getTraits()).anySatisfy(trait -> assertThat(trait)
                .isInstanceOfSatisfying(WildcardSCMHeadFilterTrait.class, wildcarTrait -> { //
                    assertThat(wildcarTrait.getIncludes()).isEqualTo("feature/*"); //
                    assertThat(wildcarTrait.getExcludes()).isEmpty(); //
                }));
    }

    @Test
    public void given__legacyCode_withExcludes__when__setIncludes_default__then__traitUpdated() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(List.of(
                new BranchDiscoveryTrait(true, false),
                new WildcardSCMHeadFilterTrait("feature/*", "feature/ignore"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes()).isEqualTo("feature/*");
        assertThat(instance.getExcludes()).isEqualTo("feature/ignore");
        assertThat(instance.getTraits()).anySatisfy(trait -> assertThat(trait)
                .isInstanceOfSatisfying(WildcardSCMHeadFilterTrait.class, wildcarTrait -> { //
                    assertThat(wildcarTrait.getIncludes()).isEqualTo("feature/*"); //
                    assertThat(wildcarTrait.getExcludes()).isEqualTo("feature/ignore"); //
                }));
        instance.setIncludes("*");
        assertThat(instance.getIncludes()).isEqualTo("*");
        assertThat(instance.getExcludes()).isEqualTo("feature/ignore");
        assertThat(instance.getTraits()).anySatisfy(trait -> assertThat(trait)
                .isInstanceOfSatisfying(WildcardSCMHeadFilterTrait.class, wildcarTrait -> { //
                    assertThat(wildcarTrait.getIncludes()).isEqualTo("*"); //
                    assertThat(wildcarTrait.getExcludes()).isEqualTo("feature/ignore"); //
                }));
    }

    @Test
    public void given__legacyCode_withExcludes__when__setIncludes_value__then__traitUpdated() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(List.of(
                new BranchDiscoveryTrait(true, false),
                new WildcardSCMHeadFilterTrait("feature/*", "feature/ignore"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes()).isEqualTo("feature/*");
        assertThat(instance.getExcludes()).isEqualTo("feature/ignore");
        assertThat(instance.getTraits()).anySatisfy(trait -> assertThat(trait)
                .isInstanceOfSatisfying(WildcardSCMHeadFilterTrait.class, wildcarTrait -> { //
                    assertThat(wildcarTrait.getIncludes()).isEqualTo("feature/*"); //
                    assertThat(wildcarTrait.getExcludes()).isEqualTo("feature/ignore"); //
                }));

        instance.setIncludes("bug/*");
        assertThat(instance.getIncludes()).isEqualTo("bug/*");
        assertThat(instance.getExcludes()).isEqualTo("feature/ignore");
        assertThat(instance.getTraits()).anySatisfy(trait -> assertThat(trait)
                .isInstanceOfSatisfying(WildcardSCMHeadFilterTrait.class, wildcarTrait -> { //
                    assertThat(wildcarTrait.getIncludes()).isEqualTo("bug/*"); //
                    assertThat(wildcarTrait.getExcludes()).isEqualTo("feature/ignore"); //
                }));
    }

    @Test
    public void given__legacyCode_withoutIncludes__when__setExcludes_default__then__traitRemoved() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(List.of(
                new BranchDiscoveryTrait(true, false),
                new WildcardSCMHeadFilterTrait("*", "feature/ignore"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes()).isEqualTo("*");
        assertThat(instance.getExcludes()).isEqualTo("feature/ignore");
        assertThat(instance.getTraits()).anySatisfy(trait -> assertThat(trait)
                .isInstanceOfSatisfying(WildcardSCMHeadFilterTrait.class, wildcarTrait -> { //
                    assertThat(wildcarTrait.getIncludes()).isEqualTo("*"); //
                    assertThat(wildcarTrait.getExcludes()).isEqualTo("feature/ignore"); //
                }));
        instance.setExcludes("");
        assertThat(instance.getIncludes()).isEqualTo("*");
        assertThat(instance.getExcludes()).isEmpty();
        assertThat(instance.getTraits()).doesNotHaveAnyElementsOfTypes(SSHCheckoutTrait.class);
    }

    @Test
    public void given__legacyCode_withoutIncludes__when__setExcludes_value__then__traitUpdated() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(List.of(
                new BranchDiscoveryTrait(true, false),
                new WildcardSCMHeadFilterTrait("*", "feature/ignore"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes()).isEqualTo("*");
        assertThat(instance.getExcludes()).isEqualTo("feature/ignore");
        assertThat(instance.getTraits()).anySatisfy(trait -> assertThat(trait)
                .isInstanceOfSatisfying(WildcardSCMHeadFilterTrait.class, wildcarTrait -> { //
                    assertThat(wildcarTrait.getIncludes()).isEqualTo("*"); //
                    assertThat(wildcarTrait.getExcludes()).isEqualTo("feature/ignore"); //
                }));

        instance.setExcludes("bug/ignore");
        assertThat(instance.getIncludes()).isEqualTo("*");
        assertThat(instance.getExcludes()).isEqualTo("bug/ignore");
        assertThat(instance.getTraits()).anySatisfy(trait -> assertThat(trait)
                .isInstanceOfSatisfying(WildcardSCMHeadFilterTrait.class, wildcarTrait -> { //
                    assertThat(wildcarTrait.getIncludes()).isEqualTo("*"); //
                    assertThat(wildcarTrait.getExcludes()).isEqualTo("bug/ignore"); //
                }));
    }

    @Test
    public void given__legacyCode_withoutTrait__when__setExcludes_value__then__traitAdded() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(List.of(
                new BranchDiscoveryTrait(true, false),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes()).isEqualTo("*");
        assertThat(instance.getExcludes()).isEmpty();
        assertThat(instance.getTraits()).doesNotHaveAnyElementsOfTypes(WildcardSCMHeadFilterTrait.class);
        instance.setExcludes("feature/ignore");
        assertThat(instance.getIncludes()).isEqualTo("*");
        assertThat(instance.getExcludes()).isEqualTo("feature/ignore");
        assertThat(instance.getTraits()).anySatisfy(trait -> assertThat(trait)
                .isInstanceOfSatisfying(WildcardSCMHeadFilterTrait.class, wildcarTrait -> { //
                    assertThat(wildcarTrait.getIncludes()).isEqualTo("*"); //
                    assertThat(wildcarTrait.getExcludes()).isEqualTo("feature/ignore"); //
                }));
    }

    @Test
    public void given__legacyCode_withIncludes__when__setExcludes_default__then__traitUpdated() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(List.of(
                new BranchDiscoveryTrait(true, false),
                new WildcardSCMHeadFilterTrait("feature/*", "feature/ignore"),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes()).isEqualTo("feature/*");
        assertThat(instance.getExcludes()).isEqualTo("feature/ignore");
        assertThat(instance.getTraits()).anySatisfy(trait -> assertThat(trait)
                .isInstanceOfSatisfying(WildcardSCMHeadFilterTrait.class, wildcarTrait -> { //
                    assertThat(wildcarTrait.getIncludes()).isEqualTo("feature/*"); //
                    assertThat(wildcarTrait.getExcludes()).isEqualTo("feature/ignore"); //
                }));

        instance.setExcludes("");
        assertThat(instance.getIncludes()).isEqualTo("feature/*");
        assertThat(instance.getExcludes()).isEmpty();
        assertThat(instance.getTraits()).anySatisfy(trait -> assertThat(trait)
                .isInstanceOfSatisfying(WildcardSCMHeadFilterTrait.class, wildcarTrait -> { //
                    assertThat(wildcarTrait.getIncludes()).isEqualTo("feature/*"); //
                    assertThat(wildcarTrait.getExcludes()).isEmpty(); //
                }));
    }

    @Test
    public void given__legacyCode_withIncludes__when__setExcludes_value__then__traitUpdated() {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setTraits(List.of(
                new BranchDiscoveryTrait(true, false),
                new WildcardSCMHeadFilterTrait("feature/*", ""),
                new WebhookRegistrationTrait(WebhookRegistration.SYSTEM)));
        assertThat(instance.getIncludes()).isEqualTo("feature/*");
        assertThat(instance.getExcludes()).isEmpty();
        assertThat(instance.getTraits()).anySatisfy(trait -> assertThat(trait)
                .isInstanceOfSatisfying(WildcardSCMHeadFilterTrait.class, wildcarTrait -> { //
                    assertThat(wildcarTrait.getIncludes()).isEqualTo("feature/*"); //
                    assertThat(wildcarTrait.getExcludes()).isEmpty(); //
                }));

        instance.setExcludes("feature/ignore");
        assertThat(instance.getIncludes()).isEqualTo("feature/*");
        assertThat(instance.getExcludes()).isEqualTo("feature/ignore");
        assertThat(instance.getTraits()).anySatisfy(trait -> assertThat(trait)
                .isInstanceOfSatisfying(WildcardSCMHeadFilterTrait.class, wildcarTrait -> { //
                    assertThat(wildcarTrait.getIncludes()).isEqualTo("feature/*"); //
                    assertThat(wildcarTrait.getExcludes()).isEqualTo("feature/ignore"); //
                }));
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
        assertThat(instance.getEndpointJenkinsRootUrl()).isEqualTo(ClassicDisplayURLProvider.get().getRoot());

        // Verify that an empty custom URL keeps returning the
        // current global root URL (ending with a slash),
        // meaning "current value at the moment when we ask".
        JenkinsLocationConfiguration.get().setUrl("http://localjenkins:80");
        assertThat(instance.getEndpointJenkinsRootUrl()).isEqualTo("http://localjenkins:80/");

        JenkinsLocationConfiguration.get().setUrl("https://ourjenkins.master:8443/ci");
        assertThat(instance.getEndpointJenkinsRootUrl()).isEqualTo("https://ourjenkins.master:8443/ci/");
    }

    @Test
    public void bitbucketJenkinsRootUrl_goodAsIs() {
        loadBEC();
        BitbucketSCMSource instance = load();
        assertThat(instance.getEndpointJenkinsRootUrl()).isEqualTo("http://jenkins.test:8080/");
    }

    @Test
    public void bitbucketJenkinsRootUrl_normalized() {
        loadBEC();
        BitbucketSCMSource instance = load();
        assertThat(instance.getEndpointJenkinsRootUrl()).isEqualTo("https://jenkins.test/");
    }

    @Test
    public void bitbucketJenkinsRootUrl_slashed() {
        loadBEC();
        BitbucketSCMSource instance = load();
        assertThat(instance.getEndpointJenkinsRootUrl()).isEqualTo("https://jenkins.test/jenkins/");
    }

    @Test
    public void bitbucketJenkinsRootUrl_notslashed() {
        loadBEC();
        BitbucketSCMSource instance = load();
        assertThat(instance.getEndpointJenkinsRootUrl()).isEqualTo("https://jenkins.test/jenkins/");
    }

    @Test
    public void verify_built_scm_with_username_password_authenticator() throws Exception {
        BitbucketSCMSource instance = new BitbucketSCMSource("testing", "test-repo");
        instance.setCredentialsId("other-credentials");
        BitbucketMockApiFactory.add(instance.getServerUrl(), BitbucketIntegrationClientFactory.getApiMockClient(instance.getServerUrl()));
        BranchSCMHead head = new BranchSCMHead("master");
        GitSCM scm = (GitSCM) instance.build(head);
        assertThat(scm.getUserRemoteConfigs())
            .hasSize(1)
            .first()
            .satisfies(urc -> assertThat(urc.getCredentialsId()).isEqualTo("other-credentials"));

        GitClient c = mock(GitClient.class);
        for (GitSCMExtension ext : scm.getExtensions()) {
            c = ext.decorate(scm, c);
        }
        verify(c, never()).setCredentials(any(StandardUsernameCredentials.class));
    }
}
