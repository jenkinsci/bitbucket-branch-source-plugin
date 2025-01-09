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
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.util.Secret;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHost;

/**
 * Authenticator that uses a username and password (probably the default)
 */
public class BitbucketUsernamePasswordAuthenticator implements BitbucketAuthenticator {

    private static final Logger LOGGER = Logger.getLogger(BitbucketUsernamePasswordAuthenticator.class.getName());

    private final UsernamePasswordCredentials httpCredentials;
    private final String credentialsId;

    /**
     * Constructor.
     * @param credentials the username/password that will be used
     */
    public BitbucketUsernamePasswordAuthenticator(StandardUsernamePasswordCredentials credentials) {
        credentialsId = credentials.getId();
        String password = Secret.toString(credentials.getPassword());
        httpCredentials = new UsernamePasswordCredentials(credentials.getUsername(), password.toCharArray());
    }

    /**
     * Sets up HTTP Basic Auth with the provided username/password
     *
     * @param context The connection context
     * @param host host being connected to
     */
    @Override
    public void configureContext(HttpClientContext context, HttpHost host) {
        CredentialsStore credentialsStore = new BasicCredentialsProvider();
        credentialsStore.setCredentials(new AuthScope(host), httpCredentials);
        AuthCache authCache = new BasicAuthCache();
        LOGGER.log(Level.FINE,"Add host={0} to authCache.", host);
        authCache.put(host, new BasicScheme());
        context.setCredentialsProvider(credentialsStore);
        context.setAuthCache(authCache);
    }

    @Override
    public String getId() {
        return credentialsId;
    }

}
