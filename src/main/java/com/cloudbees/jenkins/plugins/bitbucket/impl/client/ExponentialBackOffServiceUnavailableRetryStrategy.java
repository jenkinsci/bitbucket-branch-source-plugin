/*
 * The MIT License
 *
 * Copyright (c) 2025, Nikolas Falco
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

import java.util.concurrent.TimeUnit;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.impl.client.cache.AsynchronousValidationRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;

/**
 * An implementation that backs off exponentially based on the number of
 * consecutive failed attempts. It uses the following defaults:
 * <pre>
 *         no delay in case it was never tried or didn't fail so far
 *     6 secs delay for one failed attempt (= {@link #getInitialExpiryInMillis()})
 *    60 secs delay for two failed attempts
 *    10 mins delay for three failed attempts
 *   100 mins delay for four failed attempts
 *  ~16 hours delay for five failed attempts
 *   24 hours delay for six or more failed attempts (= {@link #getMaxExpiryInMillis()})
 * </pre>
 *
 * The following equation is used to calculate the delay for a specific revalidation request:
 * <pre>
 *     delay = {@link #getInitialExpiryInMillis()} * Math.pow({@link #getBackOffRate()},
 *     {@link AsynchronousValidationRequest#getConsecutiveFailedAttempts()} - 1))
 * </pre>
 * The resulting delay won't exceed {@link #getMaxExpiryInMillis()}.
 */
@Contract(threading = ThreadingBehavior.SAFE)
public class ExponentialBackOffServiceUnavailableRetryStrategy implements ServiceUnavailableRetryStrategy {

    public static final long DEFAULT_BACK_OFF_RATE = 10;
    public static final long DEFAULT_INITIAL_EXPIRY_IN_MILLIS = TimeUnit.SECONDS.toMillis(6);
    public static final long DEFAULT_MAX_EXPIRY_IN_MILLIS = TimeUnit.SECONDS.toMillis(86400);

    private final long backOffRate;
    private final long initialExpiryInMillis;
    private final long maxExpiryInMillis;
    private ThreadLocal<Integer> consecutiveFailedAttempts; // TODO call ThreadLocal#remove method is not possible using the lifecycle of apache client 4.x. Move to http client 5.x ASAP

    /**
     * Create a new strategy using a fixed pool of worker threads.
     */
    public ExponentialBackOffServiceUnavailableRetryStrategy() {
        this(DEFAULT_BACK_OFF_RATE,
             DEFAULT_INITIAL_EXPIRY_IN_MILLIS,
             DEFAULT_MAX_EXPIRY_IN_MILLIS);
    }

    /**
     * Create a new strategy by using a fixed pool of worker threads and the
     * given parameters to calculated the delay.
     *
     * @param backOffRate the back off rate to be used; not negative
     * @param initialExpiryInMillis the initial expiry in milli seconds; not negative
     * @param maxExpiryInMillis the upper limit of the delay in milli seconds; not negative
     */
    public ExponentialBackOffServiceUnavailableRetryStrategy(
            final long backOffRate,
            final long initialExpiryInMillis,
            final long maxExpiryInMillis) {
        this.backOffRate = Args.notNegative(backOffRate, "BackOffRate");
        this.initialExpiryInMillis = Args.notNegative(initialExpiryInMillis, "InitialExpiryInMillis");
        this.maxExpiryInMillis = Args.notNegative(maxExpiryInMillis, "MaxExpiryInMillis");
        this.consecutiveFailedAttempts = new ThreadLocal<>();
    }

    public long getBackOffRate() {
        return backOffRate;
    }

    public long getInitialExpiryInMillis() {
        return initialExpiryInMillis;
    }

    public long getMaxExpiryInMillis() {
        return maxExpiryInMillis;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean retryRequest(HttpResponse response, int executionCount, HttpContext context) {
        int statusCode = response.getStatusLine().getStatusCode();
        consecutiveFailedAttempts.set(executionCount);
        return getRetryInterval(executionCount) < maxExpiryInMillis
                && (statusCode == HttpStatus.SC_TOO_MANY_REQUESTS
                || statusCode == HttpStatus.SC_SERVICE_UNAVAILABLE);
    }

    private long getRetryInterval(int failedAttempts) {
        if (failedAttempts > 0) {
            final long delayInSeconds = (long) (initialExpiryInMillis * Math.pow(backOffRate, failedAttempts - 1));
            return Math.min(delayInSeconds, maxExpiryInMillis);
        } else {
            return 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRetryInterval() {
        Integer attempts = consecutiveFailedAttempts.get();
        return getRetryInterval(attempts == null ? 0 : attempts.intValue());
    }

}
