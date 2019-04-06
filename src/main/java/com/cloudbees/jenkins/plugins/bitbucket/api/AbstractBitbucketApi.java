package com.cloudbees.jenkins.plugins.bitbucket.api;

import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerAPIClient;
import hudson.ProxyConfiguration;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
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
import org.apache.http.util.EntityUtils;

public abstract class AbstractBitbucketApi implements BitbucketApi {

    private static final Logger LOGGER = Logger.getLogger(AbstractBitbucketApi.class.getName());

    protected HttpClientContext context;

    @Override
    public URL checkURL(String aURL) {
        try {
            URL url = new URL(aURL);
            if ("localhost".equals(url.getHost())) {
                throw new IllegalStateException("Jenkins URL cannot start with http://localhost");
            }
            if ("unconfigured-jenkins-location".equals(url.getHost())) {
                throw new IllegalStateException("Could not determine Jenkins URL.");
            }
            return url;
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Bad Jenkins URL");
        }
    }

    protected String getResponseContent(CloseableHttpResponse response) throws IOException {
        EntityUtils.consume(response.getEntity());
        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK && response.getStatusLine().getStatusCode() != HttpStatus.SC_CREATED) {
            throw new BitbucketRequestException(response.getStatusLine().getStatusCode(), "HTTP request error. Status: " + response.getStatusLine().getStatusCode() + ": " + response.getStatusLine().getReasonPhrase() + ".\n" + response);
        }
        String content;
        long len = response.getEntity().getContentLength();
        if (len == 0) {
            content = "";
        } else {
            ByteArrayOutputStream buf;
            if (len > 0 && len <= Integer.MAX_VALUE / 2) {
                buf = new ByteArrayOutputStream((int) len);
            } else {
                buf = new ByteArrayOutputStream();
            }
            try (InputStream is = response.getEntity().getContent()) {
                IOUtils.copy(is, buf);
            }
            content = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }
        return content;
    }

    protected void setClientProxyParams(String host, HttpClientBuilder builder) {
        Jenkins jenkins = Jenkins.getInstance();
        ProxyConfiguration proxyConfig = jenkins.proxy;

        Proxy proxy = Proxy.NO_PROXY;
        if (proxyConfig != null) {
            URI hostURI = URI.create(host);
            proxy = proxyConfig.createProxy(hostURI.getHost());
        }

        if (proxy.type() != Proxy.Type.DIRECT) {
            final InetSocketAddress proxyAddress = (InetSocketAddress) proxy.address();
            LOGGER.log(Level.FINE, "Jenkins proxy: {0}", proxy.address());
            builder.setProxy(new HttpHost(proxyAddress.getHostName(), proxyAddress.getPort()));
            String username = proxyConfig.getUserName();
            String password = proxyConfig.getPassword();
            if (username != null && !"".equals(username.trim())) {
                LOGGER.fine("Using proxy authentication (user=" + username + ")");
                CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(
                    AuthScope.ANY, new UsernamePasswordCredentials(username, password));
                AuthCache authCache = new BasicAuthCache();
                authCache.put(HttpHost.create(proxyAddress.getHostName()), new BasicScheme());
                context = HttpClientContext.create();
                context.setCredentialsProvider(credentialsProvider);
                context.setAuthCache(authCache);
            }
        }
    }
}
