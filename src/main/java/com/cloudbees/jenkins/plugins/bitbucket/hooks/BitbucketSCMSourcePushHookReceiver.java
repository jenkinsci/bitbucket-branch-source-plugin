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
package com.cloudbees.jenkins.plugins.bitbucket.hooks;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.security.csrf.CrumbExclusion;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import hudson.util.HttpResponses;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Process Bitbucket push and pull requests creations/updates hooks.
 */
@Extension
public class BitbucketSCMSourcePushHookReceiver extends CrumbExclusion implements UnprotectedRootAction {

    private static final Logger LOGGER = Logger.getLogger(BitbucketSCMSourcePushHookReceiver.class.getName());

    private static final String PATH = "bitbucket-scmsource-hook";

    public static final String FULL_PATH = PATH + "/notify";

    @Override
    public boolean process(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
    throws IOException, ServletException {
        String pathInfo = req.getPathInfo();
        if (pathInfo != null && pathInfo.startsWith("/"+FULL_PATH)) {
            chain.doFilter(req, resp);
            return true;
        }
        return false;
    }

    @Override
    public String getUrlName() {
        return PATH;
    }

    /**
     * Receives Bitbucket push notifications.
     *
     * @param req Stapler request. It contains the payload in the body content
     *          and a header param "X-Event-Key" pointing to the event type.
     * @return the HTTP response object
     * @throws IOException if there is any issue reading the HTTP content paylod.
     */
    public HttpResponse doNotify(StaplerRequest req) throws IOException {
        String body = IOUtils.toString(req.getInputStream());
        String eventKey = req.getHeader("X-Event-Key");
        if (eventKey == null) {
            return HttpResponses.error(HttpServletResponse.SC_BAD_REQUEST, "X-Event-Key HTTP header not found");
        }
        HookEventType type = HookEventType.fromString(eventKey);
        if (type == null) {
            LOGGER.info("Received unknown Bitbucket hook: " + eventKey + ". Skipping.");
            return HttpResponses.error(HttpServletResponse.SC_BAD_REQUEST, "X-Event-Key HTTP header invalid: " + eventKey);
        }

        String bitbucketKey = req.getHeader("X-Bitbucket-Type");
        BitbucketType instanceType = null;
        if (bitbucketKey != null) {
            instanceType = BitbucketType.fromString(bitbucketKey);
        }
        if(instanceType == null){
            LOGGER.log(Level.FINE, "X-Bitbucket-Type header not found. Bitbucket Cloud webhook incoming.");
            instanceType = BitbucketType.CLOUD;
        }

        type.getProcessor().process(body, instanceType);
        return HttpResponses.ok();
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

}
