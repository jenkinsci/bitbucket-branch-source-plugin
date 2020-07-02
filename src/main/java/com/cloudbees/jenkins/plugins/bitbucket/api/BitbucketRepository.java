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

import java.util.List;
import java.util.Map;

/**
 * Represents a Bitbucket repository.
 */
public interface BitbucketRepository {

    /**
     * @return the scm type (git)
     * @deprecated no longer a choice
     */
    @Deprecated
    String getScm();

    /**
     * @return full repository name, which is owner/name (where owner could be a user, a team or a project)
     */
    String getFullName();


    /**
     * @return the project containing the repository
     */
    BitbucketProject getProject();

    /**
     * @return repository owner (could be a user, a team or a project)
     */
    BitbucketRepositoryOwner getOwner();

    /**
     * @return {@link #getOwner()}'s name
     */
    String getOwnerName();

    /**
     * @return the repository name (as extracted from {@link #getFullName()})
     */
    String getRepositoryName();

    /**
     * @return return true if the repository is a private one (false otherwise).
     */
    boolean isPrivate();

    /**
     * Gets the links for this repository.
     * @return the links for this repository.
     */
    Map<String, List<BitbucketHref>> getLinks();

}
