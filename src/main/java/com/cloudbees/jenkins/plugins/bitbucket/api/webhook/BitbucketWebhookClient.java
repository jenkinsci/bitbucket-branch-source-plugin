package com.cloudbees.jenkins.plugins.bitbucket.api.webhook;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

public interface BitbucketWebhookClient extends AutoCloseable {

    String post(@NonNull String path, @CheckForNull String payload) throws IOException ;

    String put(@NonNull String path, @CheckForNull String payload) throws IOException ;

    String delete(@NonNull String path) throws IOException ;

    @NonNull
    String get(@NonNull String path) throws IOException ;

    @Override
    void close() throws IOException;
}
