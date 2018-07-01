package com.cloudbees.jenkins.plugins.bitbucket;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.PrintStream;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus;

import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import hudson.model.AbstractBuild;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Result;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.util.AbstractTaskListener;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import junit.framework.TestCase;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ Jenkins.class, Job.class, DisplayURLProvider.class, SCMSource.SourceByItem.class,
        JenkinsLocationConfiguration.class })
public class BitbucketBuildStatusNotificationsTest extends TestCase {

    @Mock
    private Jenkins jenkins;

    @Mock
    private JenkinsLocationConfiguration jenkinsLocationConfiguration;

    @Mock
    private DisplayURLProvider displayURLProvider;

    @Mock
    private SCMRevisionAction action;

    @Mock
    private AbstractBuild build;

    @Mock
    private Job job;

    @Mock
    private AbstractTestResultAction testResultAction;

    @Mock
    private AbstractGitSCMSource.SCMRevisionImpl revision;

    @Mock
    private BitbucketSCMSource source;

    @Mock
    private BitbucketApi bitbucketApi;

    @Mock
    private AbstractTaskListener listener;

    @Mock
    private PrintStream printStream;

    private String hash = "fake";
    private String buildRunUrl = "http://myjenkins:8080/myjob/mybranch/1";
    private String jobFullName = "myjob/mybranch";
    private String buildFullDisplayName = "#55";
    private Result buildResult = Result.UNSTABLE;
    private BitbucketBuildStatus.State expectedBuildState = BitbucketBuildStatus.State.FAILED;

    @Test
    public void bitbucketBuildStatusNotificationsWithTestResultsTest() throws Exception {
        int totalTestsCount = 10;
        int failTestsCount = 5;
        int passedCount = totalTestsCount - failTestsCount;
        String expectedBuildNotificationDescription = Messages.BitbucketBuildStatusNotifications_DescriptionUnstable() + ". "
                + passedCount + " of " + totalTestsCount + " tests passed";

        PowerMockito.when(testResultAction.getTotalCount()).thenReturn(totalTestsCount);
        PowerMockito.when(testResultAction.getFailCount()).thenReturn(failTestsCount);

        PowerMockito.when(job.getFullName()).thenReturn(jobFullName);
        PowerMockito.when(build.getParent()).thenReturn(job);
        PowerMockito.when(build.getFullDisplayName()).thenReturn(buildFullDisplayName);
        PowerMockito.when(build.getResult()).thenReturn(buildResult);
        PowerMockito.when(build.getAction(AbstractTestResultAction.class)).thenReturn(testResultAction);

        PowerMockito.when(revision.getHash()).thenReturn(hash);

        PowerMockito.when(action.getRevision()).thenReturn(revision);

        PowerMockito.when(build.getAction(SCMRevisionAction.class)).thenReturn(action);

        PowerMockito.mockStatic(SCMSource.SourceByItem.class);

        PowerMockito.when(SCMSource.SourceByItem.findSource(any(Item.class))).thenReturn(source);

        PowerMockito.when(source.buildBitbucketClient()).thenReturn(bitbucketApi);

        PowerMockito.mockStatic(JenkinsLocationConfiguration.class);
        PowerMockito.when(jenkinsLocationConfiguration.getUrl()).thenReturn("http://myjenkins:8080");
        PowerMockito.when(JenkinsLocationConfiguration.get()).thenReturn(jenkinsLocationConfiguration);

        PowerMockito.mockStatic(Jenkins.class);
        PowerMockito.when(Jenkins.getInstance()).thenReturn(jenkins);

        PowerMockito.mockStatic(DisplayURLProvider.class);
        PowerMockito.when(displayURLProvider.getRunURL(build)).thenReturn(buildRunUrl);
        PowerMockito.when(DisplayURLProvider.get()).thenReturn(displayURLProvider);

        BitbucketBuildStatusNotifications.JobCompletedListener jobCompletedListener = new BitbucketBuildStatusNotifications.JobCompletedListener();

        PowerMockito.when(listener.getLogger()).thenReturn(printStream);

        jobCompletedListener.onCompleted(build, listener);

        BitbucketBuildStatus buildStatus = new BitbucketBuildStatus(hash, expectedBuildNotificationDescription,
                expectedBuildState, buildRunUrl, jobFullName, buildFullDisplayName);

        verify(bitbucketApi, times(1)).postBuildStatus(argThat(new IsBuildStatusIsCorrect(buildStatus)));
        verify(printStream, times(1)).println("[Bitbucket] Build result notified");
    }

