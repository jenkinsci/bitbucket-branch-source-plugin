/*
 * The MIT License
 *
 * Copyright (c) 2016-2017, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket.hooks;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Bitbucket hooks types managed by this plugin.
 */
public enum HookEventType {

    /**
     * See <a href="https://confluence.atlassian.com/bitbucket/event-payloads-740262817.html#EventPayloads-Push">EventPayloads-Push</a>
     */
    PUSH("repo:push"),

    /**
     * See <a href="https://confluence.atlassian.com/bitbucket/event-payloads-740262817.html#EventPayloads-Created.1">EventPayloads-Created</a>
     */
    PULL_REQUEST_CREATED("pullrequest:created"),

    /**
     * See <a href="https://confluence.atlassian.com/bitbucket/event-payloads-740262817.html#EventPayloads-Updated.1">EventPayloads-Updated</a>
     */
    PULL_REQUEST_UPDATED("pullrequest:updated"),

    /**
     * See <a href="https://confluence.atlassian.com/bitbucket/event-payloads-740262817.html#EventPayloads-Merged">EventPayloads-Merged</a>
     */
    PULL_REQUEST_MERGED("pullrequest:fulfilled"),

    /**
     * See <a href="https://confluence.atlassian.com/bitbucket/event-payloads-740262817.html#EventPayloads-Declined">EventPayloads-Declined</a>
     */
    PULL_REQUEST_DECLINED("pullrequest:rejected"),

    /**
     * @see <a href="https://confluence.atlassian.com/bitbucketserver054/event-payload-939508609.html#Eventpayload-Push">Eventpayload-Push</a>
     * @since Bitbucket Server 5.4
     */
    SERVER_REFS_CHANGED("repo:refs_changed"),

    /**
     * @see <a href="https://confluence.atlassian.com/bitbucketserver/event-payload-938025882.html#Eventpayload-repo-mirr-syn">Eventpayload-repo-mirr-syn</a>
     * @since Bitbucket Server 6.5
     */
    SERVER_MIRROR_REPO_SYNCHRONIZED("mirror:repo_synchronized"),

    /**
     * @see <a href="https://confluence.atlassian.com/bitbucketserver054/event-payload-939508609.html#Eventpayload-Opened">Eventpayload-Opened</a>
     * @since Bitbucket Server 5.4
     */
    SERVER_PULL_REQUEST_OPENED("pr:opened"),

    /**
     * @see <a href="https://confluence.atlassian.com/bitbucketserver054/event-payload-939508609.html#Eventpayload-Merged">Eventpayload-Merged</a>
     * @since Bitbucket Server 5.4
     */
    SERVER_PULL_REQUEST_MERGED("pr:merged"),

    /**
     * See <a href="https://confluence.atlassian.com/bitbucketserver054/event-payload-939508609.html#Eventpayload-Declined">Eventpayload-Declined</a>
     * @since Bitbucket Server 5.4
     */
    SERVER_PULL_REQUEST_DECLINED("pr:declined"),

    /**
     * See <a href="https://confluence.atlassian.com/bitbucketserver054/event-payload-939508609.html#Eventpayload-Deleted">Eventpayload-Deleted</a>
     *
     * @since Bitbucket Server 5.4
     */
    SERVER_PULL_REQUEST_DELETED("pr:deleted"),

    /**
     * @see <a href="https://confluence.atlassian.com/bitbucketserver0510/event-payload-951390742.html#Eventpayload-Modified.1">Eventpayload: Pull Request - Modified</a>
     * @since Bitbucket Server 5.10
     */
    SERVER_PULL_REQUEST_MODIFIED("pr:modified"),

    /**
     * @see <a href="https://confluence.atlassian.com/bitbucketserver070/event-payload-996644369.html#Eventpayload-Sourcebranchupdated">Eventpayload-Sourcebranchupdated</a>
     * @since Bitbucket Server 7.0
     */
    SERVER_PULL_REQUEST_FROM_REF_UPDATED("pr:from_ref_updated"),

    /**
     * Sent when hitting the {@literal "Test connection"} button in Bitbucket Server. Apparently undocumented.
     */
    SERVER_PING("diagnostics:ping");


    private final String key;

    HookEventType(@NonNull String key) {
        this.key = key;
    }

    @NonNull
    public static HookEventType fromString(String key) {
        for (HookEventType value : HookEventType.values()) {
            if (value.getKey().equals(key)) {
                return value;
            }
        }
        throw new IllegalArgumentException("No enum constant " + HookEventType.class.getCanonicalName() + " have key " + key);
    }

    public String getKey() {
        return key;
    }

}
