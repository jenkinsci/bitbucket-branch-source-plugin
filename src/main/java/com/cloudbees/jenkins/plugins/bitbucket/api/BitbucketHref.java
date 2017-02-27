package com.cloudbees.jenkins.plugins.bitbucket.api;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

/**
 * A Href for something on bitbucket.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketHref {
    private String href;


    // Used for marshalling/unmarshalling
    @Restricted(DoNotUse.class)
    public BitbucketHref() {
    }

    public BitbucketHref(String href) {
        this.href = href;
    }

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }
}
