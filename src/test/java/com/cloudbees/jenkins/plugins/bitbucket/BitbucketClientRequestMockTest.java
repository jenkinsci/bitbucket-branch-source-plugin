package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertTrue;

/**
 * Creates a BitbucketCloudApiClient with mock Bitbucket client API response status'
 * @author Alex Johnson
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(BitbucketCloudApiClient.class)
public class BitbucketClientRequestMockTest {

    /** The mocked BitBucketApiClient */
    private BitbucketCloudApiClient api;

    /**
     * Initializes the mocked BitbucketCloudApiClient class.
     */
    @Before
    public void setUp() throws Exception {
        api = PowerMockito.spy(new BitbucketCloudApiClient("alexbrjo", "repo1", null));
        String correctUrl = "https://bitbucket.org/api/1.0/repositories/alexbrjo/repo1/raw/";

        // mock api response for branch 'name with spaces'
        PowerMockito.doThrow(new IllegalArgumentException("Invalid uri"))
                .when(api, "getRequestStatus",correctUrl + "name with spaces/Jenkinsfile");
        PowerMockito.doReturn(200)
                .when(api, "getRequestStatus",correctUrl + "name%20with%20spaces/Jenkinsfile");
        // mock api response for branch '~`!@#$%^&*()_+=[]{}\|;"<>,./\?a'
        PowerMockito.doThrow(new IllegalArgumentException("Invalid uri"))
                .when(api, "getRequestStatus",correctUrl + "~`!@#$%^&*()_+=[]{}\\|;\"<>,./\\?a/Jenkinsfile");
        PowerMockito.doReturn(200)
                .when(api, "getRequestStatus",correctUrl + "~%60!@%23$%25%5E&*()_%2B=%5B%5D%7B%7D%5C%7C;%22%3C%3E,./%5C%3Fa/Jenkinsfile");
    }

    /**
     * Tests scanning of hg and git repos that have a branch name with characters that need to be uri encoded. Testing
     * with no cred access.
     */
    @Test
    public void testBranchNameUriEncoding () throws Exception {
        // branch with spaces in the names can be used
        assertTrue(api.checkPathExists("name with spaces", "Jenkinsfile"));
        // branch other characters in the name can be used
        assertTrue(api.checkPathExists("~`!@#$%^&*()_+=[]{}\\|;\"<>,./\\?a", "Jenkinsfile"));
    }
}
