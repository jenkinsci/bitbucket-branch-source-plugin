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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.ExtensionList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Extension
public class BitbucketMockApiFactory extends BitbucketApiFactory {
    private static final String NULL = "\u0000\u0000\u0000\u0000";
    private final Map<String, BitbucketApi> mocks = new HashMap<>();

    public static void clear() {
        instance().mocks.clear();
    }

    public static void add(String serverURL, BitbucketApi client) {
        instance().mocks.put(Objects.toString(serverURL, NULL), client);
    }

    public static void remove(String serverURL) {
        instance().mocks.remove(Objects.toString(serverURL, NULL));
    }

    private static BitbucketMockApiFactory instance() {
        return ExtensionList.lookup(BitbucketApiFactory.class).get(BitbucketMockApiFactory.class);
    }


    @Override
    protected boolean isMatch(@Nullable String serverURL) {
        return mocks.containsKey(Objects.toString(serverURL, NULL));
    }

    @NonNull
    @Override
    protected BitbucketApi create(@Nullable String serverURL, @Nullable BitbucketAuthenticator authenticator,
                                  @NonNull String owner, @CheckForNull String projectKey, @CheckForNull String repository) {
        return mocks.get(Objects.toString(serverURL, NULL));
    }
}
