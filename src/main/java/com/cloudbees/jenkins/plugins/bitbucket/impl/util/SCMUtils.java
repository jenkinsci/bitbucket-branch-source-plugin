/*
 * The MIT License
 *
 * Copyright (c) 2025, Nikolas Falco
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
package com.cloudbees.jenkins.plugins.bitbucket.impl.util;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import jenkins.plugins.git.AbstractGitSCMSource.SCMRevisionImpl;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.ChangeRequestSCMRevision;

/**
 * Utility class that helps to extract specific information from implementation
 * of SCM base classes.
 *
 * @author Nikolas Falco
 * @since 936.1.0
 */
public final class SCMUtils {

    private SCMUtils() {
    }

    /**
     * Returns the SHA1 hash for the given SCMRevision implementation.
     * <p>
     * In case of ChangeRequestSCMRevision returns the SHA1 hash of the target.
     *
     * @param revision extract from
     * @return a SHA1 commit hash in the revision if present
     */
    @CheckForNull
    public static String getHash(@Nullable SCMRevision revision) {
        if (revision == null) {
            return null;
        } else if (revision instanceof SCMRevisionImpl gitRev) {
            return gitRev.getHash();
        } else if (revision instanceof ChangeRequestSCMRevision<?> prRev) {
            return getHash(prRev.getTarget());
        } else {
            throw new UnsupportedOperationException("Revision of type " + revision.getClass().getSimpleName() + " is not supported.\n"
                    + "Please fill an issue at https://issues.jenkins.io to the bitbucket-branch-source-plugin component.");
        }
    }

}
