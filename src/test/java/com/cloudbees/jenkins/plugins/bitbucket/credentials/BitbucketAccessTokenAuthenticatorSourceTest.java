package com.cloudbees.jenkins.plugins.bitbucket.credentials;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import jenkins.authentication.tokens.api.AuthenticationTokenContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;

class BitbucketAccessTokenAuthenticatorSourceTest {

    private BitbucketAccessTokenAuthenticatorSource sut;

    @BeforeEach
    void setup() {
        sut = new BitbucketAccessTokenAuthenticatorSource();
    }

    @Test
    @WithJenkins
    void test_fit_expected_authetication_context(JenkinsRule j) {
        AuthenticationTokenContext<BitbucketAuthenticator> cloudContext = BitbucketAuthenticator.authenticationContext(BitbucketCloudEndpoint.SERVER_URL);
        assertThat(sut.isFit(cloudContext)).isTrue();

        AuthenticationTokenContext<BitbucketAuthenticator> serverContext = BitbucketAuthenticator.authenticationContext("https://bitbucket-server.org");
        assertThat(sut.isFit(serverContext)).isTrue();

        AuthenticationTokenContext<BitbucketAuthenticator> unsecureServerContext = BitbucketAuthenticator.authenticationContext("http://bitbucket-server.org");
        assertThat(sut.isFit(unsecureServerContext)).isFalse();
    }
}
