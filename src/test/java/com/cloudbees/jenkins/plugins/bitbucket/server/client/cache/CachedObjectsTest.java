package com.cloudbees.jenkins.plugins.bitbucket.server.client.cache;

import junit.framework.TestCase;

import java.util.concurrent.Callable;

public class CachedObjectsTest extends TestCase {

    public void testNoCache() throws Exception {
        CachedObjects<Long,Long> c = new CachedObjects<>(0);
        Callable<Long> calculate = new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                return System.currentTimeMillis();
            }
        };
        Long now = c.get(new Long(1), calculate);
        Thread.sleep(100);
        Long now1 = c.get(new Long(1), calculate);

        assertNotSame (now,now1);
    }

    public void testCache() throws Exception {
        CachedObjects<Long,Long> c = new CachedObjects<>(5000000);

        Callable<Long> calculate = new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                return System.currentTimeMillis();
            }
        };
        Long now = c.get(new Long(1), calculate);
        Thread.sleep(100);
        Long now1 = c.get(new Long(1), calculate);

        assertEquals (now,now1);
    }

}