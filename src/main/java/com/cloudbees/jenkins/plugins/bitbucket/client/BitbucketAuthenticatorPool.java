/*
 * The MIT License
 *
 * Copyright (c) 2026, CloudBees, Inc.
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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;

/** Ordered primary/fallback authenticators used for Bitbucket Cloud API calls. */
public final class BitbucketAuthenticatorPool implements BitbucketAuthenticator {
    private final List<BitbucketAuthenticator> authenticators;
    private final AtomicInteger current = new AtomicInteger();

    public BitbucketAuthenticatorPool(@NonNull List<BitbucketAuthenticator> authenticators) {
        LinkedHashMap<String, BitbucketAuthenticator> unique = new LinkedHashMap<>();
        authenticators.forEach(authenticator -> unique.putIfAbsent(authenticator.getId(), authenticator));
        if (unique.isEmpty()) {
            throw new IllegalArgumentException("At least one authenticator is required");
        }
        this.authenticators = new ArrayList<>(unique.values());
        Class<?> authenticatorType = this.authenticators.get(0).getClass();
        if (this.authenticators.stream().anyMatch(authenticator -> authenticator.getClass() != authenticatorType)) {
            throw new IllegalArgumentException("All authenticators must have the same type");
        }
    }

    public int size() {
        return authenticators.size();
    }

    public boolean advance() {
        if (authenticators.size() < 2) {
            return false;
        }
        current.updateAndGet(index -> (index + 1) % authenticators.size());
        return true;
    }

    BitbucketAuthenticator current() {
        return authenticators.get(current.get());
    }

    @Override
    public String getId() {
        return current().getId();
    }

    @Override
    public void configureBuilder(HttpClientBuilder builder) {
        // Pool members are required to have the same type, so their builder setup is
        // equivalent. Applying every setup can corrupt mutually exclusive settings
        // such as client certificates.
        current().configureBuilder(builder);
    }

    @Override
    public void configureContext(HttpClientContext context, HttpHost host) {
        current().configureContext(context, host);
    }

    @Override
    public void configureRequest(HttpRequest request) {
        request.removeHeaders(HttpHeaders.AUTHORIZATION);
        current().configureRequest(request);
    }
}
