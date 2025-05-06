/*
 * The MIT License
 *
 * Copyright (c) 2025, CloudBees, Inc.
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
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.http.impl.client.cache.AsynchronousValidationRequest;

/**
 * An implementation that backs off exponentially based on the number of
 * consecutive failed attempts. It uses the following defaults:
 * <pre>
 *         no delay in case it was never tried or didn't fail so far
 *     6 secs delay for one failed attempt (= initialExpiryInMillis)
 *    60 secs delay for two failed attempts
 *    10 mins delay for three failed attempts
 *   100 mins delay for four failed attempts
 *  ~16 hours delay for five failed attempts
 *   24 hours delay for six or more failed attempts (= maxExpiryInMillis)
 * </pre>
 *
 * The following equation is used to calculate the delay for a specific revalidation request:
 * <pre>
 *     delay = initialExpiryInMillis * Math.pow(backOffRate, {@link AsynchronousValidationRequest#getConsecutiveFailedAttempts()} - 1))
 * </pre>
 * The resulting delay won't exceed maxExpiryInMillis.
 */
@Contract(threading = ThreadingBehavior.SAFE)
public class ExponentialBackoffRetryStrategy extends DefaultHttpRequestRetryStrategy {

    public static final long DEFAULT_BACK_OFF_RATE = 10;
    public static final long DEFAULT_INITIAL_EXPIRY_IN_MILLIS = TimeUnit.SECONDS.toMillis(6);
    public static final long DEFAULT_MAX_EXPIRY_IN_MILLIS = TimeUnit.SECONDS.toMillis(86400);

    private final long backOffRate;
    private final long initialExpiryInMillis;
    private final long maxExpiryInMillis;
    /**
     * Derived {@code IOExceptions} which shall not be retried
     */
    private final Set<Class<? extends IOException>> nonRetriableIOExceptionClasses;

    /**
     * Create a new strategy using a fixed pool of worker threads.
     */
    public ExponentialBackoffRetryStrategy() {
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
    public ExponentialBackoffRetryStrategy(
            final long backOffRate,
            final long initialExpiryInMillis,
            final long maxExpiryInMillis) {
        this.backOffRate = Args.notNegative(backOffRate, "BackOffRate");
        this.initialExpiryInMillis = Args.notNegative(initialExpiryInMillis, "InitialExpiryInMillis");
        this.maxExpiryInMillis = Args.notNegative(maxExpiryInMillis, "MaxExpiryInMillis");
        this.nonRetriableIOExceptionClasses = Set.of(
                InterruptedIOException.class,
                UnknownHostException.class,
                ConnectException.class,
                ConnectionClosedException.class,
                NoRouteToHostException.class,
                SSLException.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean retryRequest(HttpResponse response, int executionCount, HttpContext context) {
        int statusCode = response.getCode();
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

    @Override
    public boolean retryRequest(HttpRequest request, IOException exception, int execCount, HttpContext context) {
        if (this.nonRetriableIOExceptionClasses.contains(exception.getClass())) {
            return false;
        } else {
            for (final Class<? extends IOException> rejectException : this.nonRetriableIOExceptionClasses) {
                if (rejectException.isInstance(exception)) {
                    return false;
                }
            }
        }
        if (request instanceof CancellableDependency cancellable && cancellable.isCancelled()) {
            return false;
        }

        // Retry if the request is considered idempotent
        return handleAsIdempotent(request);
    }

    @Override
    public TimeValue getRetryInterval(HttpRequest request, IOException exception, int execCount, HttpContext context) {
        return TimeValue.ofMilliseconds(getRetryInterval(execCount));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TimeValue getRetryInterval(HttpResponse response, int execCount, HttpContext context) {
        final Header header = response.getFirstHeader(HttpHeaders.RETRY_AFTER);
        TimeValue retryAfter = null;
        if (header != null) {
            final String value = header.getValue();
            try {
                retryAfter = TimeValue.ofSeconds(Long.parseLong(value));
            } catch (final NumberFormatException ignore) {
                final Instant retryAfterDate = DateUtils.parseStandardDate(value);
                if (retryAfterDate != null) {
                    retryAfter = TimeValue.ofMilliseconds(retryAfterDate.toEpochMilli() - System.currentTimeMillis());
                }
            }

            if (TimeValue.isPositive(retryAfter)) {
                return retryAfter;
            }
        }
        return TimeValue.ofMilliseconds(getRetryInterval(execCount));
    }

}
