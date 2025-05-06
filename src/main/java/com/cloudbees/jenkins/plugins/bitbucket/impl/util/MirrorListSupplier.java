/*
 * The MIT License
 *
 * Copyright (c) 2024, Andrey Fomin
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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketMirrorServer;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerAPIClient;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.List;

public class MirrorListSupplier implements BitbucketApiUtils.BitbucketSupplier<ListBoxModel> {

    public static final MirrorListSupplier INSTANCE = new MirrorListSupplier();

    @Override
    public ListBoxModel get(BitbucketApi client) throws IOException, InterruptedException {
        ListBoxModel result = new ListBoxModel(new ListBoxModel.Option("Primary server", ""));
        if (!BitbucketApiUtils.isCloud(client)) {
            BitbucketServerAPIClient bitbucketServerAPIClient = (BitbucketServerAPIClient) client;
            List<BitbucketMirrorServer> mirrors = bitbucketServerAPIClient.getMirrors();
            for (BitbucketMirrorServer mirror : mirrors) {
                result.add(new ListBoxModel.Option(mirror.getName(), mirror.getId()));
            }
        }
        return result;

    }
}
