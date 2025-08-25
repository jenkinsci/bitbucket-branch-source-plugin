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
package com.cloudbees.jenkins.plugins.bitbucket.impl.buildstatus;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticatedClient;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus;
import com.cloudbees.jenkins.plugins.bitbucket.api.buildstatus.BitbucketBuildStatusNotifier;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.EndpointType;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.JsonParser;
import com.damnhandy.uri.template.UriTemplate;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.abbreviate;

@Extension
public class CloudBuildStatusNotifier implements BitbucketBuildStatusNotifier {
    private static final String COMMIT_BUILD_STATUS_URL = "/2.0/repositories{/owner,repo}/commit/{hash}/statuses/build";

    @Override
    public void sendBuildStatus(@NonNull BitbucketBuildStatus status, @NonNull BitbucketAuthenticatedClient client) throws IOException {
        BitbucketBuildStatus newStatus = new BitbucketBuildStatus(status);
        newStatus.setName(abbreviate(newStatus.getName(), 255));

        String url = UriTemplate.fromTemplate(COMMIT_BUILD_STATUS_URL)
                .set("owner", client.getRepositoryOwner())
                .set("repo", client.getRepositoryName())
                .set("hash", newStatus.getHash())
                .expand();
        client.post(url, JsonParser.toString(newStatus));
    }

    @Override
    public boolean isApplicable(@NonNull EndpointType type) {
        return type == EndpointType.CLOUD;
    }

}
