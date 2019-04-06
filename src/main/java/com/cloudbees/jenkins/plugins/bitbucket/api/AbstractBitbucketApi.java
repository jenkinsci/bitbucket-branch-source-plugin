package com.cloudbees.jenkins.plugins.bitbucket.api;

import java.net.MalformedURLException;
import java.net.URL;

public abstract class AbstractBitbucketApi implements BitbucketApi {

    @Override
    public URL checkURL(String aURL) {
        try {
            URL url = new URL(aURL);
            if ("localhost".equals(url.getHost())) {
                throw new IllegalStateException("Jenkins URL cannot start with http://localhost");
            }
            if ("unconfigured-jenkins-location".equals(url.getHost())) {
                throw new IllegalStateException("Could not determine Jenkins URL.");
            }
            return url;
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Bad Jenkins URL");
        }
    }
}
