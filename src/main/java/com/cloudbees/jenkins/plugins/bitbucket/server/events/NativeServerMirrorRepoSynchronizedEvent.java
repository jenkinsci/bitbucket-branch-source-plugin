package com.cloudbees.jenkins.plugins.bitbucket.server.events;

import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketServerRepository;
import java.util.Collections;
import java.util.List;

public class NativeServerMirrorRepoSynchronizedEvent {
    private BitbucketServerMirrorServer mirrorServer;

    private BitbucketServerRepository repository;
    private List<NativeServerChange> changes;

    public BitbucketServerMirrorServer getMirrorServer() {
        return mirrorServer;
    }

    public BitbucketServerRepository getRepository() {
        return repository;
    }

    public List<NativeServerChange> getChanges() {
        return changes == null ? Collections.emptyList() : Collections.unmodifiableList(changes);
    }

}
