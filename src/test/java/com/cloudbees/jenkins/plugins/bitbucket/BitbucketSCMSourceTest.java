package com.cloudbees.jenkins.plugins.bitbucket;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class BitbucketSCMSourceTest {

    public BitbucketSCMSource bitbucketSCMSource;

    @Before
    public void setUp() throws Exception {
        bitbucketSCMSource = new BitbucketSCMSource("1", "owner", "repo");
    }

    @Test
    public void shouldAllowExclusionsAtStartOfBranchName() throws Exception {
        String includes = "master branch-*";
        bitbucketSCMSource.setIncludes(includes);
        assertFalse("Should not exclude branch-* at start of name", bitbucketSCMSource.isExcluded("branch-1.0.0"));
        assertTrue("Should exclude branch in the middle of the branch name", bitbucketSCMSource.isExcluded("FOO_branch-1.0.0"));
    }
}
