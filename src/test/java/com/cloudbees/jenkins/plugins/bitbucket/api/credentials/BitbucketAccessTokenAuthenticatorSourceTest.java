package com.cloudbees.jenkins.plugins.bitbucket.api.credentials;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import jenkins.authentication.tokens.api.AuthenticationTokenContext;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class BitbucketAccessTokenAuthenticatorSourceTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Test
    public void isFitTest() {
        BitbucketAccessTokenAuthenticatorSource source = new BitbucketAccessTokenAuthenticatorSource();
        AuthenticationTokenContext<BitbucketAuthenticator> cloudContext = BitbucketAuthenticator.authenticationContext("https://bitbucket.org");
        AuthenticationTokenContext<BitbucketAuthenticator> serverContext = BitbucketAuthenticator.authenticationContext("https://bitbucket-server.org");
        assertThat(source.isFit(cloudContext), is(true));
        assertThat(source.isFit(serverContext), is(true));
    }
}
