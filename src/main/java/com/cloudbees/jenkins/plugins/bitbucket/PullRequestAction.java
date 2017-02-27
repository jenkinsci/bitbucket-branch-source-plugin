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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.MalformedURLException;
import java.net.URL;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Retained to help migrate legacy SCMHead instances
 */
@Deprecated
@Restricted(NoExternalUse.class)
class PullRequestAction {
    private final String number;
    private URL url;
    private final String title;
    private final String userLogin;

    public PullRequestAction(BitbucketPullRequest pr) {
        number = pr.getId();
        try {
            url = new URL(pr.getLink());
        } catch (MalformedURLException e) {
            url = null;
        }
        title = pr.getTitle();
        userLogin = pr.getAuthorLogin();
    }

    @NonNull
    public String getId() {
        return number;
    }

    public URL getURL() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return userLogin;
    }

    private static final long serialVersionUID = 1L;

}
