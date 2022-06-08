/*
 * The MIT License
 *
 * Copyright (c) 2016-2017, CloudBees, Inc.
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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * Bitbucket paginated resource
 */
public class BitbucketCloudPage<T> extends BitbucketPage<T> {
    private final int pageLength;
    private final int page;
    private final int size;
    private final String next;
    private final List<T> values;

    public BitbucketCloudPage(@JsonProperty("pagelen") int pageLength,
                       @JsonProperty("page") int page,
                       @JsonProperty("size") int size,
                       @Nullable @JsonProperty("next") String next,
                       @NonNull @JsonProperty("values") List<T> values) {
        this.pageLength = pageLength;
        this.page = page;
        this.size = size;
        this.next = next;
        this.values = ImmutableList.copyOf(values);
    }

    public int getSize() {
        return size;
    }

    public List<T> getValues() {
        return values;
    }

    public String getNext(){
        return next;
    }

    public boolean isLastPage() {
        return next == null;
    }

    public int getPageLength() {
        return pageLength;
    }

    public int getPage() {
        return page;
    }
}
