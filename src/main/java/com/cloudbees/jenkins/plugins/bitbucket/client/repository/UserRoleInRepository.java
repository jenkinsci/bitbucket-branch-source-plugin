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
package com.cloudbees.jenkins.plugins.bitbucket.client.repository;

/**
 * User role in repository:
 * <ul>
 *   <li>owner: returns all repositories owned by the current user.</li>
 *   <li>admin: returns repositories to which the user has explicit administrator access.</li>
 *   <li>contributor: returns repositories to which the user has explicit write access.<li>
 *   <li>member: returns repositories to which the user has explicit read access.</li>
 * </ul>
 *
 * See <a href="https://confluence.atlassian.com/bitbucket/repositories-endpoint-423626330.html#repositoriesEndpoint-GETalistofrepositoriesforanaccount">API docs</a> for more information.
 */
public enum UserRoleInRepository {

    OWNER("owner"),
    ADMIN("admin"),
    CONTRIBUTOR("contributor"),
    MEMBER("member");

    private final String id;

    private UserRoleInRepository(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

}
