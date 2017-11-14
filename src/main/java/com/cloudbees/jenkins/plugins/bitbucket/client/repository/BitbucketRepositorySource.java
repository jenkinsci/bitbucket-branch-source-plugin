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

package com.cloudbees.jenkins.plugins.bitbucket.client.repository;

import com.cloudbees.jenkins.plugins.bitbucket.filesystem.BitbucketSCMFile;
import java.util.List;
import jenkins.scm.api.SCMFile;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;

public class BitbucketRepositorySource {
    private final String path;
    private final String type;
    private final List<String> attributes;

    @JsonCreator
    public BitbucketRepositorySource(@JsonProperty("path") String path, @JsonProperty("type") String type, @JsonProperty("attributes") List<String> attributes) {
        this.path = path;
        this.type = type;
        this.attributes = attributes;
    }

    @JsonProperty("path")
    public String getPath() {
        return path;
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("attributes")
    public List<String> getAttributes() {
        return attributes;
    }

    @JsonIgnore
    public boolean isDirectory() {
        return type.equals("commit_directory");
    }

    public BitbucketSCMFile toBitbucketScmFile(BitbucketSCMFile parent){
        SCMFile.Type fileType;
        if(isDirectory()){
            fileType = SCMFile.Type.DIRECTORY;
        } else {
            fileType = SCMFile.Type.REGULAR_FILE;
            for(String attribute: getAttributes()){
                if(attribute.equals("link")){
                    fileType = SCMFile.Type.LINK;
                } else if(attribute.equals("subrepository")){
                    fileType = SCMFile.Type.OTHER; // sub-module or sub-repo
                }
            }
        }
        return new BitbucketSCMFile(parent, path, fileType);
    }
}
