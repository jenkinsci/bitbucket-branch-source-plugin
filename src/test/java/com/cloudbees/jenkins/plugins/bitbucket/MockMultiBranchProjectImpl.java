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

import com.cloudbees.jenkins.plugins.bitbucket.BranchScanningIntegrationTest.BranchProperty;
import hudson.Extension;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import jenkins.branch.Branch;
import jenkins.branch.BranchProjectFactory;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.MultiBranchProjectDescriptor;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;

public class MockMultiBranchProjectImpl extends MultiBranchProject<FreeStyleProject, FreeStyleBuild> {

    @SuppressWarnings("serial")
    public static final SCMSourceCriteria CRITERIA = new SCMSourceCriteria() {
        @Override
        public boolean isHead(SCMSourceCriteria.Probe probe, TaskListener listener) throws IOException {
            return probe.stat("markerfile.txt").exists();
        }

        @Override
        public int hashCode() {
            return getClass().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return getClass().isInstance(obj);
        }
    };

    public MockMultiBranchProjectImpl(ItemGroup<?> parent, String name) {
        super(parent, name);
    }

    @Override
    protected BranchProjectFactory<FreeStyleProject, FreeStyleBuild> newProjectFactory() {
        return new BranchProjectFactoryImpl();
    }

    @Override
    public SCMSourceCriteria getSCMSourceCriteria(SCMSource source) {
        return CRITERIA;
    }

    @Override
    public List<SCMSource> getSCMSources() {
        if (getSourcesList() == null) {
            // test code is generating a NullPointer when calling it from an ItemListener on the SCMSourceOwner
            // It seems that the object is not fully initialized when the ItemListener uses it.
            // Perhaps it needs to be reproduced and investigated in a branch-api test.
            return new ArrayList<>();
        }
        return super.getSCMSources();
    }

    public static class BranchProjectFactoryImpl extends BranchProjectFactory<FreeStyleProject, FreeStyleBuild> {

        @Override
        public FreeStyleProject newInstance(Branch branch) {
            FreeStyleProject job = new FreeStyleProject(getOwner(), branch.getEncodedName());
            setBranch(job, branch);
            return job;
        }

        @Override
        public Branch getBranch(FreeStyleProject project) {
            return project.getProperty(BranchProperty.class).getBranch();
        }

        @Override
        public FreeStyleProject setBranch(FreeStyleProject project, Branch branch) {
            try {
                project.addProperty(new BranchProperty(branch));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return project;
        }

        @Override
        public boolean isProject(Item item) {
            return item instanceof FreeStyleProject && ((FreeStyleProject) item).getProperty(BranchProperty.class) != null;
        }

    }

    @Extension
    public static class DescriptorImpl extends MultiBranchProjectDescriptor {

        @Override
        public String getDisplayName() {
            return "Test Multibranch";
        }

        @Override
        public TopLevelItem newInstance(@SuppressWarnings("rawtypes") ItemGroup parent, String name) {
            return new MockMultiBranchProjectImpl(parent, name);
        }

    }
}
