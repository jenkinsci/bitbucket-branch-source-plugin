/*
 * The MIT License
 *
 * Copyright (c) 2016-2018, Yieldlab AG
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
package com.cloudbees.jenkins.plugins.bitbucket.server.events;

import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketServerRepository;
import java.util.Collections;
import java.util.List;

public class NativeServerRefsChangedEvent {

    private BitbucketServerRepository repository;
    private List<Change> changes;

    public BitbucketServerRepository getRepository() {
        return repository;
    }

    public List<Change> getChanges() {
        return changes == null ? Collections.<Change> emptyList() : Collections.unmodifiableList(changes);
    }

    public static class Change {
        private Ref ref;
        private String refId, fromHash, toHash, type;

        public Ref getRef() {
            return ref;
        }

        public void setRef(Ref ref) {
            this.ref = ref;
        }

        public String getRefId() {
            return refId;
        }

        public void setRefId(String refId) {
            this.refId = refId;
        }

        public String getFromHash() {
            return fromHash;
        }

        public void setFromHash(String fromHash) {
            this.fromHash = fromHash;
        }

        public String getToHash() {
            return toHash;
        }

        public void setToHash(String toHash) {
            this.toHash = toHash;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    public static class Ref {
        private String id, displayId, type;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getDisplayId() {
            return displayId;
        }

        public void setDisplayId(String displayId) {
            this.displayId = displayId;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }
}
