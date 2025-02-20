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
package com.cloudbees.jenkins.plugins.bitbucket.impl.client;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRequestException;
import com.cloudbees.jenkins.plugins.bitbucket.client.ClosingConnectionInputStream;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.ProxyConfiguration;
import hudson.util.Secret;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.StandardHttpRequestRetryHandler;
import org.apache.http.util.EntityUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.ProtectedExternally;

@Restricted(ProtectedExternally.class)
public abstract class AbstractBitbucketApi implements AutoCloseable {
    protected final Logger logger = Logger.getLogger(this.getClass().getName());
    private final BitbucketAuthenticator authenticator;
    private HttpClientContext context;

    protected AbstractBitbucketApi(BitbucketAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

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

    protected HttpClientBuilder setupClientBuilder(@Nullable String host) {
        int connectTimeout = Integer.getInteger("http.connect.timeout", 10);
        int connectionRequestTimeout = Integer.getInteger("http.connect.request.timeout", 60);
        int socketTimeout = Integer.getInteger("http.socket.timeout", 60);

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(connectTimeout * 1000)
                .setConnectionRequestTimeout(connectionRequestTimeout * 1000)
                .setSocketTimeout(socketTimeout * 1000)
                .build();

        HttpClientConnectionManager connectionManager = getConnectionManager();
        ServiceUnavailableRetryStrategy serviceUnavailableStrategy = new ExponentialBackOffServiceUnavailableRetryStrategy(2, TimeUnit.SECONDS.toMillis(5), TimeUnit.HOURS.toMillis(1));
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create()
                .useSystemProperties()
                .setConnectionManager(connectionManager)
                .setConnectionManagerShared(connectionManager != null)
                .setServiceUnavailableRetryStrategy(serviceUnavailableStrategy)
                .setRetryHandler(new StandardHttpRequestRetryHandler())
                .setDefaultRequestConfig(config)
                .disableCookieManagement();

        if (authenticator != null) {
            authenticator.configureBuilder(httpClientBuilder);

            context = HttpClientContext.create();
            authenticator.configureContext(context, getHost());
        }
        setClientProxyParams(httpClientBuilder);
        return httpClientBuilder;
    }

    protected void setClientProxyParams(HttpClientBuilder builder) {
        Jenkins jenkins = Jenkins.getInstanceOrNull(); // because unit test
        ProxyConfiguration proxyConfig = jenkins != null ? jenkins.proxy : null;

        final Proxy proxy;
        if (proxyConfig != null) {
            proxy = proxyConfig.createProxy(getHost().getHostName());
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

    @CheckForNull
    protected abstract HttpClientConnectionManager getConnectionManager();

    @NonNull
    protected abstract HttpHost getHost();

    @NonNull
    protected abstract CloseableHttpClient getClient();

    protected CloseableHttpResponse executeMethod(HttpRequestBase request, boolean requireAuthentication) throws IOException {
        if (requireAuthentication && authenticator != null) {
            authenticator.configureRequest(request);
        }
        // the Apache client determinate the host from request.getURI()
        // in some cases like requests to mirror or avatar, the host could not be the same of configured in Jenkins
        return getClient().execute(request, context);
    }

    protected CloseableHttpResponse executeMethod(HttpRequestBase httpMethod) throws IOException {
        return executeMethod(httpMethod, true);
    }

    protected String doRequest(HttpRequestBase request, boolean requireAuthentication) throws IOException {
        try (CloseableHttpResponse response =  executeMethod(request, requireAuthentication)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_NOT_FOUND) {
                throw new FileNotFoundException("URL: " + request.getURI());
            }
            if (statusCode == HttpStatus.SC_NO_CONTENT) {
                EntityUtils.consume(response.getEntity());
                // 204, no content
                return "";
            }
            String content = getResponseContent(response);
            EntityUtils.consume(response.getEntity());
            if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_CREATED) {
                throw buildResponseException(response, content);
            }
            return content;
        } catch (FileNotFoundException | BitbucketRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new IOException("Communication error for url: " + request, e);
        } finally {
            release(request);
        }
    }

    protected String doRequest(HttpRequestBase request) throws IOException {
        return doRequest(request, true);
    }

    private void release(HttpRequestBase method) {
        method.releaseConnection();
        HttpClientConnectionManager connectionManager = getConnectionManager();
        if (connectionManager != null) {
            connectionManager.closeExpiredConnections();
        }
    }

    /*
     * Caller's responsible to close the InputStream.
     */
    protected InputStream getRequestAsInputStream(String path) throws IOException {
        HttpGet httpget = new HttpGet(path);
        CloseableHttpResponse response =  executeMethod(httpget);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode == HttpStatus.SC_NOT_FOUND) {
            EntityUtils.consume(response.getEntity());
            throw new FileNotFoundException("URL: " + path);
        }
        if (statusCode != HttpStatus.SC_OK) {
            String content = getResponseContent(response);
            throw buildResponseException(response, content);
        }
        return new ClosingConnectionInputStream(response, httpget, getConnectionManager());
    }

    protected int headRequestStatus(HttpRequestBase request) throws IOException {
        try (CloseableHttpResponse response = executeMethod(request)) {
            EntityUtils.consume(response.getEntity());
            return response.getStatusLine().getStatusCode();
        } catch (IOException e) {
            throw new IOException("Communication error for url: " + request, e);
        } finally {
            release(request);
        }
    }

    protected String getRequest(String path) throws IOException {
        HttpGet httpget = new HttpGet(path);
        return doRequest(httpget);
    }

    protected String postRequest(String path, List<? extends NameValuePair> params) throws IOException {
        HttpPost request = new HttpPost(path);
        request.setEntity(new UrlEncodedFormEntity(params));
        return doRequest(request);
    }

    protected String postRequest(String path, String content) throws IOException {
        HttpPost request = new HttpPost(path);
        request.setEntity(new StringEntity(content, ContentType.create("application/json", "UTF-8")));
        return doRequest(request);
    }

    protected String putRequest(String path, String content) throws IOException {
        HttpPut request = new HttpPut(path);
        request.setEntity(new StringEntity(content, ContentType.create("application/json", "UTF-8")));
        return doRequest(request);
    }

    protected String deleteRequest(String path) throws IOException {
        HttpDelete request = new HttpDelete(path);
        return doRequest(request);
    }

    @Override
    public void close() throws IOException {
        getClient().close();
    }

    protected BitbucketAuthenticator getAuthenticator() {
        return authenticator;
    }
}
