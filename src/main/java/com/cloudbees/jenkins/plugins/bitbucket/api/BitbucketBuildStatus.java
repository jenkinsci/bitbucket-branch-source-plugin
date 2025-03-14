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
package com.cloudbees.jenkins.plugins.bitbucket.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

public class BitbucketBuildStatus {

    /**
     * Enumeration of possible Bitbucket commit notification states
     */
    public enum Status {
        INPROGRESS("INPROGRESS"),
        FAILED("FAILED"),
        // available only in Cloud
        STOPPED("STOPPED"),
        // available only in Data Center
        CANCELLED("CANCELLED"),
        SUCCESSFUL("SUCCESSFUL");

        private final String status;

        Status(final String status) {
            this.status = status;
        }

        @Override
        public String toString() {
            return status;
        }
    }

    /**
     * The commit hash to set the status on
     */
    @JsonIgnore
    private String hash;

    /**
     * Text shown in the UI
     */
    private String description;

    /**
     * One of: INPROGRESS|FAILED|STOPPED|SUCCESSFUL
     */
    private Status state;

    /**
     * The URL to link from the status details
     */
    private String url;

    /**
     * Usually the job name in Jenkins
     */
    private String key;

    /**
     * The parent key
     */
    private String parent;

    /**
     * A short name, usually #job-name, #build-number (will be shown as link
     * text in BB UI)
     */
    private String name;

    /**
     * The fully qualified git reference e.g. refs/heads/master.
     */
    private String refname;

    /**
     * Duration of a completed build in milliseconds.
     */
    private long buildDuration;

    /**
     * A unique identifier of this particular run.
     */
    private int buildNumber;

    // Used for marshalling/unmarshalling
    @Restricted(DoNotUse.class)
    public BitbucketBuildStatus() {}

    public BitbucketBuildStatus(String hash,
                                String description,
                                @NonNull Status state,
                                String url,
                                @NonNull String key,
                                String name,
                                @Nullable String refname) {
        this.hash = hash;
        this.description = description;
        this.state = state;
        this.url = url;
        this.key = key;
        this.name = name;
        this.refname = refname;
    }

    /**
     * Copy constructor.
     *
     * @param other from copy to.
     */
    public BitbucketBuildStatus(@NonNull BitbucketBuildStatus other) {
        this.hash = other.hash;
        this.description = other.description;
        this.state = other.state;
        this.url = other.url;
        this.key = other.key;
        this.name = other.name;
        this.refname = other.refname;
        this.buildDuration = other.buildDuration;
        this.parent = other.parent;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getState() {
        return state.toString();
    }

    public void setState(Status state) {
        this.state = state;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRefname() {
        return refname;
    }

    public void setRefname(String refname) {
        this.refname = refname;
    }

    public long getBuildDuration() {
        return buildDuration;
    }

    public void setBuildDuration(long buildDuration) {
        this.buildDuration = buildDuration;
    }

    public int getBuildNumber() {
        return buildNumber;
    }

    public void setBuildNumber(int buildNumber) {
        this.buildNumber = buildNumber;
    }

    public void setParent(String parent) {
        this.parent = parent;
    }

    public String getParent() {
        return parent;
    }
}
