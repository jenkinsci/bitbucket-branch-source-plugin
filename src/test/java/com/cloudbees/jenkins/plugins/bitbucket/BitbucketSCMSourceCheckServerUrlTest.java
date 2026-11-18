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

import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketServerEndpoint;
import hudson.util.FormValidation;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;

@WithJenkins
class BitbucketSCMSourceCheckServerUrlTest {

    private WorkflowMultiBranchProject project;

    @BeforeEach
    void setup(JenkinsRule rule) throws Exception {
        project = rule.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
    }

    @Issue("JENKINS-76588")
    @Test
    void doCheckServerUrlWhenNoValueThenReturnOk() {
        // On a new (unsaved) form, doFillServerUrlItems and doCheckServerUrl fire concurrently on
        // page load. The check fires before the fill sets a value, so the field is empty.
        // @QueryParameter(fixEmpty = true) converts "" to null at the Stapler HTTP layer.
        // Returning ok() defers validation until the fill completes and sets an actual value,
        // which triggers onchange and re-fires the check with the real selected URL.
        FormValidation result = BitbucketSCMSource.DescriptorImpl.doCheckServerUrl(project, null);
        assertThat(result.kind).isEqualTo(FormValidation.Kind.OK);
    }

    @Issue("JENKINS-76588")
    @Test
    void doCheckServerUrlWhenUnregisteredServerThenReturnError() {
        BitbucketEndpointConfiguration.get().addEndpoint(new BitbucketServerEndpoint("My DC", "https://my-dc.example.com"));
        FormValidation result = BitbucketSCMSource.DescriptorImpl.doCheckServerUrl(project, "https://bitbucket.org");
        assertThat(result.kind).isEqualTo(FormValidation.Kind.ERROR);
        assertThat(result.getMessage()).isEqualTo("Unregistered Server: https://bitbucket.org");
    }

    @Issue("JENKINS-76588")
    @Test
    void doCheckServerUrlWhenRegisteredServerThenReturnOk() {
        BitbucketEndpointConfiguration.get().addEndpoint(new BitbucketServerEndpoint("My DC", "https://my-dc.example.com"));
        FormValidation result = BitbucketSCMSource.DescriptorImpl.doCheckServerUrl(project, "https://my-dc.example.com");
        assertThat(result.kind).isEqualTo(FormValidation.Kind.OK);
    }
}