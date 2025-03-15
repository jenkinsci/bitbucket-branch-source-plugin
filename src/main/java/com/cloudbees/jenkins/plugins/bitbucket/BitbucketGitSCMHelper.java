/*
 * The MIT License
 *
 * Copyright (c) 2016-2017, CloudBees, Inc., Nikolas Falco
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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryProtocol;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketCredentials;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import java.util.List;
import org.apache.commons.lang.StringUtils;

public class BitbucketGitSCMHelper {
    protected static String getCloneLink(@NonNull BitbucketSCMSource scmSource,
                                         @CheckForNull List<BitbucketHref> primaryCloneLinks,
                                         @CheckForNull List<BitbucketHref> mirrorCloneLinks) {
        if (primaryCloneLinks == null) {
            throw new IllegalArgumentException("Primary clone links shouldn't be null");
        }
        mirrorCloneLinks = Util.fixNull(mirrorCloneLinks);
        if (mirrorCloneLinks.isEmpty()) {
            return getCloneLink(scmSource, mirrorCloneLinks);
        }
        return getCloneLink(scmSource, primaryCloneLinks);
    }

    private static String getCloneLink(BitbucketSCMSource scmSource, List<BitbucketHref> cloneLinks) {
        BitbucketRepositoryProtocol protocol = getProtocol(scmSource);
        return cloneLinks.stream()
                .filter(link -> protocol.matches(link.getName()))
                .findAny()
                .orElseThrow(
                        () -> new IllegalStateException("Can't find clone link for protocol " + protocol))
                .getHref();
    }

    private static BitbucketRepositoryProtocol getProtocol(BitbucketSCMSource scmSource) {
        String credentialsId = scmSource.getCredentialsId();
        if (StringUtils.isNotBlank(credentialsId)) {
            StandardCredentials credentials = BitbucketCredentials.lookupCredentials(
                    scmSource.getServerUrl(),
                    scmSource.getOwner(),
                    BitbucketSCMSource.DescriptorImpl.SAME.equals(
                            scmSource.getCheckoutCredentialsId()) ? credentialsId :
                            scmSource.getCheckoutCredentialsId(),
                    StandardCredentials.class
            );
            if (credentials instanceof SSHUserPrivateKey) {
                return BitbucketRepositoryProtocol.SSH;
            }
        }
        return BitbucketRepositoryProtocol.HTTP;
    }
}
