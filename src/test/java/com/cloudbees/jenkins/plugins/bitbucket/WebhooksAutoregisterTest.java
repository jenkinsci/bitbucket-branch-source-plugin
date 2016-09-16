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

import java.io.File;
import java.io.IOException;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

import com.cloudbees.jenkins.plugins.bitbucket.BranchScanningIntegrationTest.MultiBranchProjectImpl;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.WebhookAutoRegisterListener;
import com.gargoylesoftware.htmlunit.html.HtmlForm;

import hudson.util.RingBufferLogHandler;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.model.JenkinsLocationConfiguration;

public class WebhooksAutoregisterTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void registerHookTest() throws Exception {
        RingBufferLogHandler log = createJULTestHandler();

        MultiBranchProjectImpl p = j.jenkins.createProject(MultiBranchProjectImpl.class, "test");
        BitbucketSCMSource source = BranchScanningIntegrationTest.getTestSCMSource(RepositoryType.GIT, true);
        source.setAutoRegisterHook(true);
        p.getSourcesList().add(new BranchSource(source, new DefaultBranchPropertyStrategy(null)));
        p.scheduleBuild2(0);
        waitForLogFileMessage("Can not register hook. Jenkins root URL is not valid", log);

        setRootUrl();
        p.save(); // force item listener to run onUpdated

        waitForLogFileMessage("Registering hook for amuniz/test-repos", log);

    }

    private void setRootUrl() throws IOException, SAXException, Exception {
        JenkinsLocationConfiguration.get().setUrl(j.getURL().toString().replace("localhost", "127.0.0.1"));
    }

    private void waitForLogFileMessage(String string, RingBufferLogHandler logs) throws IOException, InterruptedException {
        File rootDir = j.jenkins.getRootDir();
        synchronized (rootDir) {
            int limit = 0;
            while (limit < 5) {
                rootDir.wait(1000);
                for (LogRecord r : logs.getView()) {
                    if (r.getMessage().contains(string)) {
                        return;
                    }
                }
                limit++;
            }
        }
        Assert.assertTrue("Expected log not found: " + string, false);
    }

    private RingBufferLogHandler createJULTestHandler() throws SecurityException, IOException {
        RingBufferLogHandler handler = new RingBufferLogHandler();
        SimpleFormatter formatter = new SimpleFormatter();
        handler.setFormatter(formatter);
        Logger logger = Logger.getLogger(WebhookAutoRegisterListener.class.getName());
        logger.addHandler(handler);
        return handler;
    }

}
