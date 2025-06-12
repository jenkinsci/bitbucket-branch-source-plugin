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
package com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint;

import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpointDescriptor;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.EndpointType;
import com.damnhandy.uri.template.UriTemplate;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;

public class DummyEndpointConfiguration extends AbstractBitbucketEndpoint {

    DummyEndpointConfiguration(boolean manageHooks, String credentialsId) {
        super(manageHooks, credentialsId, false, null);
    }

    @Override
    public String getDisplayName() {
        return "Dummy";
    }

    @NonNull
    @Override
    public String getServerUrl() {
        return getServerURL();
    }

    @NonNull
    @Override
    public String getServerURL() {
        return "http://dummy.example.com";
    }

    @NonNull
    @Override
    public String getEndpointJenkinsRootURL() {
        return "http://master.example.com";
    }

    @Override
    public EndpointType getType() {
        return EndpointType.SERVER;
    }

    @NonNull
    @Override
    public String getRepositoryUrl(@NonNull String repoOwner, @NonNull String repository) {
        return UriTemplate
                .fromTemplate("http://dummy.example.com{/owner,repo}")
                .set("owner", repoOwner)
                .set("repo", repository)
                .expand();
    }

    @Extension // TestExtension could be used only for embedded classes
    public static class DescriptorImpl extends BitbucketEndpointDescriptor {

    }

}