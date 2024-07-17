package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketMirrorServer;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerAPIClient;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.List;

public class MirrorListSupplier implements BitbucketApiUtils.BitbucketSupplier<ListBoxModel> {

    public static final MirrorListSupplier INSTANCE = new MirrorListSupplier();

    @Override
    public ListBoxModel get(BitbucketApi bitbucketApi) throws IOException, InterruptedException {
        ListBoxModel result = new ListBoxModel(new ListBoxModel.Option("Primary server", ""));
        if (bitbucketApi instanceof BitbucketServerAPIClient) {
            BitbucketServerAPIClient bitbucketServerAPIClient = (BitbucketServerAPIClient) bitbucketApi;
            List<BitbucketMirrorServer> mirrors = bitbucketServerAPIClient.getMirrors();
            for (BitbucketMirrorServer mirror : mirrors) {
                result.add(new ListBoxModel.Option(mirror.getName(), mirror.getId()));
            }
        }
        return result;

    }
}
