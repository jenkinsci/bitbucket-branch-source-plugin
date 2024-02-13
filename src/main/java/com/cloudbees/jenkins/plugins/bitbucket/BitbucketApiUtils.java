package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRequestException;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
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

public class BitbucketApiUtils {

    private static final Logger LOGGER = Logger.getLogger(BitbucketApiUtils.class.getName());

    public static ListBoxModel getFromBitbucket(SCMSourceOwner context,
                                                String serverUrl,
                                                String credentialsId,
                                                String repoOwner,
                                                String repository,
                                                BitbucketSupplier<ListBoxModel> listBoxModelSupplier)
        throws FormFillFailure {
        repoOwner = Util.fixEmptyAndTrim(repoOwner);
        if (repoOwner == null) {
            return new ListBoxModel();
        }
        if (context == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER) ||
            context != null && !context.hasPermission(Item.EXTENDED_READ)) {
            return new ListBoxModel(); // not supposed to be seeing this form
        }
        if (context != null && !context.hasPermission(CredentialsProvider.USE_ITEM)) {
            return new ListBoxModel(); // not permitted to try connecting with these credentials
        }

        String serverUrlFallback = BitbucketCloudEndpoint.SERVER_URL;
        // if at least one bitbucket server is configured use it instead of bitbucket cloud
        if(BitbucketEndpointConfiguration.get().getEndpointItems().size() > 0){
            serverUrlFallback =  BitbucketEndpointConfiguration.get().getEndpointItems().get(0).value;
        }

        serverUrl = StringUtils.defaultIfBlank(serverUrl, serverUrlFallback);
        StandardCredentials credentials = BitbucketCredentials.lookupCredentials(
            serverUrl,
            context,
            credentialsId,
            StandardCredentials.class
        );

        BitbucketAuthenticator authenticator = AuthenticationTokens.convert(BitbucketAuthenticator.authenticationContext(serverUrl), credentials);

        try {
            BitbucketApi bitbucket = BitbucketApiFactory.newInstance(serverUrl, authenticator, repoOwner, null, repository);
            return listBoxModelSupplier.get(bitbucket);
        } catch (FormFillFailure | OutOfMemoryError e) {
            throw e;
        } catch (IOException e) {
            if (e instanceof BitbucketRequestException) {
                if (((BitbucketRequestException) e).getHttpCode() == 401) {
                    throw FormFillFailure.error(credentials == null
                        ? Messages.BitbucketSCMSource_UnauthorizedAnonymous(repoOwner)
                        : Messages.BitbucketSCMSource_UnauthorizedOwner(repoOwner)).withSelectionCleared();
                }
            } else if (e.getCause() instanceof BitbucketRequestException) {
                if (((BitbucketRequestException) e.getCause()).getHttpCode() == 401) {
                    throw FormFillFailure.error(credentials == null
                        ? Messages.BitbucketSCMSource_UnauthorizedAnonymous(repoOwner)
                        : Messages.BitbucketSCMSource_UnauthorizedOwner(repoOwner)).withSelectionCleared();
                }
            }
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw FormFillFailure.error(e.getMessage());
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw FormFillFailure.error(e.getMessage());
        }
    }

    public interface BitbucketSupplier<T> {
        T get(BitbucketApi bitbucketApi) throws IOException, InterruptedException;
    }

}
