package com.cloudbees.jenkins.plugins.bitbucket.server.client.cache;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;

public class CachedObjects<K,V> {

    private final long timeout;

    private Map<K,Long> timestamps = new HashMap<K,Long>();
    private Map<K,V> cachedObjects = new HashMap<K,V>();

    public interface IFilter<K> {
        public boolean matches(K key);
    }

    public CachedObjects(long timeout) {
        this.timeout = timeout;
    }

    public synchronized V get(K key, Callable<V> result) throws Exception {
        Long last = timestamps.get(key);
        Long now = System.currentTimeMillis();
        if (last != null &&  now < last + timeout) {
            return cachedObjects.get(key);
        }
        V cache = result.call();
        cachedObjects.put(key,cache);
        timestamps.put(key,now);
        return cache;
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
                timestamps.remove(key);
            }
        }

    }
}
