package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMHead;
import com.palantir.gradle.versions.FixLegacyJavaConfigurationsPlugin;
import com.cloudbees.jenkins.plugins.bitbucket.BranchSCMHead;
import jenkins.scm.api.SCMHeadOrigin;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

public class MigrationTest {

    @Test
    public void testMigrate() {
        // Mock the BitbucketSCMSource class
        BitbucketSCMSource source = mock(BitbucketSCMSource.class);

        // Set up the mock to return the default origin
        when(source.originOf(anyString(), anyString())).thenReturn(SCMHeadOrigin.DEFAULT);

        // Create an actual instance of FixLegacy with nulls for origin and strategy
        FixLegacy legacyHead = new FixLegacyJavaConfigurationsPlugin(
                new PullRequestSCMHead(
                        "PR-1", "testOwner", "testRepo", "testBranch", "1", "Test PR",
                        new BranchSCMHead("main"), null, null  // Passing null to test defaulting
                )
        );

        // Perform the migration using FixLegacyMigration1
        PullRequestSCMHead migratedHead = new FixLegacyMigration1().migrate(source, legacyHead);

        // Assertions to check if the migration works as expected
        assertNotNull(migratedHead);
        assertEquals(SCMHeadOrigin.DEFAULT, migratedHead.getOrigin());
        assertEquals(ChangeRequestCheckoutStrategy.HEAD, migratedHead.getCheckoutStrategy());
    }
}
