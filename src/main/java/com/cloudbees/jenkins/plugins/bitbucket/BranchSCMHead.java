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
package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryType;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import jenkins.scm.api.SCMHead;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

/**
 * {@link SCMHead} for a BitBucket branch.
 *
 * @since 2.0.0
 */
public class BranchSCMHead extends SCMHead {

    private static final long serialVersionUID = 1L;

    /**
     * Cache of the repository type. Will only be {@code null} for data loaded from pre-2.1.0 releases
     *
     * @since 2.1.0
     */
    // The repository type should be immutable for any SCMSource.
    @CheckForNull
    private final BitbucketRepositoryType repositoryType;

    /**
     * Constructor.
     *
     * @param branchName the branch name
     * @deprecated use {@link #BranchSCMHead(String, BitbucketRepositoryType)}
     */
    @Deprecated
    @Restricted(DoNotUse.class)
    public BranchSCMHead(String branchName) {
        this(branchName, null);
    }

    /**
     * Constructor.
     *
     * @param branchName     the branch name
     * @param repositoryType the repository type.
     */
    public BranchSCMHead(String branchName, BitbucketRepositoryType repositoryType) {
        super(branchName);
        this.repositoryType = repositoryType;
    }

    /**
     * Gets the repository type.
     * @return the repository type or {@code null} if this is a legacy branch instance.
     */
    @CheckForNull
    public BitbucketRepositoryType getRepositoryType() {
        return repositoryType;
    }
}
