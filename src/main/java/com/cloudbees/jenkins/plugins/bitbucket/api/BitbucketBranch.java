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

/**
 * Bitbucket Branch.
 *
 * It's used to represent branches to be built and source branches for pull requests.
 */
public interface BitbucketBranch {

    /**
     * @return the head commit node of this branch
     */
    String getRawNode();

    /**
     * @return the branch name
     */
    String getName();

    /**
     * @return the commit milliseconds from epoch
     */
    long getDateMillis();

    /**
     * Returns the head commit message for this branch.
     *
     * @return the head commit message of this branch
     * @author Nikolas Falco
     * @since 2.2.14
     */
    String getMessage();

    /**
     * Returns the head commit author for this branch.
     *
     * @return the head commit author of this branch
     * @author Nikolas Falco
     * @since 2.2.14
     */
    String getAuthor();

}
