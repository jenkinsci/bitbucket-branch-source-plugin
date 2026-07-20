/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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


package com.cloudbees.jenkins.plugins.bitbucket.impl.credentials;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.hc.core5.http.HttpRequest;

// TODO verify what is the best between BitbucketAuthenticator or an helper class in the API client,
// BitbucketAuthenticator is transparent to the API Client and user and could build it outside.
// helper class could also handle the header instead hardcode in the client logic and simplify the setupClientBuilder() method that does not have to hardcode this class for avoid register a retryStrategy ? 
public class BitbucketCredentialsPoolAuthenticator implements BitbucketAuthenticator {

    /**
     * Cache of timestamps of when credentials reach the rate limit.
     */
    private static final Map<String, Date> rateLimitCache = new ConcurrentHashMap<>();

    // TODO all Bitbucket authenticator must implements equals and hashcode (based on getId())
    private Set<BitbucketAuthenticator> authenticators;
    private BitbucketAuthenticator current;

    public BitbucketCredentialsPoolAuthenticator(Set<BitbucketAuthenticator> autheticators) {
        this.authenticators = new LinkedHashSet<>(autheticators);
    }

    @Override
    public void configureRequest(HttpRequest request) {
        current().configureRequest(request);
    }

    @Override
    public String getId() {
        return "pool";
    }

    private BitbucketAuthenticator current() {
        if (current == null) {
            // TODO take first one?? or call next and implement an markExpired(Date) method to explicit expire current authenticator?? in the last case here call next() would be enough
            current = authenticators.iterator().next(); 
        } else {
            // TODO should check if current has been expired since last return it and delegate the API client handle HTTP 429 ?
        }
        return current;
    }

/*
    public synchronized markExpired() {
    }
*/

    public synchronized BitbucketAuthenticator next() {
        // TODO add current id (if not already exists) to the expiry cache with current Date (or move to markExpired(Date date) method??
        // TODO remove from cache all authenticators id older than 1 hour (check bitbucket documentation to know the rate limit restore window)
        // TODO take next authenticator not present in the expiry cache -> not expired
        // TODO set it as current and return
        // TODO if all expired throw exception
        return null;
    }
}
