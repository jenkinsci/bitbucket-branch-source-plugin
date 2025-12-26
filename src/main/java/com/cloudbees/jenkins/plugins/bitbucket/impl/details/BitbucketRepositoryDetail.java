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
package com.cloudbees.jenkins.plugins.bitbucket.impl.details;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpointProvider;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Actionable;
import hudson.model.Run;
import jenkins.model.details.Detail;
import jenkins.model.details.DetailGroup;
import jenkins.scm.api.SCMDetailGroup;
import jenkins.scm.api.SCMSource;

public class BitbucketRepositoryDetail extends Detail {
    public BitbucketRepositoryDetail(Actionable object) {
        super(object);
    }

    @Nullable
    @Override
    public String getIconClassName() {
        return "symbol-logo-github plugin-ionicons-api";
    }

    @Nullable
    @Override
    public String getDisplayName() {
        BitbucketSCMSource source = getSCMSource();

        if (source == null) {
            return null;
        }

        return source.getRepoOwner() + "/" + source.getRepository();
    }

    @Override
    public String getLink() {
        BitbucketSCMSource source = getSCMSource();

        if (source == null) {
            return null;
        }

        return BitbucketEndpointProvider.lookupEndpoint(source.getServerUrl()).map(endpoint -> endpoint.getRepositoryURL(source.getRepoOwner(), source.getRepository())).orElse(null);
    }

    @Override
    public DetailGroup getGroup() {
        return SCMDetailGroup.get();
    }

    private BitbucketSCMSource getSCMSource() {
        SCMSource source = SCMSource.SourceByItem.findSource(((Run<?, ?>) getObject()).getParent());

        if (source instanceof BitbucketSCMSource bitbucketSource) {
            return bitbucketSource;
        }

        return null;
    }
}