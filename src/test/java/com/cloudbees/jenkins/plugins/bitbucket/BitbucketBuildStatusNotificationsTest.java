/*
 * The MIT License
 *
 * Copyright (c) 2018, Nikolas Falco
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
package com.cloudbees.jenkins.plugins.bitbucket;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertNotNull;

public class BitbucketBuildStatusNotificationsTest {

    private void checkURLReturnException(String s) {
        thrown.expect(IllegalStateException.class);
        BitbucketBuildStatusNotifications.checkURL(s);
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void test_checkURL_localhost() {
        checkURLReturnException("http://localhost/build/sample");
    }

    @Test
    public void test_checkURL_noJenkinsURL() {
        checkURLReturnException("http://unconfigured-jenkins-location/");
    }

    @Test
    public void test_checkURL_intranet() {
        checkURLReturnException("http://intranet/build/sample");
    }

    @Test
    public void test_checkURL_intranetWithPort() {
        checkURLReturnException("http://intranet:8080/build/sample");
    }

    @Test
    public void test_checkURL_intranetWithPortAndHttps() {
        checkURLReturnException("https://intranet:8080/build/sample");
    }

    @Test
    public void test_checkURL_isOk() {
        assertNotNull(BitbucketBuildStatusNotifications.checkURL("http://localhost.local/build/sample"));
        assertNotNull(BitbucketBuildStatusNotifications.checkURL("http://intranet.local:8080/build/sample"));
        assertNotNull(BitbucketBuildStatusNotifications.checkURL("http://www.mydomain.com:8000/build/sample"));
        assertNotNull(BitbucketBuildStatusNotifications.checkURL("https://www.mydomain.com/build/sample"));
    }
}
