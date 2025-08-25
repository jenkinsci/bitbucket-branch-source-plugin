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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketException;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRequestException;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpointProvider;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookClient;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookManager;
import com.cloudbees.jenkins.plugins.bitbucket.client.ClosingConnectionInputStream;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.ExtensionList;
import hudson.ProxyConfiguration;
import hudson.util.Secret;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.TimeValue;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.ProtectedExternally;

@Restricted(ProtectedExternally.class)
public abstract class AbstractBitbucketApi implements BitbucketApi, AutoCloseable {
    protected final Logger logger = Logger.getLogger(this.getClass().getName());
    private final BitbucketAuthenticator authenticator;
    private HttpClientContext context;

    protected AbstractBitbucketApi(BitbucketAuthenticator authenticator) {
        if (!isSupportedAuthenticator(authenticator)) {
            throw new BitbucketException("Authentication " + authenticator.getClass().getSimpleName() + " is not supported by this client. Please refer to the user documention at https://github.com/jenkinsci/bitbucket-branch-source-plugin/blob/master/docs/USER_GUIDE.adoc");
        }
        this.authenticator = authenticator;
    }

    protected BitbucketRequestException buildResponseException(ClassicHttpResponse response, String errorMessage) {
        String headers = StringUtils.join(response.getHeaders(), "\n");
        String message = String.format("HTTP request error.%nStatus: %s HTTP %s%nResponse: %s%n%s", response.getReasonPhrase(), response.getCode(), errorMessage, headers);
        return new BitbucketRequestException(response.getCode(), message);
    }

    protected String getResponseContent(ClassicHttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        try {
            long len = entity.getContentLength();
            if (len < 0) {
                len = getLenghtFromHeader(response);
            }

            String content;
            if (len == 0) {
                content = "";
            } else {
                try (InputStream is = entity.getContent()) {
                    content = IOUtils.toString(is, StandardCharsets.UTF_8);
                }
            }
            return content;
        } finally {
            EntityUtils.consumeQuietly(entity);
        }
    }

