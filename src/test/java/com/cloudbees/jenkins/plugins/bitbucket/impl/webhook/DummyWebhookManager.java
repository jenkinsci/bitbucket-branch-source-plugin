/*
 * The MIT License
 *
 * Copyright (c) 2025, Falco Nikolas
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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookClient;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookManager;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import jenkins.scm.api.trait.SCMSourceTrait;

public class DummyWebhookManager implements BitbucketWebhookManager {

    private String repositoryOwner;
    private String repositoryName;
    private String serverURL;
    private String callbackURL;

    public String getRepositoryOwner() {
        return repositoryOwner;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public String getServerURL() {
        return serverURL;
    }

    public String getCallbackURL() {
        return callbackURL;
    }

    @Override
    public void setRepositoryOwner(String repositoryOwner) {
        this.repositoryOwner = repositoryOwner;
    }

    @Override
    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    @Override
    public void setServerURL(String serverURL) {
        this.serverURL = serverURL;
    }

    @Override
    public void setCallbackURL(String callbackURL) {
        this.callbackURL = callbackURL;
    }

    @Override
    public void apply(BitbucketWebhookConfiguration configuration) {
    }

    @Override
    public Collection<Class<? extends SCMSourceTrait>> supportedTraits() {
        return Collections.emptyList();
    }

    @Override
    public void apply(SCMSourceTrait trait) {
    }

    @Override
    public Collection<BitbucketWebHook> read(BitbucketWebhookClient client) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public void register(BitbucketWebhookClient client) throws IOException {
    }

    @Override
    public void remove(String webhookId, BitbucketWebhookClient client) throws IOException {
    }

}
