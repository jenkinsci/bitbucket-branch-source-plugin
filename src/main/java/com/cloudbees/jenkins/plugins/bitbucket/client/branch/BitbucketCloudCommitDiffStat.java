/*
 * The MIT License
 *
 * Copyright (c) 2025, Nikolas Falco
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
package com.cloudbees.jenkins.plugins.bitbucket.client.branch;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public class BitbucketCloudCommitDiffStat {
    public enum CommitDiffStat {
        added, removed, modified, renamed; // NOSONAR
    }

    public static class FileInfo {
        private String path;
        private String type;

        public String getPath() {
            return path;
        }

        public String getType() {
            return type;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    private String type;
    private CommitDiffStat status;
    private int addedLines;
    private int removedLines;
    private String newPath;
    private String newFileType;
    private String oldPath;
    private String oldFileType;

    @JsonCreator
    public BitbucketCloudCommitDiffStat(@NonNull @JsonProperty("type") String type,
                                        @NonNull @JsonProperty("status") CommitDiffStat status,
                                        @NonNull @JsonProperty("lines_removed") int addedLines,
                                        @NonNull @JsonProperty("lines_added") int removedLines,
                                        @Nullable @JsonProperty("old") FileInfo oldFile,
                                        @Nullable @JsonProperty("new") FileInfo newFile) {
        this.setType(type);
        this.status = status;
        this.addedLines = addedLines;
        this.removedLines = removedLines;
        if (oldFile != null) {
            this.oldPath = oldFile.getPath();
            this.oldFileType = oldFile.getType();
        }
        if (newFile != null) {
            this.newPath = newFile.getPath();
            this.newFileType = newFile.getType();
        }
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public CommitDiffStat getStatus() {
        return status;
    }

    public void setStatus(CommitDiffStat status) {
        this.status = status;
    }

    public int getAddedLines() {
        return addedLines;
    }

    public void setAddedLines(int addedLines) {
        this.addedLines = addedLines;
    }

    public int getRemovedLines() {
        return removedLines;
    }

    public void setRemovedLines(int removedLines) {
        this.removedLines = removedLines;
    }

    public String getNewPath() {
        return newPath;
    }

    public void setNewPath(String newPath) {
        this.newPath = newPath;
    }

    public String getNewFileType() {
        return newFileType;
    }

    public void setNewFileType(String newFileType) {
        this.newFileType = newFileType;
    }

    public String getOldPath() {
        return oldPath;
    }

    public void setOldPath(String oldPath) {
        this.oldPath = oldPath;
    }

    public String getOldFileType() {
        return oldFileType;
    }

    public void setOldFileType(String oldFileType) {
        this.oldFileType = oldFileType;
    }
}
