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
    public void test_checkURL_noJenkinsURL() {
        checkURLReturnException("http://unconfigured-jenkins-location/");
    }

    @Test
    public void test_checkURL_noJenkinsURL_BlueOceanCase() {
        checkURLReturnException("http://unconfigured-jenkins-location/blue");
    }

    @Test
    public void test_checkURL_isOk() {
        assertNotNull(BitbucketBuildStatusNotifications.checkURL("http://intranet"));
        assertNotNull(BitbucketBuildStatusNotifications.checkURL("http://localhost.local/build/sample"));
        assertNotNull(BitbucketBuildStatusNotifications.checkURL("http://intranet.local:8080/build/sample"));
        assertNotNull(BitbucketBuildStatusNotifications.checkURL("http://www.mydomain.com:8000/build/sample"));
        assertNotNull(BitbucketBuildStatusNotifications.checkURL("https://www.mydomain.com/build/sample"));
    }
}
