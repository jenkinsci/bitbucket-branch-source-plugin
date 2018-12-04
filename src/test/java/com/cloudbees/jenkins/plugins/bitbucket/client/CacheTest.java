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


import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class CacheTest {

    @Test
    public void ensure_cache_hit() throws Exception {
        final Cache<String, Long> cache = new Cache<>(5, TimeUnit.HOURS);
        final Callable<Long> callable = mock(Callable.class);
        when(callable.call()).thenReturn(1L);

        assertEquals(Long.valueOf(1L), cache.get("a key", callable));
        assertEquals(Long.valueOf(1L), cache.get("a key", callable));

        verify(callable).call();
        verifyNoMoreInteractions(callable);
    }

    @Test
    public void ensure_expiration_works() throws Exception {
        final Cache<String, Long> cache = new Cache<>(1, TimeUnit.NANOSECONDS);
        final Callable<Long> callable = mock(Callable.class);
        when(callable.call()).thenReturn(1L);

        assertEquals(Long.valueOf(1L), cache.get("a key", callable));
        Thread.sleep(200);
        assertEquals(Long.valueOf(1L), cache.get("a key", callable));

        verify(callable, times(2)).call();
        verifyNoMoreInteractions(callable);
    }

    @Test
    public void ensure_max_entries_works() throws Exception {
        final Cache<String, Long> cache = new Cache<>(1, TimeUnit.NANOSECONDS, 10);
        final Callable<Long> callable = mock(Callable.class);
        when(callable.call()).thenReturn(1L);

        for (int i = 0; i < 10; i++) {
            cache.get("key" + i, callable);
        }
        assertEquals(10, cache.size());

        cache.get("another key", callable);
        assertEquals(10, cache.size());
    }
}
