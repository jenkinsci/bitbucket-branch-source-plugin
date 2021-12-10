/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryType;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.plugins.git.GitTagSCMHead;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.TagSCMHead;

/**
 * {@link SCMHead} for a Bitbucket tags.
 *
 * @since 2.2.11
 */
public class BitbucketTagSCMHead extends GitTagSCMHead implements TagSCMHead {

    private static final long serialVersionUID = 1L;

    /**
     * Cache of the repository type.
     *
     * @since 2.2.11
     */
    // The repository type should be immutable for any SCMSource.
    @CheckForNull
    private final BitbucketRepositoryType repositoryType;

    /**
     * Constructor.
     *
     * @param tagName        the tag name
     * @param timestamp      the timestamp of tag
     */
    public BitbucketTagSCMHead(@NonNull String tagName, long timestamp) {
        super(tagName, timestamp);
        this.repositoryType = BitbucketRepositoryType.GIT;
    }

    /**
     * Gets the repository type.
     * @return the repository type or {@code null}
     */
    @CheckForNull
    public BitbucketRepositoryType getRepositoryType() {
        return repositoryType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPronoun() {
        return Messages.BitbucketTagSCMHead_Pronoun();
    }
}
