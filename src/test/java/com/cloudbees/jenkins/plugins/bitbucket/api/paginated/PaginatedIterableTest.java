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
package com.cloudbees.jenkins.plugins.bitbucket.api.paginated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginatedIteratorableTest {

    @Test
    void shouldIterateOverMultiplePages() {
        List<PaginatedList<Integer>> pages = List.of(
                new PaginatedList<>(List.of(1, 2), false),
                new PaginatedList<>(List.of(3, 4), false),
                new PaginatedList<>(List.of(5), true)
        );

        AtomicInteger index = new AtomicInteger(0);

        PageFetcher<Integer> fetcher = () -> pages.get(index.getAndIncrement());

        PaginatedIterable<Integer> iterable =
                new PaginatedIterable<>(() -> fetcher);

        List<Integer> result = new ArrayList<>();
        for (Integer i : iterable) {
            result.add(i);
        }

        assertEquals(List.of(1, 2, 3, 4, 5), result);
    }

    @Test
    void shouldHandleEmptyPagesBetweenData() {
        List<PaginatedList<Integer>> pages = List.of(
                new PaginatedList<>(Collections.emptyList(), false),
                new PaginatedList<>(List.of(1, 2), false),
                new PaginatedList<>(Collections.emptyList(), false),
                new PaginatedList<>(List.of(3), true)
        );

        AtomicInteger index = new AtomicInteger(0);

        PageFetcher<Integer> fetcher = () -> pages.get(index.getAndIncrement());

        PaginatedIterable<Integer> iterable =
                new PaginatedIterable<>(() -> fetcher);

        List<Integer> result = new ArrayList<>();
        for (Integer i : iterable) {
            result.add(i);
        }

        assertEquals(List.of(1, 2, 3), result);
    }

    @Test
    void shouldReturnFalseWhenNoElements() {
        List<PaginatedList<Integer>> pages = List.of(
                new PaginatedList<>(Collections.emptyList(), true)
        );

        PageFetcher<Integer> fetcher = () -> pages.get(0);

        PaginatedIterator<Integer> iterator = new PaginatedIterator<>(fetcher);

        assertFalse(iterator.hasNext());
    }

    @Test
    void shouldThrowExceptionWhenNoMoreElements() {
        List<PaginatedList<Integer>> pages = List.of(
                new PaginatedList<>(List.of(1), true)
        );

        PageFetcher<Integer> fetcher = () -> pages.get(0);

        PaginatedIterator<Integer> iterator = new PaginatedIterator<>(fetcher);

        assertTrue(iterator.hasNext());
        assertEquals(1, iterator.next());

        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    void shouldCreateNewFetcherPerIterableIterator() {
        AtomicInteger fetchCount = new AtomicInteger(0);

        Supplier<PageFetcher<Integer>> supplier = () -> () -> {
            fetchCount.incrementAndGet();
            return new PaginatedList<>(List.of(1), true);
        };

        PaginatedIterable<Integer> iterable = new PaginatedIterable<>(supplier);

        // First iteration
        List<Integer> firstRun = new ArrayList<>();
        for (Integer i : iterable) {
            firstRun.add(i);
        }

        // Second iteration (should use new fetcher)
        List<Integer> secondRun = new ArrayList<>();
        for (Integer i : iterable) {
            secondRun.add(i);
        }

        assertEquals(List.of(1), firstRun);
        assertEquals(List.of(1), secondRun);
        assertEquals(2, fetchCount.get()); // ensures new fetcher each time
    }
}
