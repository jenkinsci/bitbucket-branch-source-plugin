/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
