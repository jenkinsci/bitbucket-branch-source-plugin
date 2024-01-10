package com.cloudbees.jenkins.plugins.bitbucket.server.events;

public class NativeServerChange {
    private NativeServerRef ref;
    private String refId, fromHash, toHash, type;

    public NativeServerRef getRef() {
        return ref;
    }

    public void setRef(NativeServerRef ref) {
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
