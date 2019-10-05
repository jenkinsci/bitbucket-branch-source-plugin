package com.cloudbees.jenkins.plugins.bitbucket.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@JsonIgnoreProperties(ignoreUnknown = true)
@Restricted(NoExternalUse.class)
public class BitbucketProject {

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

    @Override
    public String toString() {
        return "{ key=" + key + ", name=" + name + "}";
    }
}
