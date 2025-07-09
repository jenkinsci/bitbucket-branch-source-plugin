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
package com.cloudbees.jenkins.plugins.bitbucket.server.client.repository;


import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.HookEventType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

public class BitbucketPluginWebhook implements BitbucketWebHook {
    @JsonProperty("id")
    private Integer uid;
    @JsonProperty("title")
    private String description;
    @JsonProperty("url")
    private String url;
    @JsonProperty("enabled")
    private boolean active;
    private boolean tagCreated;
    private boolean branchDeleted;
    private boolean branchCreated;
    private boolean repoPush;
    private boolean prDeclined;
    private boolean prRescoped;
    private boolean prMerged;
    private boolean prReopened;
    private boolean prUpdated;
    private boolean prCreated;
    private boolean prCommented;
    private boolean prDeleted;
    private boolean repoMirrorSynced;

    @JsonInclude(JsonInclude.Include.NON_NULL) // If null, don't marshal to allow for backwards compatibility
    private String committersToIgnore; // Since Bitbucket Webhooks version 1.5.0

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

    public String getCommittersToIgnore() {
        return committersToIgnore;
    }

    public void setCommittersToIgnore(String committersToIgnore) {
        this.committersToIgnore = committersToIgnore;
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
        List<String> events = new ArrayList<>();
        if (prCommented) {
            events.add("prCommented");
        }
        if (prCreated) {
            events.add(HookEventType.SERVER_PULL_REQUEST_OPENED.getKey());
        }
        if (prDeclined) {
            events.add(HookEventType.SERVER_PULL_REQUEST_DECLINED.getKey());
        }
        if (prDeleted) {
            events.add(HookEventType.SERVER_PULL_REQUEST_DELETED.getKey());
        }
        if (prMerged) {
            events.add(HookEventType.SERVER_PULL_REQUEST_MERGED.getKey());
        }
        if (prReopened) {
            events.add(HookEventType.SERVER_PULL_REQUEST_OPENED.getKey());
        }
        if (prRescoped) {
            events.add("prRescoped");
        }
        if (prUpdated) {
            events.add(HookEventType.SERVER_PULL_REQUEST_FROM_REF_UPDATED.getKey());
        }
        if (repoMirrorSynced) {
            events.add(HookEventType.SERVER_MIRROR_REPO_SYNCHRONIZED.getKey()); // not supported by the hookprocessor
        }
        if (repoPush) {
            events.add(HookEventType.SERVER_REFS_CHANGED.getKey());
        }
        if (tagCreated) {
            events.add(HookEventType.SERVER_REFS_CHANGED.getKey());
        }
        if (branchCreated) {
            events.add(HookEventType.SERVER_REFS_CHANGED.getKey());
        }
        if (branchDeleted) {
            events.add(HookEventType.SERVER_REFS_CHANGED.getKey());
        }
        return events;
    }

    @JsonIgnore
    public void setEvents(List<String> events) {
        prCommented = events.contains("prCommented");
        prCreated = events.contains(HookEventType.SERVER_PULL_REQUEST_OPENED.getKey());
        prDeclined = events.contains(HookEventType.SERVER_PULL_REQUEST_DECLINED.getKey());
        prDeleted = events.contains(HookEventType.SERVER_PULL_REQUEST_DELETED.getKey());
        prMerged = events.contains(HookEventType.SERVER_PULL_REQUEST_MERGED.getKey());
        prReopened = prCreated;
        prRescoped = events.contains("prRescoped");
        prUpdated = events.contains(HookEventType.SERVER_PULL_REQUEST_FROM_REF_UPDATED.getKey());
        repoMirrorSynced = events.contains(HookEventType.SERVER_MIRROR_REPO_SYNCHRONIZED.getKey()); // TODO not supported in hookprocessor
        repoPush = events.contains(HookEventType.SERVER_REFS_CHANGED.getKey());
        tagCreated = repoPush;
        branchCreated = repoPush;
        branchDeleted = repoPush;
    }

    @Override
    @JsonIgnore
    public String getUuid() {
        if (uid != null) {
            return String.valueOf(uid);
        }
        return null;
    }

    @Override
    public String getSecret() {
        return null;
    }

    public boolean isTagCreated() {
        return tagCreated;
    }

    public void setTagCreated(boolean tagCreated) {
        this.tagCreated = tagCreated;
    }

    public boolean isBranchDeleted() {
        return branchDeleted;
    }

    public void setBranchDeleted(boolean branchDeleted) {
        this.branchDeleted = branchDeleted;
    }

    public boolean isBranchCreated() {
        return branchCreated;
    }

    public void setBranchCreated(boolean branchCreated) {
        this.branchCreated = branchCreated;
    }

    public boolean isRepoPush() {
        return repoPush;
    }

    public void setRepoPush(boolean repoPush) {
        this.repoPush = repoPush;
    }

    public boolean isPrDeclined() {
        return prDeclined;
    }

    public void setPrDeclined(boolean prDeclined) {
        this.prDeclined = prDeclined;
    }

    public boolean isPrRescoped() {
        return prRescoped;
    }

    public void setPrRescoped(boolean prRescoped) {
        this.prRescoped = prRescoped;
    }

    public boolean isPrMerged() {
        return prMerged;
    }

    public void setPrMerged(boolean prMerged) {
        this.prMerged = prMerged;
    }

    public boolean isPrReopened() {
        return prReopened;
    }

    public void setPrReopened(boolean prReopened) {
        this.prReopened = prReopened;
    }

    public boolean isPrUpdated() {
        return prUpdated;
    }

    public void setPrUpdated(boolean prUpdated) {
        this.prUpdated = prUpdated;
    }

    public boolean isPrCreated() {
        return prCreated;
    }

    public void setPrCreated(boolean prCreated) {
        this.prCreated = prCreated;
    }

    public boolean isPrCommented() {
        return prCommented;
    }

    public void setPrCommented(boolean prCommented) {
        this.prCommented = prCommented;
    }

    public boolean isPrDeleted() {
        return prDeleted;
    }

    public void setPrDeleted(boolean prDeleted) {
        this.prDeleted = prDeleted;
    }

    public boolean isRepoMirrorSynced() {
        return repoMirrorSynced;
    }

    public void setRepoMirrorSynced(boolean repoMirrorSynced) {
        this.repoMirrorSynced = repoMirrorSynced;
    }

}
