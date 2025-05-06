/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket.api;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;

/**
 * Bitbucket commit.
 */
public interface BitbucketCommit {

    /**
     * Returns the head commit author for this branch.
     *
     * @return the head commit author of this branch
     * @since 2.2.14
     */
    String getAuthor();

    /**
     * Returns the head commit author date for this branch.
     * <p>
     * If not supported by the server returns the same value of committer date.
     *
     * @return the author date in ISO format
     * @since 936.1.0
     */
    default Date getAuthorDate() {
        long millis = getDateMillis();
        return millis > 0 ? new Date(millis) : null;
    }

    /**
     * Returns the head committer for this branch.
     *
     * @return the head committer author of this branch
     * @since 936.1.0
     */
    default String getCommitter() {
        return getAuthor();
    }

    /**
     * Returns the head committer date for this branch.
     *
     * @return the author date in ISO format
     * @since 936.1.0
     */
    default Date getCommitterDate() {
        long millis = getDateMillis();
        return millis > 0 ? new Date(millis) : null;
    }

    /**
     * @return commit message
     */
    String getMessage();

    /**
     * @return the commit date in ISO format
     */
    @Deprecated(since = "936.1.0", forRemoval = true)
    String getDate();

    /**
     * @return the commit hash (complete, not reduced)
     */
    String getHash();

    /**
     * @return commit time in milliseconds (Java timestamp)
     */
    @Deprecated(since = "936.1.0", forRemoval = true)
    long getDateMillis();

    /**
     * Returns the SHA1 parents of this commit.
     *
     * @return a list of parent SHA1 commit.
     */
    default Collection<String> getParents() {
        return Collections.emptyList();
    }
}
