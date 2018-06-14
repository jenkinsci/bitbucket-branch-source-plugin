package com.cloudbees.jenkins.plugins.bitbucket.server.client.cache;

import java.util.Map;
import java.util.LinkedHashMap;

public final class SimpleLRUCache<K,V> extends LinkedHashMap<K,V> {
    private int maxSize;
    public SimpleLRUCache(final int maxSize) {
        super(1, 0.75f, true);
        this.maxSize = maxSize;
    }

    protected boolean removeEldestEntry(final Map.Entry eldest) {
        return size() > maxSize;
    }

}