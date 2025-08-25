/*
 * The MIT License
 *
 * Copyright (c) 2025, Falco Nikolas
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
package com.cloudbees.jenkins.plugins.bitbucket.api;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

/**
 * The implementation provides an authenticated client for a configured
 * Bitbucket endpoint.
 *
 * @apiNote This interface is intended to be consumed in an extension point.
 *
 * @author Nikolas Falco
 * @since 937.0.0
 */
public interface BitbucketAuthenticatedClient extends AutoCloseable {

    /**
     * The owner of the repository where register the webhook.
     */
    @NonNull
    String getRepositoryOwner();

    /**
     * Name of the repository where register the webhook.
     */
    @CheckForNull
    String getRepositoryName();

    /**
     * Perform an HTTP POST to the configured endpoint.
     * <p>
     * Request will be sent as JSON
     *
     * @param path to call, it will prepend with the server URL
     * @param payload to send
     * @return the JSON string of the response
     * @throws IOException in case of connection failures
     */
    String post(@NonNull String path, @CheckForNull String payload) throws IOException;

    /**
     * Perform an HTTP PUT to the configured endpoint.
     * <p>
     * Request will be sent as JSON
     *
     * @param path to call, it will prepend with the server URL
     * @param payload to send
     * @return the JSON string of the response
     * @throws IOException in case of connection failures
     */
    String put(@NonNull String path, @CheckForNull String payload) throws IOException;

    /**
     * Perform an HTTP DELETE to the configured endpoint.
     * <p>
     * Request will be sent as JSON
     *
     * @param path to call, it will prepend with the server URL
     * @return the JSON string of the response
     * @throws IOException in case of connection failures
     */
    String delete(@NonNull String path) throws IOException;

    /**
     * Perform an HTTP GET to the configured endpoint.
     * <p>
     * Request will be sent as JSON
     *
     * @param path to call, it will prepend with the server URL
     * @return the JSON string of the response
     * @throws IOException in case of connection failures
     */
    @NonNull
    String get(@NonNull String path) throws IOException;

    @Override
    void close() throws IOException;
}
