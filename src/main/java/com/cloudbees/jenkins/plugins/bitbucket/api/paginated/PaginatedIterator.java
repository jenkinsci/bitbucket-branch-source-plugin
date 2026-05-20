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

import java.util.Iterator;
import java.util.NoSuchElementException;

public class PaginatedIterator<T> implements Iterator<T> {

    private final PageFetcher<T> fetcher;
    private Iterator<T> currentIterator;
    private boolean lastPageReached = false;

    public PaginatedIterator(PageFetcher<T> fetcher) {
        this.fetcher = fetcher;
        loadNextPage();
    }

    private void loadNextPage() {
        if (this.lastPageReached) return;

        PaginatedList<T> page = fetcher.fetchNextPage();

        this.lastPageReached = page.isLastPage();

        if (page.hasItems()) {
            currentIterator = page.iterator();
        } else {
            currentIterator = null;
        }
    }

    @Override
    public boolean hasNext() {
        if (currentIterator != null && currentIterator.hasNext()) {
            return true;
        } else if (!this.lastPageReached) {
            loadNextPage();
            return hasNext();
        }

        return false;
    }

    @Override
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return currentIterator.next();
    }
}
