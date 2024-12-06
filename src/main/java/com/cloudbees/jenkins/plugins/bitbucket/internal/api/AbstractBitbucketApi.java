/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket.internal.api;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRequestException;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.ProxyConfiguration;
import hudson.util.Secret;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.ProtectedExternally;

@Restricted(ProtectedExternally.class)
public abstract class AbstractBitbucketApi {
    protected static final int API_RATE_LIMIT_STATUS_CODE = 429;

    protected final Logger logger = Logger.getLogger(this.getClass().getName());
    protected HttpClientContext context;

    protected String truncateMiddle(@CheckForNull String value, int maxLength) {
        int length = StringUtils.length(value);
        if (length > maxLength) {
            int halfLength = (maxLength - 3) / 2;
            return StringUtils.left(value, halfLength) + "..." + StringUtils.substring(value, -halfLength);
        } else {
            return value;
        }
    }

    protected BitbucketRequestException buildResponseException(CloseableHttpResponse response, String errorMessage) {
        String headers = StringUtils.join(response.getAllHeaders(), "\n");
        String message = String.format("HTTP request error.%nStatus: %s%nResponse: %s%n%s", response.getStatusLine(), errorMessage, headers);
        return new BitbucketRequestException(response.getStatusLine().getStatusCode(), message);
    }

    protected String getResponseContent(CloseableHttpResponse response) throws IOException {
        String content;
        long len = response.getEntity().getContentLength();
        if (len < 0) {
            len = getLenghtFromHeader(response);
        }
        if (len == 0) {
            content = "";
        } else {
            try (InputStream is = response.getEntity().getContent()) {
                content = IOUtils.toString(is, StandardCharsets.UTF_8);
            }
        }
        return content;
    }

    private long getLenghtFromHeader(CloseableHttpResponse response) {
        long len = -1L;
        Header[] headers = response.getHeaders("Content-Length");
        if (headers != null && headers.length > 0) {
            int i = headers.length - 1;
            len = -1L;
            while (i >= 0) {
                Header header = headers[i];
                try {
                    len = Long.parseLong(header.getValue());
                    break;
                } catch (NumberFormatException var5) {
                    --i;
                }
            }
        }
        return len;
    }

    protected void setClientProxyParams(String host, HttpClientBuilder builder) {
        Jenkins jenkins = Jenkins.getInstanceOrNull(); // because unit test
        ProxyConfiguration proxyConfig = jenkins != null ? jenkins.proxy : null;

        final Proxy proxy;
        if (proxyConfig != null) {
            proxy = proxyConfig.createProxy(host);
        } else {
            proxy = Proxy.NO_PROXY;
        }

        if (proxy != Proxy.NO_PROXY && proxy.type() != Proxy.Type.DIRECT) {
            final InetSocketAddress proxyAddress = (InetSocketAddress) proxy.address();
            logger.fine("Jenkins proxy: " + proxy.address());
            HttpHost proxyHttpHost = new HttpHost(proxyAddress.getHostName(), proxyAddress.getPort());
            builder.setProxy(proxyHttpHost);
            String username = proxyConfig.getUserName();
            String password = Secret.toString(proxyConfig.getSecretPassword());
            if (StringUtils.isNotBlank(username)) {
                logger.fine("Using proxy authentication (user=" + username + ")");
                if (context == null) {
                    // may have been already set in com.cloudbees.jenkins.plugins.bitbucket.api.credentials.BitbucketUsernamePasswordAuthenticator.configureContext(HttpClientContext, HttpHost)
                    context = HttpClientContext.create();
                }
                CredentialsProvider credentialsProvider = context.getCredentialsProvider();
                if (credentialsProvider == null) {
                    credentialsProvider = new BasicCredentialsProvider();
                    // may have been already set in com.cloudbees.jenkins.plugins.bitbucket.api.credentials.BitbucketUsernamePasswordAuthenticator.configureContext(HttpClientContext, HttpHost)
                    context.setCredentialsProvider(credentialsProvider);
                }
                credentialsProvider.setCredentials(new AuthScope(proxyHttpHost), new UsernamePasswordCredentials(username, password));
                AuthCache authCache = context.getAuthCache();
                if (authCache == null) {
                    authCache = new BasicAuthCache();
                    context.setAuthCache(authCache);
                }
                authCache.put(proxyHttpHost, new BasicScheme());
            }
        }
    }

}
