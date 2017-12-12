package com.cloudbees.jenkins.plugins.bitbucket.client;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodBase;

import java.io.IOException;
import java.io.InputStream;

public class ClosingConnectionInputStream extends InputStream {

    private final InputStream delegate;

    private final HttpMethodBase method;

    private final HttpClient client;

    public ClosingConnectionInputStream(final InputStream delegate, final HttpMethodBase method,
            final HttpClient client) {
        this.delegate = delegate;
        this.method = method;
        this.client = client;
    }

    @Override
    public int available() throws IOException {
        return delegate.available();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
        method.releaseConnection();
        client.getHttpConnectionManager().closeIdleConnections(0);
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
