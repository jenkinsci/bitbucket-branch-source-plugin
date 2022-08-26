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


package com.cloudbees.jenkins.plugins.bitbucket.api.credentials;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;

/**
 * Authenticator that uses a username and password (probably the default)
 */
public class BitbucketUsernamePasswordAuthenticator extends BitbucketAuthenticator {

    private static final Logger LOGGER = Logger.getLogger(BitbucketUsernamePasswordAuthenticator.class.getName());

    private final UsernamePasswordCredentials httpCredentials;

    /**
     * Constructor.
     * @param credentials the username/password that will be used
     */
    public BitbucketUsernamePasswordAuthenticator(StandardUsernamePasswordCredentials credentials) {
        super(credentials);
        httpCredentials = new UsernamePasswordCredentials(credentials.getUsername(),
                credentials.getPassword().getPlainText());
    }

    /**
     * Sets up HTTP Basic Auth with the provided username/password
     *
     * @param context The connection context
     * @param host host being connected to
     */
    @Override
    public void configureContext(HttpClientContext context, HttpHost host) {
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(host), httpCredentials);
        AuthCache authCache = new BasicAuthCache();
        LOGGER.log(Level.FINE,"Add host={0} to authCache.", host);
        authCache.put(host, new BasicScheme());
        context.setCredentialsProvider(credentialsProvider);
        context.setAuthCache(authCache);
    }
}
