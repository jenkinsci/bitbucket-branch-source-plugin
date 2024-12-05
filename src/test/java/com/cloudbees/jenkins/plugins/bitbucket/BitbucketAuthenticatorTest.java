package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.credentials.BitbucketAccessTokenAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.credentials.BitbucketClientCertificateAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.credentials.BitbucketOAuthAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.credentials.BitbucketUsernamePasswordAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.util.Secret;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import jenkins.authentication.tokens.api.AuthenticationTokenContext;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.apache.commons.lang3.RandomStringUtils.insecure;
import static org.assertj.core.api.Assertions.assertThat;

@WithJenkins
class BitbucketAuthenticatorTest {
    private StandardUsernameCredentials credentials;

    @BeforeEach
    void setup() throws Exception {
        credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, "credentialsId", "description", "user", "password");
    }

    @Test
    void test_authenticationContext_builder() {
        AuthenticationTokenContext<?> nullContext = BitbucketAuthenticator.authenticationContext(null);
        assertThat(nullContext.mustHave(BitbucketAuthenticator.SCHEME, "https")).isTrue();
        assertThat(nullContext.mustHave(BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE,
                BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE_CLOUD)).isTrue();

        AuthenticationTokenContext<?> cloudContext = BitbucketAuthenticator.authenticationContext(BitbucketCloudEndpoint.SERVER_URL);
        assertThat(cloudContext.mustHave(BitbucketAuthenticator.SCHEME, "https")).isTrue();
        assertThat(cloudContext.mustHave(BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE,
                BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE_CLOUD)).isTrue();

        AuthenticationTokenContext<?> httpContext = BitbucketAuthenticator.authenticationContext("http://git.example.com");
        assertThat(httpContext.mustHave(BitbucketAuthenticator.SCHEME, "http")).isTrue();
        assertThat(httpContext.mustHave(BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE,
                BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE_SERVER)).isTrue();

        AuthenticationTokenContext<?> httpsContext = BitbucketAuthenticator.authenticationContext("https://git.example.com");
        assertThat(httpsContext.mustHave(BitbucketAuthenticator.SCHEME, "https")).isTrue();
        assertThat(httpsContext.mustHave(BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE,
                BitbucketAuthenticator.BITBUCKET_INSTANCE_TYPE_SERVER)).isTrue();
    }

    @Test
    void given_UsernamePasswordCredentials_returns_BitbucketOAuthAuthenticator(JenkinsRule r) throws Exception {
        String clientSecret = insecure().nextAlphabetic(32);
        String clientId = insecure().nextAlphabetic(18);
        credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM,
                credentials.getId(),
                credentials.getDescription(),
                clientId,
                clientSecret);
        List<Credentials> list = Collections.<Credentials>singletonList(credentials);
        AuthenticationTokenContext<?> ctx = BitbucketAuthenticator.authenticationContext(null);
        Credentials c = CredentialsMatchers.firstOrNull(list, AuthenticationTokens.matcher(ctx));
        assertThat(c).isNotNull();
        BitbucketAuthenticator a = (BitbucketAuthenticator) AuthenticationTokens.convert(ctx, c);
        assertThat(a).isNotNull()
            .isInstanceOf(BitbucketOAuthAuthenticator.class);
    }

    @Test
    void given_UsernamePasswordCredentials_returns_BitbucketUsernamePasswordAuthenticator(JenkinsRule r) {
        // use cases: real username/password or an app password
        List<Credentials> list = Collections.<Credentials>singletonList(credentials);
        AuthenticationTokenContext<?> ctx = BitbucketAuthenticator.authenticationContext(null);
        Credentials c = CredentialsMatchers.firstOrNull(list, AuthenticationTokens.matcher(ctx));
        assertThat(c).isNotNull();
        BitbucketAuthenticator a = (BitbucketAuthenticator) AuthenticationTokens.convert(ctx, c);
        assertThat(a).isNotNull()
            .isInstanceOf(BitbucketUsernamePasswordAuthenticator.class);
    }

    @Test
    void given_StringCredentials_returns_BitbucketAccessTokenAuthenticator_cloud(JenkinsRule r) {
        // repository Access Token
        StringCredentialsImpl tokenCredentials = new StringCredentialsImpl(CredentialsScope.SYSTEM,
                credentials.getId(),
                credentials.getDescription(),
                Secret.fromString("XXXTT3xFfGN0YR0qSTr0SoRZeTZ2InfnX8U1BGTvAFY6_OzZRHn-0RjTmNseIV84KfdBq3nHlL4mNF1UMBTZwbpWUnNO1MXIsI4kgimuOSlHFwfWyD78Fz7OBYw-K_z1UwvhSgsvVt0K6xalAKgrTYbgOQubublL7A1Fav6BzaIrprZ11XJDRWY=455AEB4D"));
        List<Credentials> list = Collections.<Credentials>singletonList(tokenCredentials);
        AuthenticationTokenContext<?> ctx = BitbucketAuthenticator.authenticationContext(null);
        Credentials c = CredentialsMatchers.firstOrNull(list, AuthenticationTokens.matcher(ctx));
        assertThat(c).isNotNull();
        BitbucketAuthenticator a = (BitbucketAuthenticator) AuthenticationTokens.convert(ctx, c);
        assertThat(a).isNotNull()
            .isInstanceOf(BitbucketAccessTokenAuthenticator.class);
    }

    @Test
    void given_CertificateCredentials_returns_BitbucketUsernamePasswordAuthenticator(JenkinsRule r) throws Exception {
        String password = UUID.randomUUID().toString();
        StandardCertificateCredentials certCredentials = new CertificateCredentialsImpl(CredentialsScope.SYSTEM,
                credentials.getId(),
                credentials.getDescription(),
                password,
                new DummyKeyStoreSource(password));
        List<Credentials> list = Collections.<Credentials>singletonList(certCredentials);

        AuthenticationTokenContext<?> ctx = BitbucketAuthenticator.authenticationContext(null);
        Credentials c = CredentialsMatchers.firstOrNull(list, AuthenticationTokens.matcher(ctx));
        assertThat(c).isNull();

        ctx = BitbucketAuthenticator.authenticationContext("http://git.example.com");
        c = CredentialsMatchers.firstOrNull(list, AuthenticationTokens.matcher(ctx));
        assertThat(c).isNull();

        ctx = BitbucketAuthenticator.authenticationContext("https://git.example.com");
        c = CredentialsMatchers.firstOrNull(list, AuthenticationTokens.matcher(ctx));
        assertThat(c).isNotNull();
        BitbucketAuthenticator a = (BitbucketAuthenticator) AuthenticationTokens.convert(ctx, c);
        assertThat(a).isNotNull()
            .isInstanceOf(BitbucketClientCertificateAuthenticator.class);
    }

    @SuppressWarnings("serial")
    private static class DummyKeyStoreSource extends CertificateCredentialsImpl.UploadedKeyStoreSource {

        DummyKeyStoreSource(String password) throws Exception {
            super(null, dummyPKCS12Store(password));
        }

        private static SecretBytes dummyPKCS12Store(String password) throws Exception {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, password.toCharArray());
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ks.store(bos, password.toCharArray());
            return SecretBytes.fromRawBytes(bos.toByteArray());
        }

    }

}
