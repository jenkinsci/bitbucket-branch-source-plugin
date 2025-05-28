/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

package com.cloudbees.jenkins.plugins.bitbucket.impl.credentials;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketException;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import hudson.util.Secret;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.HttpsSupport;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLContexts;

/**
 * Authenticates against Bitbucket using a TLS client certificate
 */
public class BitbucketClientCertificateAuthenticator implements BitbucketAuthenticator {
    private static final Logger logger = Logger.getLogger(BitbucketClientCertificateAuthenticator.class.getName());
    private static final String SOCKET_FACTORY_REGISTRY = "http.socket-factory-registry";

    private final String credentialsId;
    private final KeyStore keyStore;
    private final Secret password;

    public BitbucketClientCertificateAuthenticator(StandardCertificateCredentials credentials) {
        this.credentialsId = credentials.getId();
        keyStore = credentials.getKeyStore();
        password = credentials.getPassword();
    }

    /**
     * Sets the SSLContext for the builder to one that will connect with the selected certificate.
     * @param context The client builder context
     * @param host the target host name
     */
    @Override
    public void configureContext(HttpClientContext context, HttpHost host) {
        try {
            Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register(URIScheme.HTTP.id, PlainConnectionSocketFactory.getSocketFactory())
                .register(URIScheme.HTTPS.id, new SSLConnectionSocketFactory(buildSSLContext(), HttpsSupport.getDefaultHostnameVerifier()))
                .build();
            context.setAttribute(SOCKET_FACTORY_REGISTRY, registry); // override SSL registry for this context
        } catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException | KeyManagementException e) {
            throw new BitbucketException("Failed to set up SSL context from provided client certificate", e);
        }
    }

    private SSLContext buildSSLContext() throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException, KeyManagementException {
        SSLContextBuilder contextBuilder = SSLContexts.custom();
        contextBuilder.loadKeyMaterial(keyStore, Secret.toString(password).toCharArray());
        return contextBuilder.build();
    }

    @Override
    public String getId() {
        return credentialsId;
    }
}
