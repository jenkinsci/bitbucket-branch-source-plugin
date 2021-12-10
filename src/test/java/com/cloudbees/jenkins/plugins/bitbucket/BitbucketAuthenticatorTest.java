package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.credentials.BitbucketUsernamePasswordAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.List;
import jenkins.authentication.tokens.api.AuthenticationTokenContext;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class BitbucketAuthenticatorTest {

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
    @Rule
    public TestName currentTestName = new TestName();

    @Test
    public void authenticationContextTest() {
        AuthenticationTokenContext nullCloudContext = BitbucketAuthenticator.authenticationContext(null);
        AuthenticationTokenContext cloudContext = BitbucketAuthenticator.authenticationContext(BitbucketCloudEndpoint.SERVER_URL);
        AuthenticationTokenContext httpContext = BitbucketAuthenticator.authenticationContext("http://git.example.com");
        AuthenticationTokenContext httpsContext = BitbucketAuthenticator.authenticationContext("https://git.example.com");

        assertThat(nullCloudContext.mustHave(BitbucketAuthenticator.SCHEME, "https"), is(true));
        assertThat(nullCloudContext.mustHave(BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE,
                BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE_CLOUD), is(true));

        assertThat(cloudContext.mustHave(BitbucketAuthenticator.SCHEME, "https"), is(true));
        assertThat(cloudContext.mustHave(BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE,
                BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE_CLOUD), is(true));

        assertThat(httpContext.mustHave(BitbucketAuthenticator.SCHEME, "http"), is(true));
        assertThat(httpContext.mustHave(BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE,
                BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE_SERVER), is(true));

        assertThat(httpsContext.mustHave(BitbucketAuthenticator.SCHEME, "https"), is(true));
        assertThat(httpsContext.mustHave(BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE,
                BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE_SERVER), is(true));
    }

    @Test
    public void passwordCredentialsTest() {
        List<Credentials> list = Collections.<Credentials>singletonList(new UsernamePasswordCredentialsImpl(
                        CredentialsScope.SYSTEM, "dummy", "dummy", "user", "pass"));
        AuthenticationTokenContext ctx = BitbucketAuthenticator.authenticationContext((null));
        Credentials c = CredentialsMatchers.firstOrNull(list, AuthenticationTokens.matcher(ctx));
        assertThat(c, notNullValue());
        Object a = AuthenticationTokens.convert(ctx, c);
        assertThat(a, notNullValue());
        assertThat(a, isA(BitbucketUsernamePasswordAuthenticator.class));
    }

    @Test
    public void certCredentialsTest() {
        List<Credentials> list = Collections.<Credentials>singletonList(new CertificateCredentialsImpl(
                CredentialsScope.SYSTEM, "dummy", "dummy", "password", new DummyKeyStoreSource()));

        AuthenticationTokenContext ctx = BitbucketAuthenticator.authenticationContext(null);
        Credentials c = CredentialsMatchers.firstOrNull(list, AuthenticationTokens.matcher(ctx));
        assertThat(c, nullValue());

        ctx = BitbucketAuthenticator.authenticationContext("http://git.example.com");
        c = CredentialsMatchers.firstOrNull(list, AuthenticationTokens.matcher(ctx));
        assertThat(c, nullValue());

        ctx = BitbucketAuthenticator.authenticationContext("https://git.example.com");
        c = CredentialsMatchers.firstOrNull(list, AuthenticationTokens.matcher(ctx));
        assertThat(c, notNullValue());
        assertThat(AuthenticationTokens.convert(ctx, c), notNullValue());
    }

    private static class DummyKeyStoreSource extends CertificateCredentialsImpl.KeyStoreSource {
        @NonNull
        @Override
        public byte[] getKeyStoreBytes() { return new byte[0]; }

        @Override
        public long getKeyStoreLastModified() { return 0; }

        @Override
        public boolean isSnapshotSource() { return true; }
    }
}
