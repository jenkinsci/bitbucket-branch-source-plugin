/*
 * The MIT License
 *
 * Copyright (c) 2017-2018, bguerin
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

import com.cloudbees.jenkins.plugins.bitbucket.impl.client.ICheckedCallable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CacheTest {

    @Test
    void ensure_cache_hit() throws Exception {
        final Cache<String, Long> cache = new Cache<>(5, TimeUnit.HOURS);
        @SuppressWarnings("unchecked")
        final ICheckedCallable<Long, Exception> callable = mock(ICheckedCallable.class);
        when(callable.call()).thenReturn(1L);

        assertThat(cache.get("a key", callable)).isEqualTo(1L);
        assertThat(cache.get("a key", callable)).isEqualTo(1L);

        verify(callable).call();
        verifyNoMoreInteractions(callable);
    }

    @Test
    void ensure_expiration_works() throws Exception {
        final Cache<String, Long> cache = new Cache<>(1, TimeUnit.NANOSECONDS);
        @SuppressWarnings("unchecked")
        final ICheckedCallable<Long, Exception> callable = mock(ICheckedCallable.class);
        when(callable.call()).thenReturn(1L);

        assertThat(cache.get("a key", callable)).isEqualTo(1L);
        Thread.sleep(200);
        assertThat(cache.get("a key", callable)).isEqualTo(1L);

        verify(callable, times(2)).call();
        verifyNoMoreInteractions(callable);
    }

    @Test
    void ensure_max_entries_works() throws Exception {
        final Cache<String, Long> cache = new Cache<>(1, TimeUnit.NANOSECONDS, 10);
        @SuppressWarnings("unchecked")
        final ICheckedCallable<Long, Exception> callable = mock(ICheckedCallable.class);
        when(callable.call()).thenReturn(1L);

        for (int i = 0; i < 10; i++) {
            cache.get("key" + i, callable);
        }
        assertThat(cache.size()).isEqualTo(10);

        cache.get("another key", callable);
        assertThat(cache.size()).isEqualTo(10);
    }

    @Test
    void ensure_failure_is_cached_for_configured_duration() throws Exception {
        final Cache<String, Long> cache = new Cache<>(5, TimeUnit.HOURS);
        @SuppressWarnings("unchecked")
        final ICheckedCallable<Long, IOException> callable = mock(ICheckedCallable.class);
        when(callable.call()).thenThrow(new IOException("rate limited"));

        assertThatThrownBy(() -> cache.get("a key", callable, 1, TimeUnit.MINUTES))
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseMessage("rate limited");
        assertThatThrownBy(() -> cache.get("a key", callable, 1, TimeUnit.MINUTES))
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseMessage("rate limited");

        verify(callable).call();
        verifyNoMoreInteractions(callable);
    }

    @Test
    void ensure_concurrent_failures_execute_loader_once() throws Exception {
        final Cache<String, Long> cache = new Cache<>(5, TimeUnit.HOURS);
        final AtomicInteger calls = new AtomicInteger();
        final ICheckedCallable<Long, IOException> loader = () -> {
            calls.incrementAndGet();
            throw new IOException("rate limited");
        };

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                results.add(executor.submit(() -> assertThatThrownBy(
                                () -> cache.get("a key", loader, 1, TimeUnit.MINUTES))
                        .isInstanceOf(ExecutionException.class)
                        .hasRootCauseMessage("rate limited")));
            }
            for (Future<?> result : results) {
                result.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(calls).hasValue(1);
    }
}
