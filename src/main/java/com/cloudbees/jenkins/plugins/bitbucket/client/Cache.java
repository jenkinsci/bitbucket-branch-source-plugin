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
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class Cache<K, V> {

    private static final int MAX_ENTRIES_DEFAULT = 100;

    private final Map<K, Entry<V>> entries;

    private long expireAfterNanos;

    public Cache(final int duration, final TimeUnit unit) {
        this(duration, unit, MAX_ENTRIES_DEFAULT);
    }

    public Cache(final int duration, final TimeUnit unit, final int maxEntries) {
        this.expireAfterNanos = unit.toNanos(duration);
        this.entries = new LimitedMap<>(maxEntries);
    }

    public synchronized <E extends Exception> V get(final K key, final ICheckedCallable<V, E> request) throws ExecutionException {
        return get(key, request, 0, NANOSECONDS);
    }

    /**
     * Returns a cached value, loading it when necessary, and optionally caches
     * a failed load for a shorter period.
     *
     * <p>Failure caching prevents callers waiting for the same key from
     * executing the same failing remote request one after another. A zero
     * failure duration retains the original behavior.
     *
     * @param key cache key
     * @param request value loader
     * @param failureDuration duration for which a loader failure is cached
     * @param failureUnit unit for {@code failureDuration}
     * @return the cached or loaded value
     * @throws ExecutionException if the loader failed, including a cached failure
     */
    public synchronized <E extends Exception> V get(
            final K key,
            final ICheckedCallable<V, E> request,
            final int failureDuration,
            final TimeUnit failureUnit) throws ExecutionException {
        if (isExpired(key)) {
            doRemove(key);
        }

        if (entries.containsKey(key)) {
            Entry<V> entry = entries.get(key);
            if (entry.failure != null) {
                throw new ExecutionException("Cached failure loading value for key: " + key, entry.failure);
            }
            return entry.value;
        }

        V result;
        try {
            result = request.call();
        } catch (final Exception e) {
            if (failureDuration > 0 && !(e instanceof InterruptedException)) {
                entries.put(key, Entry.failure(e, failureUnit.toNanos(failureDuration)));
            }
            throw new ExecutionException("Cannot load value for key: " + key, e);
        }

        return doPut(key, result);
    }

    public void evictAll() {
        entries.clear();
    }

    public void evict(@NonNull String key) {
        entries.remove(key);
    }

    public int size() {
        return entries.size();
    }

    public void setExpireDuration(final int duration, final TimeUnit unit) {
        this.expireAfterNanos = unit.toNanos(duration);
    }

    public Stat stats() {
        final List<Long> durations = new ArrayList<>();
        if (entries.size() > 0) {
            for (final Entry<V> e : entries.values()) {
                durations.add(System.nanoTime() - e.nanos);
            }
            Collections.sort(durations);
        } else {
            durations.add(0L);
            durations.add(0L);
        }
        return new Stat(entries.size(), durations.get(0), durations.get(durations.size() - 1));
    }

    private boolean isExpired(final K key) {
        final Entry<V> entry = entries.get(key);
        if (entry == null) {
            return false;
        }
        long expiration = entry.failure == null ? expireAfterNanos : entry.expireAfterNanos;
        return System.nanoTime() - entry.nanos > expiration;
    }

    private void doRemove(final K key) {
        entries.remove(key);
    }

    private V doPut(final K key, final V value) {
        entries.put(key, Entry.value(value));
        return value;
    }

    private static class LimitedMap<K, V> extends LinkedHashMap<K, V> { // NOSONAR
        private static final long serialVersionUID = 12492123640782072L;

        private final int maxEntries;

        public LimitedMap(final int maxEntries) {
            this.maxEntries = maxEntries;
        }

        @Override
        protected boolean removeEldestEntry(final java.util.Map.Entry<K, V> eldest) {
            return super.size() > maxEntries;
        }
    }

    private static class Entry<V> {
        private final V value;

        private final Exception failure;

        private final long nanos;

        private final long expireAfterNanos;

        private Entry(final V value, final Exception failure, final long expireAfterNanos) {
            this.value = value;
            this.failure = failure;
            this.expireAfterNanos = expireAfterNanos;
            nanos = System.nanoTime();
        }

        private static <V> Entry<V> value(final V value) {
            return new Entry<>(value, null, 0);
        }

        private static <V> Entry<V> failure(final Exception failure, final long expireAfterNanos) {
            return new Entry<>(null, failure, expireAfterNanos);
        }
    }

    public static class Stat {
        private final int count;

        private final long minDuration;

        private final long maxDuration;

        public Stat(final int count, final long minDuration, final long maxDuration) {
            this.count = count;
            this.minDuration = minDuration;
            this.maxDuration = maxDuration;
        }

        public int getCount() {
            return count;
        }

        public long getMinDuration() {
            return minDuration;
        }

        public long getMaxDuration() {
            return maxDuration;
        }

        @Override
        public String toString() {
            if (count == 0) {
                return "No entry.";
            } else {
                final StringBuilder builder = new StringBuilder();
                if (count == 1) {
                    builder.append("1 entry, since ").append(NANOSECONDS.toMinutes(minDuration)).append(
                            " minutes");
                } else {
                    builder.append(count).append(" entries, since ").append(
                            NANOSECONDS.toMinutes(minDuration)).append(" (youngest) to ").append(
                                    NANOSECONDS.toMinutes(maxDuration)).append(" (oldest) minutes.");
                }
                return builder.toString();
            }
        }
    }

}