    private long getLenghtFromHeader(ClassicHttpResponse response) {
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

    protected static PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder() {
        int connectTimeout = Integer.getInteger("http.connect.timeout", 10);
        int socketTimeout = Integer.getInteger("http.socket.timeout", 60);
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(connectTimeout, TimeUnit.SECONDS)
                .setSocketTimeout(socketTimeout, TimeUnit.SECONDS)
                .build();

        return PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(connectionConfig);
    }

    protected HttpClientBuilder setupClientBuilder() {
        int connectionRequestTimeout = Integer.getInteger("http.connect.request.timeout", 60);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(connectionRequestTimeout, TimeUnit.SECONDS)
                .build();

        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create()
                .useSystemProperties()
                .setConnectionManager(getConnectionManager())
                .setConnectionManagerShared(true)
                .setRetryStrategy(new ExponentialBackoffRetryStrategy(2, TimeUnit.SECONDS.toMillis(5), TimeUnit.HOURS.toMillis(1)))
                .setDefaultRequestConfig(requestConfig)
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofSeconds(2))
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
                if (credentialsProvider instanceof CredentialsStore credentialsStore) {
                    credentialsStore.setCredentials(new AuthScope(proxyHttpHost), new UsernamePasswordCredentials(username, password.toCharArray()));
                }
                AuthCache authCache = context.getAuthCache();
                if (authCache == null) {
                    authCache = new BasicAuthCache();
                    context.setAuthCache(authCache);
                }
                authCache.put(proxyHttpHost, new BasicScheme());
            }
        }
    }

    /**
     * Implementation must validate if the configured authenticator is supported by this client implementation.
     */
    protected abstract boolean isSupportedAuthenticator(@CheckForNull BitbucketAuthenticator authenticator);

    @CheckForNull
    protected abstract HttpClientConnectionManager getConnectionManager();

    @NonNull
    protected abstract HttpHost getHost();

    @NonNull
    protected abstract CloseableHttpClient getClient();

    protected ClassicHttpResponse executeMethod(HttpUriRequest request) throws IOException {
        HttpHost targetHost = getHost();
        HttpHost requestHost;
        try {
            requestHost = HttpHost.create(request.getUri());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }

        // some request could have different host than serverURL, for example avatar images or mirror clone URL
        // for all these host authentication will be not applied
        if (authenticator != null && targetHost.equals(requestHost)) {
            authenticator.configureRequest(request);
        }
        return getClient().executeOpen(requestHost, request, context);
    }

    private String doRequest(HttpUriRequest request) throws IOException {
        try (ClassicHttpResponse response =  executeMethod(request)) {
            int statusCode = response.getCode();
            if (statusCode == HttpStatus.SC_NOT_FOUND) {
                String errorMessage = getResponseContent(response);
                throw new FileNotFoundException("Resource " + request.getRequestUri() + " not found: " + errorMessage);
            }
            if (statusCode == HttpStatus.SC_NO_CONTENT) {
                EntityUtils.consumeQuietly(response.getEntity());
                // 204, no content
                return "";
            }
            String content = getResponseContent(response);
            if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_CREATED) {
                throw buildResponseException(response, content);
            }
            return content;
        } catch (FileNotFoundException | BitbucketRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new IOException("Communication error, requested URL: " + request, e);
        }
    }

    /*
     * Caller's responsible to close the InputStream.
     */
    protected InputStream getRequestAsInputStream(String path) throws IOException {
        HttpGet httpget = new HttpGet(path);
        ClassicHttpResponse response =  executeMethod(httpget);
        int statusCode = response.getCode();
        if (statusCode == HttpStatus.SC_NOT_FOUND) {
            String errorMessage = getResponseContent(response);
            throw new FileNotFoundException("Resource " + path + " not found: " + errorMessage);
        }
        if (statusCode != HttpStatus.SC_OK) {
            String content = getResponseContent(response);
            throw buildResponseException(response, content);
        }
        return new ClosingConnectionInputStream(response);
    }

    protected int headRequestStatus(String path) throws IOException {
        HttpHead request = new HttpHead(path);
        request.setAbsoluteRequestUri(true);
        try (ClassicHttpResponse response = executeMethod(request)) {
            return response.getCode();
        } catch (IOException e) {
            throw new IOException("Communication error for url: " + request, e);
        }
    }

    protected String getRequest(String path) throws IOException {
        HttpGet request = new HttpGet(path);
        request.setAbsoluteRequestUri(true);
        return doRequest(request);
    }

    protected String postRequest(String path, List<? extends NameValuePair> params) throws IOException {
        HttpPost request = new HttpPost(path);
        request.setEntity(new UrlEncodedFormEntity(params));
        return doRequest(request);
    }

    protected String postRequest(String path, String content) throws IOException {
        HttpPost request = new HttpPost(path);
        request.setAbsoluteRequestUri(true);
        request.setEntity(new StringEntity(content, ContentType.create("application/json", "UTF-8")));
        return doRequest(request);
    }

    protected String putRequest(String path, String content) throws IOException {
        HttpPut request = new HttpPut(path);
        request.setAbsoluteRequestUri(true);
        request.setEntity(new StringEntity(content, ContentType.create("application/json", "UTF-8")));
        return doRequest(request);
    }

    protected String deleteRequest(String path) throws IOException {
        HttpDelete request = new HttpDelete(path);
        request.setAbsoluteRequestUri(true);
        return doRequest(request);
    }

    @Override
    public void close() throws IOException {
        getClient().close();
    }

    protected BitbucketAuthenticator getAuthenticator() {
        return authenticator;
    }

    @Override
    public List<? extends BitbucketWebHook> getWebHooks() throws IOException {
        logger.warning("getWebHooks is deprecated, do not use this API method anymore, webhook are now handled by the BitbucketWebhookManager.");
        BitbucketWebhookManager manager = buildManager();
        BitbucketWebhookClient webhookClient = adapt(BitbucketWebhookClient.class);
        return new ArrayList<>(manager.read(webhookClient));
    }

    @Override
    public void registerCommitWebHook(BitbucketWebHook hook) throws IOException {
        logger.warning("registerCommitWebHook is deprecated, do not use this API method anymore, webhook are now handled by the BitbucketWebhookManager.");
        this.updateCommitWebHook(hook);
    }

    @Override
    public void updateCommitWebHook(BitbucketWebHook hook) throws IOException {
        logger.warning("updateCommitWebHook is deprecated, do not use this API method anymore, webhook are now handled by the BitbucketWebhookManager.");
        BitbucketWebhookManager manager = buildManager();
        BitbucketWebhookClient webhookClient = adapt(BitbucketWebhookClient.class);
        manager.register(webhookClient);
    }

    @Override
    public void removeCommitWebHook(BitbucketWebHook hook) throws IOException {
        logger.warning("removeCommitWebHook is deprecated, do not use this API method anymore, webhook are now handled by the BitbucketWebhookManager.");
        BitbucketWebhookManager integmanagerration = buildManager();
        BitbucketWebhookClient webhookClient = adapt(BitbucketWebhookClient.class);
        integmanagerration.remove(hook.getUuid(), webhookClient);
    }

    @Deprecated
    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    @NonNull
    private BitbucketWebhookManager buildManager() {
        String serverURL = getHost().toString(); // not 100% true in case of bitbucket data center...but this method must not be used so does not matter
        BitbucketEndpoint endpoint = BitbucketEndpointProvider.lookupEndpoint(serverURL)
                .orElseThrow();
        BitbucketWebhookConfiguration configuration = endpoint.getWebhook();
        BitbucketWebhookManager manager = ExtensionList.lookupFirst(configuration.getManager());
        manager.apply(configuration);
        manager.setRepositoryOwner(getOwner());
        manager.setRepositoryName(getRepositoryName());
        return manager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T adapt(Class<T> clazz) {
        if (clazz == BitbucketWebhookClient.class) {
            return (T) new BitbucketWebhookClient() {

                @Override
                public String post(@NonNull String path, @CheckForNull String payload) throws IOException {
                    return postRequest(path, payload);
                }

                @Override
                public String put(@NonNull String path, @CheckForNull String payload) throws IOException {
                    return putRequest(path, payload);
                }

                @Override
                public String delete(@NonNull String path) throws IOException {
                    return deleteRequest(path);
                }

                @Override
                public String get(@NonNull String path) throws IOException {
                    return getRequest(path);
                }

                @Override
                public void close() throws IOException {
                    AbstractBitbucketApi.this.close();
                }
            };
        } else {
            return null;
        }
    }
}
