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
 * Represents a Bitbucket team (or a Project when working with Bitbucket Server).
 */
public interface BitbucketTeam {

    /**
     * @return team or project name
     */
    String getName();

    /**
     * @return team or project display name.
     */
    String getDisplayName();

    /**
     * Gets the links of the project.
     *
     * @return the links of the project.
     */
    Map<String, List<BitbucketHref>> getLinks();

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
     * Return the avatar associated to the team or project name.
     *
     * @return the URL of the avatar
     */
    String getAvatar();
}
