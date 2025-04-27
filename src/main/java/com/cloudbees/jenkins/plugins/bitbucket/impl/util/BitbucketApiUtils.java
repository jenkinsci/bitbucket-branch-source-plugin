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
package com.cloudbees.jenkins.plugins.bitbucket.impl.util;

import com.cloudbees.jenkins.plugins.bitbucket.Messages;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRequestException;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.Item;
import hudson.util.FormFillFailure;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSourceOwner;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.HttpHost;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public class BitbucketApiUtils {

    public interface BitbucketSupplier<T> {
        T get(BitbucketApi bitbucketApi) throws IOException, InterruptedException;
    }

    private static final Logger logger = Logger.getLogger(BitbucketApiUtils.class.getName());

    public static boolean isCloud(BitbucketApi client) {
        return client instanceof BitbucketCloudApiClient;
    }

    public static boolean isCloud(@NonNull String serverURL) {
        return StringUtils.startsWithAny(serverURL, BitbucketCloudEndpoint.SERVER_URL, BitbucketCloudEndpoint.BAD_SERVER_URL);
    }

    public static ListBoxModel getFromBitbucket(SCMSourceOwner context,
                                                String serverURL,
                                                String credentialsId,
                                                String repoOwner,
                                                String repository,
                                                BitbucketSupplier<ListBoxModel> listBoxModelSupplier) throws FormFillFailure {
        repoOwner = Util.fixEmptyAndTrim(repoOwner);
        if (repoOwner == null) {
            return new ListBoxModel();
        }
        if (context == null && !Jenkins.get().hasPermission(Jenkins.MANAGE) ||
            context != null && !context.hasPermission(Item.EXTENDED_READ)) {
            return new ListBoxModel(); // not supposed to be seeing this form
        }
        if (context != null && !context.hasPermission(CredentialsProvider.USE_ITEM)) {
            return new ListBoxModel(); // not permitted to try connecting with these credentials
        }

        serverURL = BitbucketEndpointConfiguration.get()
                .findEndpoint(serverURL)
                .orElse(BitbucketEndpointConfiguration.get().getDefaultEndpoint())
                .getServerUrl();
        StandardCredentials credentials = BitbucketCredentials.lookupCredentials(
            serverURL,
            context,
            credentialsId,
            StandardCredentials.class
        );

        BitbucketAuthenticator authenticator = AuthenticationTokens.convert(BitbucketAuthenticator.authenticationContext(serverURL), credentials);

        try (BitbucketApi bitbucket = BitbucketApiFactory.newInstance(serverURL, authenticator, repoOwner, null, repository)) {
            return listBoxModelSupplier.get(bitbucket);
        } catch (FormFillFailure e) {
            throw e;
        } catch (InterruptedException | IOException e) { // NOSONAR
            BitbucketRequestException bbe = BitbucketApiUtils.unwrap(e);
            if (bbe != null && bbe.getHttpCode() == 401) {
                throw FormFillFailure.error(credentials == null
                    ? Messages.BitbucketSCMSource_UnauthorizedAnonymous(repoOwner)
                    : Messages.BitbucketSCMSource_UnauthorizedOwner(repoOwner)).withSelectionCleared();
            }
            logger.log(Level.SEVERE, e.getMessage(), e);
            throw FormFillFailure.error(e.getMessage());
        }
    }

    public static BitbucketRequestException unwrap(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (e instanceof BitbucketRequestException bbException) {
                return bbException;
            } else {
                cause = e.getCause();
            }
        }
        return null;
    }

    public static HttpHost toHttpHost(String url) {
        try {
            // it's needed because the serverURL can contains a context root different than '/' and the HttpHost must contains only schema, host and port
            URL tmp = new URL(url);
            String schema = tmp.getProtocol() == null ? "http" : tmp.getProtocol();
            return new HttpHost(schema, tmp.getHost(), tmp.getPort());
        } catch (MalformedURLException e) {
        }
        try {
            return HttpHost.create(url);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid URL " + url, e);
        }
    }

}
