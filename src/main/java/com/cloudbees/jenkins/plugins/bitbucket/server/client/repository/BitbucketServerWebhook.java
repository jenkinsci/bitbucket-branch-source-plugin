package com.cloudbees.jenkins.plugins.bitbucket.server.client.repository;


import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import java.util.Collections;
import java.util.List;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;

public class BitbucketServerWebhook implements BitbucketWebHook {
    @JsonProperty("id")
    private Integer uid;
    @JsonProperty("title")
    private String description;
    @JsonProperty("url")
    private String url;
    @JsonProperty("enabled")
    private boolean active;

    @Override
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    @JsonIgnore
    public List<String> getEvents() {
        return Collections.emptyList();
    }

    @Override
    @JsonIgnore
    public String getUuid() {
        if (uid != null) {
            return String.valueOf(uid);
        }
        return null;
    }
}
