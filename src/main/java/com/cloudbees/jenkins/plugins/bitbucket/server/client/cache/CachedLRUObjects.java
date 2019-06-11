package com.cloudbees.jenkins.plugins.bitbucket.server.client.cache;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

public class CachedLRUObjects<K,V> {

    private Map<K,V> cachedObjects;

    public interface IFilter<K> {
        public boolean matches(K key);
    }

    public CachedLRUObjects(int maxSize) {
        cachedObjects = new SimpleLRUCache<>(maxSize);
    }

    public synchronized V get(K key, Callable<V> callable) throws Exception {
        V result= cachedObjects.get(key);
        if (result == null) {
            result = callable.call();
            cachedObjects.put(key,result);
        }
        return result;
    }

    public synchronized void invalidate(IFilter<K> filter) {
        if (filter == null) {
            return;
        }
        Iterator<K> iter = cachedObjects.keySet().iterator();
        while (iter.hasNext()) {
            K key = iter.next();
            if (filter.matches(key)) {
                iter.remove();
            }
        }

    }
}
