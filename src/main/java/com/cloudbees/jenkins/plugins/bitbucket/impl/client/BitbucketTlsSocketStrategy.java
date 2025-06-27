/*
 * The MIT License
 *
 * Copyright (c) 2025, Falco Nikolas
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

import java.io.IOException;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Custom implementation of a TlsSocketStrategu to replicate what
 * {@code org.apache.http.impl.conn.DefaultHttpClientConnectionOperator#getSocketFactoryRegistry(HttpContext)}
 * did in Apache Client HTTP 4 implementation.
 */
@Contract(threading = ThreadingBehavior.SAFE)
public class BitbucketTlsSocketStrategy implements TlsSocketStrategy {
    public static final String SOCKET_FACTORY_REGISTRY = "http.socket-factory-registry";

    private TlsSocketStrategy defaultStrategy;

    @Override
    public SSLSocket upgrade(Socket socket, String target, int port, Object attachment, HttpContext context) throws IOException {
        TlsSocketStrategy strategy = defaultStrategy;

        Object value = context.getAttribute(SOCKET_FACTORY_REGISTRY);
        if (value instanceof SSLContext sslContext) {
            strategy = new DefaultClientTlsStrategy(sslContext);
        } else if (defaultStrategy == null) {
            try {
                strategy = new DefaultClientTlsStrategy(SSLContext.getDefault());
            } catch (NoSuchAlgorithmException e) {
                throw new IOException(e);
            }
            defaultStrategy = strategy;
        }
        return strategy.upgrade(socket, target, port, attachment, context);
    }

}
