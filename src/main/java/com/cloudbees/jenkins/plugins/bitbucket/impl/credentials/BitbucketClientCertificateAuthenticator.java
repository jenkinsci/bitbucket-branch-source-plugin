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
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import hudson.util.Secret;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.net.ssl.X509ExtendedKeyManager;
import nl.altindag.ssl.SSLFactory;
import nl.altindag.ssl.keymanager.AggregatedX509ExtendedKeyManager;
import nl.altindag.ssl.keymanager.DummyX509ExtendedKeyManager;
import nl.altindag.ssl.keymanager.HotSwappableX509ExtendedKeyManager;
import nl.altindag.ssl.keymanager.LoggingX509ExtendedKeyManager;
import nl.altindag.ssl.keymanager.X509KeyManagerWrapper;
import nl.altindag.ssl.util.KeyManagerUtils;
import org.apache.hc.core5.http.HttpHost;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Authenticates against Bitbucket using a TLS client certificate
 */
public class BitbucketClientCertificateAuthenticator implements BitbucketAuthenticator {
    private static final Logger logger = Logger.getLogger(BitbucketClientCertificateAuthenticator.class.getName());

    private final String credentialsId;
    private final KeyStore keyStore;
    private final Secret password;

    public BitbucketClientCertificateAuthenticator(StandardCertificateCredentials credentials) {
        this.credentialsId = credentials.getId();
        keyStore = credentials.getKeyStore();
        password = credentials.getPassword();
    }

    /**
     * Sets the SSLContext for the builder to one that will connect with the
     * selected certificate.
     *
     * @param sslFactory The client SSL context configured in the connection
     *        pool
     * @param host the target host name
     */
    @Restricted(NoExternalUse.class)
    public synchronized /*required to avoid combine the same identity material twice */ void configureContext(SSLFactory sslFactory, HttpHost host) {
        sslFactory.getKeyManager().ifPresent(baseKeyManager -> {
            AggregatedX509ExtendedKeyManager aggregate = unwrap(baseKeyManager);
            String routeAlias = host.getHostName();

            // check if given route has been already added to the SSL context
            Map<String, List<URI>> routes = aggregate.getIdentityRoute();
            if (!routes.containsKey(routeAlias)) {
                try {
                    URI hostURI = new URI(host.toURI());
                    routes.put(routeAlias, new ArrayList<>(List.of(hostURI)));
                } catch (URISyntaxException e) {
                    logger.severe("Invalid host " + host);
                }
                // create an aggregate keyManager with new identity material plus existing contributed
                // from other configured client certificate client
                X509ExtendedKeyManager combined = KeyManagerUtils.keyManagerBuilder()
                        .withKeyManagers(aggregate.getInnerKeyManagers())
                        .withKeyManager(KeyManagerUtils.createKeyManager(keyStore, Secret.toString(password).toCharArray()))
                        .withIdentityRoute(routes)
                        .build();
                // swap identity materials and reuse existing http client
                KeyManagerUtils.swapKeyManager(baseKeyManager, combined);
            }
        });
    }


    private AggregatedX509ExtendedKeyManager unwrap(X509ExtendedKeyManager keyManager) {
        if (keyManager instanceof AggregatedX509ExtendedKeyManager aggregate) {
            return aggregate;
        } else if (keyManager instanceof HotSwappableX509ExtendedKeyManager swappable) {
            return unwrap(swappable.getInnerKeyManager());
        } else if (keyManager instanceof LoggingX509ExtendedKeyManager logger) {
            return unwrap(logger.getInnerKeyManager());
        } else if (keyManager instanceof X509KeyManagerWrapper wrapper) {
            return unwrap((X509ExtendedKeyManager) wrapper.getInnerKeyManager());
        } else if (keyManager instanceof DummyX509ExtendedKeyManager) {
            return new AggregatedX509ExtendedKeyManager(new ArrayList<>());
        } else {
            return new AggregatedX509ExtendedKeyManager(List.of(keyManager));
        }
    }

    @Override
    public String getId() {
        return credentialsId;
    }
}
