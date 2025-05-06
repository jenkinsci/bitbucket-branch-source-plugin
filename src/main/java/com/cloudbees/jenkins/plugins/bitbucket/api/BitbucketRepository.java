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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;

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
     * Is the repository marked as archived. Bitbucket 8.0 introduced the ability to "Archive" a repository which
     * makes the repository read-only and distinguishable from "Active" repositories.
     * @return true if the repository is marked as archived, false otherwise
     */
    boolean isArchived();

    /**
     * Get Link based on name
     *
     * @param name - link type - one of(self, html, avatar)
     * @return href string if there is one, else null
     */
    default String getLink(String name) {
        Map<String, List<BitbucketHref>> links = getLinks();
        if (links == null) {
            return null;
        }
        List<BitbucketHref> hrefs = links.get(name);
        if (hrefs == null || hrefs.isEmpty()) {
            return null;
        }
        BitbucketHref href = hrefs.get(0);
        return href == null ? null : href.getHref();
    }

    /**
     * Gets the links for this repository.
     * @return the links for this repository.
     */
    Map<String, List<BitbucketHref>> getLinks();

    /**
     * Returns the avatar associated to the team or project name.
     *
     * @return the URL of the avatar
     */
    String getAvatar();

    /**
     * Returns the clone link available for this repository.
     *
     * @return a list of git URL available to clone.
     */
    @NonNull
    default List<BitbucketHref> getCloneLinks() {
        Map<String, List<BitbucketHref>> links = getLinks();
        if (links == null) {
            return Collections.emptyList();
        }
        List<BitbucketHref> hrefs = links.get("clone");
        if (CollectionUtils.isEmpty(hrefs)) {
            return Collections.emptyList();
        } else {
            return Collections.unmodifiableList(hrefs);
        }
    }
}
