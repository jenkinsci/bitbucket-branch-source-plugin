/*
 * The MIT License
 *
 * Copyright (c) 2025, Nikolas Falco
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

import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.util.Secret;
import java.time.Duration;
import java.util.concurrent.Executors;

final class BitbucketAuthenticatorUtils {

    private BitbucketAuthenticatorUtils() {
    }

    public static String getPassword(@NonNull UsernamePasswordCredentials credentials) throws InterruptedException {
        TimeLimiter timeLimiter = SimpleTimeLimiter.create(Executors.newSingleThreadExecutor());

        try {
            // JENKINS-75225
            return timeLimiter.callWithTimeout(() -> Secret.toString(credentials.getPassword()), Duration.ofMillis(100));
        } catch (InterruptedException e) {
            // takes long maybe credentials are not stored in Jenkins and requires some rest call than will fail
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
