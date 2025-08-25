/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketMockApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.test.util.BitbucketClientMockUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.SCMSourceObserver.ProjectObserver;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.impl.NullSCMSource;
import jenkins.scm.impl.trait.RegexSCMSourceFilterTrait;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.when;

@WithJenkins
class SCMNavigatorTest {

    private static JenkinsRule j;

    @BeforeAll
    static void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void teamRepositoriesDiscovering() throws IOException, InterruptedException {
        BitbucketMockApiFactory.add("http://bitbucket.test",
                BitbucketClientMockUtils.getAPIClientMock(true, false));
        BitbucketSCMNavigator navigator = new BitbucketSCMNavigator("myteam");
        navigator.setServerUrl("http://bitbucket.test");
        navigator.setTraits(List.of(new RegexSCMSourceFilterTrait("repo(.*)")));

        final SCMSourceOwner mock = Mockito.mock(SCMSourceOwner.class);
        when(mock.getSCMSources()).thenReturn(Collections.singletonList(new BitbucketSCMSource("myteam", "repo1")));
        SCMSourceObserverImpl observer = new SCMSourceObserverImpl(
            BitbucketClientMockUtils.getTaskListenerMock(), mock);
        navigator.visitSources(observer);

        assertEquals("myteam", navigator.getRepoOwner());

        List<String> observed = observer.getObserved();
        // Only 2 repositories match the pattern
        assertEquals(2, observed.size(), "There must be 2 repositories in the team");
        assertEquals("repo2", observed.get(0), "repo2 should be first");
        assertEquals("repo1", observed.get(1), "repo1 should be second");

        List<ProjectObserver> observers = observer.getProjectObservers();
        for (ProjectObserver obs : observers) {
            List<SCMSource> sources = ((SCMSourceObserverImpl.ProjectObserverImpl) obs).getSources();
            // It should contain only one source
            assertEquals(1, sources.size(), "Only one source must be created per observed repository");
            SCMSource scmSource = sources.get(0);
            assertInstanceOf(BitbucketSCMSource.class, scmSource,
                "BitbucketSCMSource instances must be added");
            // Check correct repoOwner (team name in this case) was set
            assertEquals("myteam", ((BitbucketSCMSource) scmSource).getRepoOwner());
        }
    }

    @Test
    void teamRepositoriesDiscoveringNullSource() throws IOException, InterruptedException {
        BitbucketMockApiFactory.add("http://bitbucket.test",
                                    BitbucketClientMockUtils.getAPIClientMock(true, false));
        BitbucketSCMNavigator navigator = new BitbucketSCMNavigator("myteam");
        navigator.setServerUrl("http://bitbucket.test");
        navigator.setTraits(List.of(new RegexSCMSourceFilterTrait("repo(.*)")));

        final SCMSourceOwner mock = Mockito.mock(SCMSourceOwner.class);
        when(mock.getSCMSources()).thenReturn(List.of(new BitbucketSCMSource("myteam", "repo1"), new NullSCMSource()));

        SCMSourceObserverImpl observer = new SCMSourceObserverImpl(
            BitbucketClientMockUtils.getTaskListenerMock(), mock);
        navigator.visitSources(observer);

        assertEquals("myteam", navigator.getRepoOwner());

        List<String> observed = observer.getObserved();
        // Only 2 repositories match the pattern
        assertEquals(2, observed.size(), "There must be 2 repositories in the team");
        assertEquals("repo2", observed.get(0), "repo2 should be first");
        assertEquals("repo1", observed.get(1), "repo1 should be second");

        List<ProjectObserver> observers = observer.getProjectObservers();
        for (ProjectObserver obs : observers) {
            List<SCMSource> sources = ((SCMSourceObserverImpl.ProjectObserverImpl) obs).getSources();
            // It should contain only one source
            assertEquals(1, sources.size(), "Only one source must be created per observed repository");
            SCMSource scmSource = sources.get(0);
            assertInstanceOf(BitbucketSCMSource.class, scmSource,
                "BitbucketSCMSource instances must be added");
            // Check correct repoOwner (team name in this case) was set
            assertEquals("myteam", ((BitbucketSCMSource) scmSource).getRepoOwner());
        }
    }

    private static class SCMSourceObserverImpl extends SCMSourceObserver {

        List<String> observed = new ArrayList<>();
        List<ProjectObserver> projectObservers = new ArrayList<>();
        TaskListener listener;
        SCMSourceOwner owner;

        public SCMSourceObserverImpl(TaskListener listener, SCMSourceOwner owner) {
            this.listener = listener;
            this.owner = owner;
        }

        @NonNull
        @Override
        public SCMSourceOwner getContext() {
            return owner;
        }

        @NonNull
        @Override
        public TaskListener getListener() {
            return listener;
        }

        @NonNull
        @Override
        public ProjectObserver observe(@NonNull String projectName) throws IllegalArgumentException {
            observed.add(projectName);
            ProjectObserverImpl obs = new ProjectObserverImpl();
            projectObservers.add(obs);
            return obs;
        }

        @Override
        public void addAttribute(@NonNull String key, Object value) throws IllegalArgumentException, ClassCastException {
        }

        public List<String> getObserved() {
            return observed;
        }

        public List<ProjectObserver> getProjectObservers() {
            return projectObservers;
        }

        public static class ProjectObserverImpl extends ProjectObserver {

            private List<SCMSource> sources = new ArrayList<>();

            @Override
            public void addSource(@NonNull SCMSource source) {
                sources.add(source);
            }

            @Override
            public void addAttribute(@NonNull String key, Object value) throws IllegalArgumentException, ClassCastException {
            }

            @Override
            public void complete() throws IllegalStateException, InterruptedException {
            }

            public List<SCMSource> getSources() {
                return sources;
            }
        }
    }

}
