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
package com.cloudbees.jenkins.plugins.bitbucket.hooks.bitbucketserver;

import com.cloudbees.jenkins.plugins.bitbucket.BranchSCMHead;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryType;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.scm.SCM;
import hudson.security.csrf.CrumbExclusion;
import hudson.util.HttpResponses;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.eclipse.jgit.transport.URIish;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Process <a href="https://confluence.atlassian.com/bitbucketserver/post-service-webhook-for-bitbucket-server-776640367.html">Bitbucket Server
 * POST service webhooks</a>.
 */
@Extension
public class BitbucketServerPushHookReceiver extends CrumbExclusion implements UnprotectedRootAction {

    private static final Logger LOGGER = Logger.getLogger(BitbucketServerPushHookReceiver.class.getName());
    private static final String PATH = "bitbucket-server-hook";

    public static final String FULL_PATH = PATH + "/notify";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean process(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        String pathInfo = req.getPathInfo();
        if (pathInfo != null && pathInfo.startsWith("/" + FULL_PATH)) {
            chain.doFilter(req, resp);
            return true;
        }
        return false;
    }

    @Override
    public String getUrlName() {
        return PATH;
    }

    public HttpResponse doNotify(StaplerRequest request) throws IOException {
        String body = IOUtils.toString(request.getInputStream());
        LOGGER.log(Level.FINEST, "Received POST service webhook: {0}", body);
        JsonNode node = objectMapper.readTree(body);
        String origin = SCMEvent.originOf(request);
        JsonNode repositoryNode = node.get("repository");
        String project = repositoryNode.get("project").get("key").asText();
        String repository = repositoryNode.get("name").asText();
        for (JsonNode refChange : node.get("refChanges")) {
            String refId = refChange.get("refId").asText();
            String toHash = refChange.get("toHash").asText();
            String type = refChange.get("type").asText();
            switch (type) {
                case "ADD":
                    SCMHeadEvent.fireNow(new BitbucketCreateSCMHeadEvent(origin, project, repository, refId, toHash));
                    LOGGER.log(Level.FINE, "Fired SCMHeadEvent CREATE for {0}/{1} {2}", new Object[]{project, repository, refId});
                    break;
                case "UPDATE":
                    SCMHeadEvent.fireNow(new BitbucketExistingSCMHeadEvent(SCMEvent.Type.UPDATED, origin, project, repository, refId, toHash));
                    LOGGER.log(Level.FINE, "Fired SCMHeadEvent UPDATE for {0}/{1} {2}", new Object[]{project, repository, refId});
                    break;
                case "DELETE":
                    SCMHeadEvent.fireNow(new BitbucketExistingSCMHeadEvent(SCMEvent.Type.REMOVED, origin, project, repository, refId, toHash));
                    LOGGER.log(Level.FINE, "Fired SCMHeadEvent REMOVED for {0}/{1} {2}", new Object[]{project, repository, refId});
                    break;
                default:
                    LOGGER.log(Level.FINE, "Received hook with unknown refChange type: {0}", type);
            }
        }
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

    private abstract static class BitbucketSCMHeadEvent extends SCMHeadEvent<String> {

        private final String project;
        private final String repository;
        private final String sha1;

        BitbucketSCMHeadEvent(SCMEvent.Type type, String origin, String project, String repository, String branch, String sha1) {
            super(type, branch, origin);
            this.project = project;
            this.repository = repository;
            this.sha1 = sha1;
        }

        @Override
        public boolean isMatch(@NonNull SCMNavigator scmNavigator) {
            return false;
        }

        @NonNull
        @Override
        public String getSourceName() {
            return project + "/" + repository;
        }

        @NonNull
        @Override
        public Map<SCMHead, SCMRevision> heads(@NonNull SCMSource scmSource) {
            SCMHead head = createSCMHead();
            return Collections.<SCMHead, SCMRevision>singletonMap(head, sha1 != null ? new AbstractGitSCMSource.SCMRevisionImpl(head, sha1) : null);
        }

        protected String getProject() {
            return project;
        }

        protected String getRepository() {
            return repository;
        }

        protected String getBranchName() {
            return getPayload().replace("refs/heads/", "");
        }

        protected abstract SCMHead createSCMHead();
    }

    private static class BitbucketCreateSCMHeadEvent extends BitbucketSCMHeadEvent {

        BitbucketCreateSCMHeadEvent(String origin, String project, String repository, String branch, String sha1) {
            super(Type.CREATED, origin, project, repository, branch, sha1);
        }

        @Override
        public boolean isMatch(@NonNull SCM scm) {
            return false;
        }

        @Override
        protected SCMHead createSCMHead() {
            return new BranchSCMHead(getBranchName(), BitbucketRepositoryType.GIT);
        }
    }

    private static class BitbucketExistingSCMHeadEvent extends BitbucketSCMHeadEvent {

        BitbucketExistingSCMHeadEvent(Type type, String origin, String project, String repository, String branch, String sha1) {
            super(type, origin, project, repository, branch, sha1);
        }

        @Override
        public boolean isMatch(@NonNull SCM scm) {
            if (scm instanceof GitSCM) {
                for (UserRemoteConfig userRemoteConfig : ((GitSCM) scm).getUserRemoteConfigs()) {
                    try {
                        return isMatch(new URIish(userRemoteConfig.getUrl())) && userRemoteConfig.getRefspec().endsWith(getPayload());
                    } catch (URISyntaxException e) {
                        // nothing to do
                    }
                }
            }
            return false;
        }

        @Override
        protected SCMHead createSCMHead() {
            return new SCMHead(getBranchName());
        }

        private boolean isMatch(URIish urIish) {
            return (urIish.getScheme().equals("https") && urIish.getPath().equals("/scm/" + getProject().toLowerCase() + "/" + getRepository() + ".git"))
                   || (urIish.getScheme().equals("ssh") && urIish.getPath().equals("/" + getProject() + "/" + getRepository() + ".git"));
        }
    }
}
