package com.cloudbees.jenkins.plugins.bitbucket.client.repository;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

/**
 * Created by saiimons on 08/08/2016.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketCloudProject {
    private String key;
    private String name;

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setName(String name) {
        this.name = name;
    }
}
