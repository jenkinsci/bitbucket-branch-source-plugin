package com.cloudbees.jenkins.plugins.bitbucket.client;

import java.io.IOException;
import java.io.InputStream;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

public class ClosingConnectionInputStream extends InputStream {

    private final CloseableHttpResponse response;

    private final HttpRequestBase method;

    private final PoolingHttpClientConnectionManager connectionManager;

    private final InputStream delegate;

    public ClosingConnectionInputStream(final CloseableHttpResponse response, final HttpRequestBase method,
            final PoolingHttpClientConnectionManager connectionManager)
            throws UnsupportedOperationException, IOException {
        this.response = response;
        this.method = method;
        this.connectionManager = connectionManager;
        this.delegate = response.getEntity().getContent();
    }

    @Override
    public int available() throws IOException {
        return delegate.available();
    }

    @Override
    public void close() throws IOException {
        EntityUtils.consume(response.getEntity());
        delegate.close();
        method.releaseConnection();
        connectionManager.closeExpiredConnections();
    }

    @Override
    public synchronized void mark(final int readlimit) {
        delegate.mark(readlimit);
    }

    @Override
    public boolean markSupported() {
        return delegate.markSupported();
    }

    @Override
    public int read() throws IOException {
        return delegate.read();
    }

    @Override
    public int read(final byte[] b) throws IOException {
        return delegate.read(b);
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        return delegate.read(b, off, len);
    }

    @Override
    public synchronized void reset() throws IOException {
        delegate.reset();
    }

    @Override
    public long skip(final long n) throws IOException {
        return delegate.skip(n);
    }
}
