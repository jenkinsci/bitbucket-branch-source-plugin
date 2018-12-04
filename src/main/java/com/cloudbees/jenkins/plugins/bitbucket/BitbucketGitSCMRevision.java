/*
 * The MIT License
 *
 * Copyright (c) 2018, Nikolas Falco
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

import com.cloudbees.jenkins.plugins.bitbucket.JsonParser.BitbucketDateFormat;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.text.ParseException;
import java.util.Date;
import jenkins.plugins.git.AbstractGitSCMSource.SCMRevisionImpl;
import jenkins.scm.api.SCMHead;
import org.apache.commons.lang.StringUtils;

/**
 * Represents a specific revision of a bitbucket {@link SCMHead}.
 *
 * @author Nikolas Falco
 * @since 2.2.14
 */
public class BitbucketGitSCMRevision extends SCMRevisionImpl {
    private static final long serialVersionUID = 1L;

    private final String message;
    private final String author;
    private final Date date;

    /**
     * Construct a Bitbucket revision.
     *
     * @param head the {@link SCMHead} that represent this revision
     * @param commit head
     */
    public BitbucketGitSCMRevision(@NonNull SCMHead head, @NonNull BitbucketCommit commit) {
        super(head, commit.getHash());
        this.message = commit.getMessage();
        this.author = commit.getAuthor();
        Date commitDate;
        try {
            commitDate = new BitbucketDateFormat().parse(commit.getDate());
        } catch (ParseException e) {
            commitDate = null;
        }
        this.date = commitDate;
    }

    /**
     * Returns the author of this revision in GIT format.
     *
     * @return commit author in the following format &gt;name&lt; &gt;email&lt;
     */
    public String getAuthor() {
        return author;
    }

    /**
     * Returns the message associated with this revision.
     *
     * @return revision message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Return the revision date in ISO format.
     *
     * @return date for this revision
     */
    public Date getDate() {
        return (Date) date.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !(o instanceof SCMRevisionImpl)) {
            return false;
        }

        SCMRevisionImpl that = (SCMRevisionImpl) o;

        return StringUtils.equals(getHash(), that.getHash()) && getHead().equals(that.getHead());
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
