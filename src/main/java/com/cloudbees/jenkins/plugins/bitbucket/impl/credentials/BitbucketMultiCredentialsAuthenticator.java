/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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


package com.cloudbees.jenkins.plugins.bitbucket.impl.credentials;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.hc.core5.http.HttpRequest;

// TODO verify what is the best between BitbucketAuthenticator or an helper class in the API client,
// BitbucketAuthenticator is transparent to the API Client and user and could build it outside.
// helper class could also handle the header instead hardcode in the client logic and simplify the setupClientBuilder() method that does not have to hardcode this class for avoid register a retryStrategy ? 
public class BitbucketMultiCredentialsAuthenticator implements BitbucketAuthenticator {

    /**
     * Cache of timestamps of when credentials reach the rate limit.
     */
    private static final Map<String, Date> rateLimitCache = new ConcurrentHashMap<>();

    private final List<BitbucketAuthenticator> authenticators;
    private BitbucketAuthenticator current;
    private int nextIds;

    public BitbucketMultiCredentialsAuthenticator(@NonNull List<BitbucketAuthenticator> autheticators) {
        verify(autheticators);
        this.authenticators = new ArrayList<>(autheticators);
        this.nextIds = 0;
    }

    private void verify(List<BitbucketAuthenticator> autheticators) {
        Class<? extends BitbucketAuthenticator> template = null;
        for (BitbucketAuthenticator auth : autheticators) {
            if (template == null) {
                template = auth.getClass();
            } else if (!auth.getClass().equals(template)) {
                throw new IllegalArgumentException("Alternative credentials must be of the same type");
            }
        }
    }

    @Override
    public void configureRequest(HttpRequest request) {
        current().configureRequest(request);
    }

    @Override
    public String getId() {
        return current().getId();
    }

    private BitbucketAuthenticator current() {
        if (current == null || rateLimitCache.containsKey(current.getId())) {
            current = next(); 
        }
        return current;
    }

    private BitbucketAuthenticator next() {
        BitbucketAuthenticator next = authenticators.get(nextIds % authenticators.size());
        int remaining = authenticators.size();
        while (rateLimitCache.containsKey(next.getId()) && remaining > 0) {
            nextIds += 1;
            remaining -= 1;
            next = authenticators.get(nextIds % authenticators.size());
        }
        if (remaining == 0) {
            // all credentials are expired
            throw new IllegalStateException("All credentials reach the rate API limit.");
        }
        current = next;
        return next;
    }

    public synchronized void markExpired() {
        Date now = new Date();

        // bitbucket cloud rolling windows is 1 hour per 1000 calls
        // https://support.atlassian.com/bitbucket-cloud/kb/bitbucket-cloud-rate-limit-troubleshooting/
        rateLimitCache.putIfAbsent(current().getId(), now);

        // remove from cache all credentials out from the rolling window
        for (Entry<String, Date> entry : rateLimitCache.entrySet()) {
            if (DateUtils.addHours(entry.getValue(), 1).before(now)) {
                // rolling windows passed, reset credentials limit
                rateLimitCache.remove(entry.getKey());
            }
        }
        next();
    }
}
