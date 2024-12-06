package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.credentials.BitbucketUsernamePasswordAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
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
    public void passwordCredentialsTest() throws Exception {
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
    public void certCredentialsTest() throws Exception {
        // random password in test code to keep code-ql happy 🤮
        String password = UUID.randomUUID().toString();
        List<Credentials> list = Collections.<Credentials>singletonList(new CertificateCredentialsImpl(
                CredentialsScope.SYSTEM, "dummy", "dummy", password, new DummyKeyStoreSource(password)));

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

    private static class DummyKeyStoreSource extends CertificateCredentialsImpl.UploadedKeyStoreSource {

        DummyKeyStoreSource(String password) throws Exception {
            super(null, dummyPKCS12Store(password));
        }

        private static SecretBytes dummyPKCS12Store(String password) throws Exception {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, password.toCharArray());
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ks.store(bos, password.toCharArray());
            return SecretBytes.fromBytes(bos.toByteArray());
        }

    }

}
