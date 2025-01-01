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
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSourceOwner;
import org.apache.commons.lang.StringUtils;
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
        return StringUtils.startsWithAny(serverURL, new String[] { BitbucketCloudEndpoint.SERVER_URL, BitbucketCloudEndpoint.BAD_SERVER_URL });
    }

    public static ListBoxModel getFromBitbucket(SCMSourceOwner context,
                                                String serverURL,
                                                String credentialsId,
                                                String repoOwner,
                                                String repository,
                                                BitbucketSupplier<ListBoxModel> listBoxModelSupplier)
        throws FormFillFailure {
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

        try {
            BitbucketApi bitbucket = BitbucketApiFactory.newInstance(serverURL, authenticator, repoOwner, null, repository);
            return listBoxModelSupplier.get(bitbucket);
        } catch (FormFillFailure | OutOfMemoryError e) {
            throw e;
        } catch (IOException e) {
            if (e instanceof BitbucketRequestException bbe) {
                if (bbe.getHttpCode() == 401) {
                    throw FormFillFailure.error(credentials == null
                        ? Messages.BitbucketSCMSource_UnauthorizedAnonymous(repoOwner)
                        : Messages.BitbucketSCMSource_UnauthorizedOwner(repoOwner)).withSelectionCleared();
                }
            } else if (e.getCause() instanceof BitbucketRequestException cause && cause.getHttpCode() == 401) {
                throw FormFillFailure.error(credentials == null
                    ? Messages.BitbucketSCMSource_UnauthorizedAnonymous(repoOwner)
                    : Messages.BitbucketSCMSource_UnauthorizedOwner(repoOwner)).withSelectionCleared();
            }
            logger.log(Level.SEVERE, e.getMessage(), e);
            throw FormFillFailure.error(e.getMessage());
        } catch (Throwable e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            throw FormFillFailure.error(e.getMessage());
        }
    }

}
