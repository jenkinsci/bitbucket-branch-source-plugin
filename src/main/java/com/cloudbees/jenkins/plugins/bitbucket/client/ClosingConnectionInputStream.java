package com.cloudbees.jenkins.plugins.bitbucket.client;

import java.io.IOException;
import java.io.InputStream;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;

public class ClosingConnectionInputStream extends InputStream {

    private final ClassicHttpResponse response;

    private final HttpUriRequest method;

    private final HttpClientConnectionManager connectionManager;

    private final InputStream delegate;

    public ClosingConnectionInputStream(final ClassicHttpResponse response,
                                        final HttpUriRequest method,
                                        final HttpClientConnectionManager connectionmanager) throws IOException {
        this.response = response;
        this.method = method;
        this.connectionManager = connectionmanager;
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
//FIXME        method.releaseConnection();
//FIXME        connectionManager.closeExpiredConnections();
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
