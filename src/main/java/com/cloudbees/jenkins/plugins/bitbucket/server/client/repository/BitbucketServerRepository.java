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

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryOwner;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketServerRepository implements BitbucketRepository {

    @JsonProperty("scmId")
    private String scm;

    private Project project;

    @JsonProperty("slug")
    private String repositoryName;

    // JSON mapping added in setter because the field can not be called "public"
    private Boolean publc;

    @Override
    public String getScm() {
        return scm;
    }

    @Override
    public String getFullName() {
        return project.getKey() + "/" + repositoryName;
    }

    @Override
    public BitbucketRepositoryOwner getOwner() {
        return new BitbucketServerRepositoryOwner(project.getKey(), project.getName());
    }

    @Override
    public String getOwnerName() {
        return project.getKey();
    }

    @Override
    public String getRepositoryName() {
        return repositoryName;
    }

    public void setProject(Project p) {
        this.project = p;
    }

    @Override
    public boolean isPrivate() {
        return !publc;
    }

    @Override
    public String getProjectName() {
        return project.getName();
    }

    @JsonProperty("public")
    public void setPublic(Boolean publc) {
        this.publc = publc;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Project {

        @JsonProperty
        private String key;

        @JsonProperty
        private String name;

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
    }

}
