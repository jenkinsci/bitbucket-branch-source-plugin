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

import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpointProvider;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookProcessor;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookProcessorException;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.UnprotectedRootAction;
import hudson.security.csrf.CrumbExclusion;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.apache.commons.collections4.EnumerationUtils;
import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Process Bitbucket push and pull requests creations/updates hooks.
 */
@Extension
public class BitbucketSCMSourcePushHookReceiver extends CrumbExclusion implements UnprotectedRootAction {

    private static final Logger logger = Logger.getLogger(BitbucketSCMSourcePushHookReceiver.class.getName());
    private static final String PATH = "bitbucket-scmsource-hook";
    public static final String FULL_PATH = PATH + "/notify";

    @Override
    public boolean process(HttpServletRequest req, HttpServletResponse resp, FilterChain chain) throws IOException, ServletException {
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
     * @throws IOException if there is any issue reading the HTTP content payload.
     */
    public HttpResponse doNotify(StaplerRequest2 req) throws IOException {
        try {
            Map<String, String> reqHeaders = getHeaders(req);
            MultiValuedMap<String, String> reqParameters = getParameters(req);
            BitbucketWebhookProcessor hookProcessor = getHookProcessor(reqHeaders, reqParameters);

            String body = IOUtils.toString(req.getInputStream(), StandardCharsets.UTF_8);
            if (StringUtils.isEmpty(body)) {
                return HttpResponses.error(HttpServletResponse.SC_BAD_REQUEST, "Payload is empty.");
            }

            String serverURL = hookProcessor.getServerURL(Collections.unmodifiableMap(reqHeaders), MultiMapUtils.unmodifiableMultiValuedMap(reqParameters));
            BitbucketEndpoint endpoint = BitbucketEndpointProvider
                    .lookupEndpoint(serverURL)
                    .orElse(null);
            if (endpoint == null) {
                logger.log(Level.SEVERE, "No configured bitbucket endpoint found for {0}.", serverURL);
                return HttpResponses.error(HttpServletResponse.SC_BAD_REQUEST, "No bitbucket endpoint found for " + serverURL);
            }

            logger.log(Level.FINE, "Payload endpoint host {0}, request endpoint host {1}", new Object[] { endpoint, req.getRemoteAddr() });
            hookProcessor.verifyPayload(reqHeaders, body, endpoint);

            Map<String, Object> context = hookProcessor.buildHookContext(req);
            String eventType = hookProcessor.getEventType(Collections.unmodifiableMap(reqHeaders), MultiMapUtils.unmodifiableMultiValuedMap(reqParameters));

            hookProcessor.process(eventType, body, context, endpoint);
        } catch(BitbucketWebhookProcessorException e) {
            return HttpResponses.error(e.getHttpCode(), e.getMessage());
        }
        return HttpResponses.ok();
    }

    private BitbucketWebhookProcessor getHookProcessor(Map<String, String> reqHeaders,
                                                    MultiValuedMap<String, String> reqParameters) {
        BitbucketWebhookProcessor hookProcessor;

        List<BitbucketWebhookProcessor> matchingProcessors = getHookProcessors()
            .filter(processor -> processor.canHandle(Collections.unmodifiableMap(reqHeaders), MultiMapUtils.unmodifiableMultiValuedMap(reqParameters)))
            .toList();
        if (matchingProcessors.isEmpty()) {
            logger.warning(() -> "No processor found for the incoming Bitbucket hook. Skipping.");
            throw new BitbucketWebhookProcessorException(HttpServletResponse.SC_BAD_REQUEST, "No processor found that supports this event. Refer to the user documentation on how configure the webHook in Bitbucket at https://github.com/jenkinsci/bitbucket-branch-source-plugin/blob/master/docs/USER_GUIDE.adoc#webhooks-registering");
        } else if (matchingProcessors.size() > 1) {
            String processors = StringUtils.joinWith("\n- ", matchingProcessors.stream()
                .map(p -> p.getClass().getName())
                .toList());
            logger.severe(() -> "More processors found that handle the incoming Bitbucket hook:\n" + processors);
            throw new BitbucketWebhookProcessorException(HttpServletResponse.SC_CONFLICT, "More processors found that handle the incoming Bitbucket hook.");
        } else {
            hookProcessor = matchingProcessors.get(0);
            logger.fine(() -> "Hook processor " + hookProcessor.getClass().getName() + " found.");
        }
        return hookProcessor;
    }

    /*test*/ Stream<BitbucketWebhookProcessor> getHookProcessors() {
        ExtensionList<BitbucketWebhookProcessor> processors = ExtensionList.lookup(BitbucketWebhookProcessor.class);
        if (processors.isEmpty()) {
            logger.warning(() -> "No registered processors found in the system.");
            throw new BitbucketWebhookProcessorException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No registered processors found in Jenkins. Refer to the user documentation on how configure the webHook in Bitbucket at https://github.com/jenkinsci/bitbucket-branch-source-plugin/blob/master/docs/USER_GUIDE.adoc#webhooks-registering");
        }
        return processors.stream();
    }

    private MultiValuedMap<String, String> getParameters(StaplerRequest2 req) {
        MultiValuedMap<String, String> reqParameters = new ArrayListValuedHashMap<>();
        for (Entry<String, String[]> entry : req.getParameterMap().entrySet()) {
            reqParameters.putAll(entry.getKey(), Arrays.asList(entry.getValue()));
        }
        return reqParameters;
    }

    private Map<String, String> getHeaders(StaplerRequest2 req) {
        // HTTP headers are case insensitive
        Map<String, String> reqHeaders = new CaseInsensitiveMap<>();
        for (String headerName : EnumerationUtils.asIterable(req.getHeaderNames())) {
            reqHeaders.put(headerName, req.getHeader(headerName));
        }
        return reqHeaders;
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
