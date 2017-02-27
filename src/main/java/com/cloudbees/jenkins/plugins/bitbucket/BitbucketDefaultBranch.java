/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.InvisibleAction;
import java.io.Serializable;

/**
 * Represents the default branch of a specific repository
 */
public class BitbucketDefaultBranch extends InvisibleAction implements Serializable {
    private static final long serialVersionUID = 1L;
    @NonNull
    private final String repoOwner;
    @NonNull
    private final String repository;
    @NonNull
    private final String defaultBranch;

    public BitbucketDefaultBranch(@NonNull String repoOwner, @NonNull String repository, @NonNull String defaultBranch) {
        this.repoOwner = repoOwner;
        this.repository = repository;
        this.defaultBranch = defaultBranch;
    }

    @NonNull
    public String getRepoOwner() {
        return repoOwner;
    }

    @NonNull
    public String getRepository() {
        return repository;
    }

    @NonNull
    public String getDefaultBranch() {
        return defaultBranch;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BitbucketDefaultBranch that = (BitbucketDefaultBranch) o;

        if (!repoOwner.equals(that.repoOwner)) {
            return false;
        }
        if (!repository.equals(that.repository)) {
            return false;
        }
        return defaultBranch.equals(that.defaultBranch);
    }

    @Override
    public int hashCode() {
        int result = repoOwner.hashCode();
        result = 31 * result + repository.hashCode();
        result = 31 * result + defaultBranch.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "BitbucketDefaultBranch{" +
                "repoOwner='" + repoOwner + '\'' +
                ", repository='" + repository + '\'' +
                ", defaultBranch='" + defaultBranch + '\'' +
                '}';
    }


}
