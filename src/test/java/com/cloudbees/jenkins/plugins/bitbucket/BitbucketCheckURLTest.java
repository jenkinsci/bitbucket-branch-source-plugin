package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketServerEndpoint;
import java.util.Arrays;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.jvnet.hudson.test.JenkinsRule;

import static com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketIntegrationClientFactory.getApiMockClient;

@RunWith(Parameterized.class)
public class BitbucketCheckURLTest {

    private static final String BITBUCKET_SERVER_URL = "https://bitbucket.server";
    private static final Class<IllegalStateException> ISE = IllegalStateException.class;
    private static final String localHost = "Jenkins URL cannot start with http://localhost";
    private static final String CLOUD = "cloud";
    private static final String SERVER = "server";
    private static final String BOTH = String.format("%s and %s", CLOUD, SERVER);
    private static final String FQDN = "Please use a fully qualified name or an IP address for Jenkins URL, this is required by Bitbucket cloud";
    private static final String DETERMINE = "Could not determine Jenkins URL.";
    private final String jenkinsUrl;
    private final Class<? extends Exception> expectedException;
    private final String expectedExceptionMsg;
    private final String serverOrCloud;
    private static BitbucketApi bitbucketServer;
    private static BitbucketApi bitbucketCloud;

    public BitbucketCheckURLTest(String jenkinsUrl,
        String expectedExceptionMsg,
        Class<? extends Exception> expectedException, String serverOrCloud) {
        this.jenkinsUrl = jenkinsUrl;
        this.expectedException = expectedException;
        this.expectedExceptionMsg = expectedExceptionMsg;
        this.serverOrCloud = serverOrCloud;
    }

    @Parameters(name = "check {0} URL against {3}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"localhost", localHost, ISE, BOTH},
            {"unconfigured-jenkins-location", DETERMINE, ISE, BOTH},
            {"intranet", FQDN, ISE, CLOUD},
            {"intranet:8080", FQDN, ISE, CLOUD},
            {"localhost.local", null, null, BOTH},
            {"intranet.local:8080", null, null, BOTH},
            {"www.mydomain.com:8000", null, null, BOTH},
            {"www.mydomain.com", null, null, BOTH}
        });
    }


    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        BitbucketEndpointConfiguration instance = BitbucketEndpointConfiguration.get();
        instance.setEndpoints(Arrays.asList(
            new BitbucketServerEndpoint(
                "Bitbucket Server",
                BITBUCKET_SERVER_URL,
                true,
                "dummy"),
            new BitbucketCloudEndpoint(
                true,
                "second")
        ));
        bitbucketServer = getApiMockClient(BITBUCKET_SERVER_URL);
        bitbucketCloud = getApiMockClient(BitbucketCloudEndpoint.SERVER_URL);
    }

    private void setupException() {
        if (expectedException != null) {
            thrown.expect(expectedException);
            thrown.expectMessage(expectedExceptionMsg);
        }
    }

    private void doubleTrouble(BitbucketApi bitbucketApi) {
        Arrays.asList("http://", "https://").forEach(
            url -> BitbucketBuildStatusNotifications
                .checkURL(url + jenkinsUrl + "/build/sample", bitbucketApi));
    }

    @Test
    public void checkURLForBitbucketServer() {
        if (serverOrCloud.contains(SERVER)) {
            setupException();
        }
        doubleTrouble(bitbucketServer);
    }

    @Test
    public void checkURLForBitbucketCloud() {
        if (serverOrCloud.contains(CLOUD)) {
            setupException();
        }
        doubleTrouble(bitbucketCloud);
    }

}
