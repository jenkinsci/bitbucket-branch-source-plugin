package com.cloudbees.jenkins.plugins.bitbucket.api;

import java.util.Date;
import java.util.List;

/**
 * Represents a push event coming from Bitbucket (webhooks).
 */
public interface BitbucketPushEvent {
    /**
     * @return the destination repository that the push points to.
     */
    BitbucketRepository getRepository();

    List<? extends Change> getChanges();

    interface Change {
        Reference getNew();
        Reference getOld();
        boolean isCreated();
        boolean isClosed();
    }

    interface Reference {
        Date getDate();
        String getType();
        String getName();
        Target getTarget();
    }

    interface Target {
        String getHash();
        Date getDate();
    }
}