    @Test
    public void bitbucketBuildStatusNotificationsWithNoTestResultsTest() throws Exception {
        String expectedBuildNotificationDescription = Messages.BitbucketBuildStatusNotifications_DescriptionUnstable();

        PowerMockito.when(job.getFullName()).thenReturn(jobFullName);
        PowerMockito.when(build.getParent()).thenReturn(job);
        PowerMockito.when(build.getFullDisplayName()).thenReturn(buildFullDisplayName);
        PowerMockito.when(build.getResult()).thenReturn(buildResult);
        PowerMockito.when(build.getAction(AbstractTestResultAction.class)).thenReturn(null);

        PowerMockito.when(revision.getHash()).thenReturn(hash);

        PowerMockito.when(action.getRevision()).thenReturn(revision);

        PowerMockito.when(build.getAction(SCMRevisionAction.class)).thenReturn(action);

        PowerMockito.mockStatic(SCMSource.SourceByItem.class);

        PowerMockito.when(SCMSource.SourceByItem.findSource(any(Item.class))).thenReturn(source);

        PowerMockito.when(source.buildBitbucketClient()).thenReturn(bitbucketApi);

        PowerMockito.mockStatic(JenkinsLocationConfiguration.class);
        PowerMockito.when(jenkinsLocationConfiguration.getUrl()).thenReturn("http://myjenkins:8080");
        PowerMockito.when(JenkinsLocationConfiguration.get()).thenReturn(jenkinsLocationConfiguration);

        PowerMockito.mockStatic(Jenkins.class);
        PowerMockito.when(Jenkins.getInstance()).thenReturn(jenkins);

        PowerMockito.mockStatic(DisplayURLProvider.class);
        PowerMockito.when(displayURLProvider.getRunURL(build)).thenReturn(buildRunUrl);
        PowerMockito.when(DisplayURLProvider.get()).thenReturn(displayURLProvider);

        BitbucketBuildStatusNotifications.JobCompletedListener jobCompletedListener = new BitbucketBuildStatusNotifications.JobCompletedListener();

        PowerMockito.when(listener.getLogger()).thenReturn(printStream);

        jobCompletedListener.onCompleted(build, listener);

        BitbucketBuildStatus buildStatus = new BitbucketBuildStatus(hash, expectedBuildNotificationDescription,
                expectedBuildState, buildRunUrl, jobFullName, buildFullDisplayName);

        verify(bitbucketApi, times(1)).postBuildStatus(argThat(new IsBuildStatusIsCorrect(buildStatus)));
        verify(printStream, times(1)).println("[Bitbucket] Build result notified");
    }

    class IsBuildStatusIsCorrect extends ArgumentMatcher<BitbucketBuildStatus> {

        BitbucketBuildStatus buildStatus;

        IsBuildStatusIsCorrect(BitbucketBuildStatus buildStatus) {
            this.buildStatus = buildStatus;
        }

        @Override
        public boolean matches(Object givenBitbucketBuildStatusObject) {

            BitbucketBuildStatus givenBitbucketBuildStatus = (BitbucketBuildStatus) givenBitbucketBuildStatusObject;
            return givenBitbucketBuildStatus.getHash().equals(this.buildStatus.getHash())
                    && givenBitbucketBuildStatus.getDescription().equals(this.buildStatus.getDescription())
                    && givenBitbucketBuildStatus.getState().equals(this.buildStatus.getState())
                    && givenBitbucketBuildStatus.getUrl().equals(this.buildStatus.getUrl())
                    && givenBitbucketBuildStatus.getKey().equals(this.buildStatus.getKey());
        }

    }
}