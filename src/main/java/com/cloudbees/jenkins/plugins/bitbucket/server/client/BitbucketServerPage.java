/*
 * The MIT License
 *
 * Copyright (c) 2016-2018, Yieldlab AG
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
package com.cloudbees.jenkins.plugins.bitbucket.server.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * Bitbucket Server paginated resource
 */
public class BitbucketServerPage<V> {

    private List<V> values;

    private Integer size;
    private Integer limit;
    private Integer start;
    private Integer nextPageStart;
    private Boolean lastPage;

    public BitbucketServerPage(@JsonProperty("start") int start,
                               @JsonProperty("limit") int limit,
                               @JsonProperty("size") int size,
                               @Nullable @JsonProperty("nextPageStart") Integer nextPageStart,
                               @Nullable @JsonProperty("isLastPage") Boolean lastPage,
                               @NonNull @JsonProperty("values") List<V> values) {
        this.start = start;
        this.limit = limit;
        this.size = size;
        this.nextPageStart = nextPageStart;
        this.lastPage = lastPage;
        this.values = ImmutableList.copyOf(values);
    }

    public List<V> getValues() {
        return values;
    }

    public Integer getSize() {
        return size;
    }

    public Integer getLimit() {
        return limit;
    }

    public Integer getStart() {
        return start;
    }

    public Integer getNextPageStart() {
        return nextPageStart;
    }

    public boolean isLastPage() {
        return lastPage != null && lastPage;
    }

}
