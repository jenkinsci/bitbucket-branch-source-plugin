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
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import hudson.security.csrf.CrumbExclusion;
import hudson.util.HttpResponses;
import hudson.util.Secret;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.scm.api.SCMEvent;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses.HttpResponseException;
import org.kohsuke.stapler.StaplerRequest2;

import static org.apache.commons.lang.StringUtils.trimToNull;

/**
 * Process Bitbucket push and pull requests creations/updates hooks.
 */
@Extension
public class BitbucketSCMSourcePushHookReceiver extends CrumbExclusion implements UnprotectedRootAction {

    private static final Logger LOGGER = Logger.getLogger(BitbucketSCMSourcePushHookReceiver.class.getName());

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
        String origin = SCMEvent.originOf(req);
        String body = IOUtils.toString(req.getInputStream(), StandardCharsets.UTF_8);

        String eventKey = req.getHeader("X-Event-Key");
        if (eventKey == null) {
            return HttpResponses.error(HttpServletResponse.SC_BAD_REQUEST, "X-Event-Key HTTP header not found");
        }
        HookEventType type = HookEventType.fromString(eventKey);
        if (type == null) {
            LOGGER.info(() -> "Received unknown Bitbucket hook: " + eventKey + ". Skipping.");
            return HttpResponses.error(HttpServletResponse.SC_BAD_REQUEST, "X-Event-Key HTTP header invalid: " + eventKey);
        }

        String bitbucketKey = req.getHeader("X-Bitbucket-Type"); // specific header from Plugin implementation
        String serverURL = req.getParameter("server_url");

        BitbucketType instanceType = null;
        if (bitbucketKey != null) {
            instanceType = BitbucketType.fromString(bitbucketKey);
            LOGGER.log(Level.FINE, "X-Bitbucket-Type header found {0}.", instanceType);
        }
        if (serverURL != null) {
            if (instanceType == null) {
                LOGGER.log(Level.FINE, "server_url request parameter found. Bitbucket Native Server webhook incoming.");
                instanceType = BitbucketType.SERVER;
            } else {
                LOGGER.log(Level.FINE, "X-Bitbucket-Type header / server_url request parameter found. Bitbucket Plugin Server webhook incoming.");
            }
        } else {
            LOGGER.log(Level.FINE, "X-Bitbucket-Type header / server_url request parameter not found. Bitbucket Cloud webhook incoming.");
            instanceType = BitbucketType.CLOUD;
            serverURL = BitbucketCloudEndpoint.SERVER_URL;
        }

        BitbucketEndpoint endpoint = BitbucketEndpointProvider
                .lookupEndpoint(serverURL)
                .orElse(null);
        if (endpoint != null) {
            if (endpoint.isEnableHookSignature()) {
                if (req.getHeader("X-Hub-Signature") != null) {
                    HttpResponseException error = checkSignature(req, body, endpoint);
                    if (error != null) {
                        return error;
                    }
                } else {
                    return HttpResponses.error(HttpServletResponse.SC_FORBIDDEN, "Payload has not be signed, configure the webHook secret in Bitbucket as documented at https://github.com/jenkinsci/bitbucket-branch-source-plugin/blob/master/docs/USER_GUIDE.adoc#webhooks-registering");
                }
            } else if (req.getHeader("X-Hub-Signature") == null) {
                LOGGER.log(Level.FINER, "Signature not configured for bitbucket endpoint {0}.", serverURL);
            }
        } else {
            LOGGER.log(Level.INFO, "No bitbucket endpoint found for {0} to verify the signature of incoming webhook.", serverURL);
        }

        HookProcessor hookProcessor = getHookProcessor(type);
        hookProcessor.process(type, body, instanceType, origin, serverURL);
        return HttpResponses.ok();
    }

    @Nullable
    private HttpResponseException checkSignature(@NonNull StaplerRequest2 req, @NonNull String body, @NonNull BitbucketEndpoint endpoint) {
        LOGGER.log(Level.FINE, "Payload endpoint host {0}, request endpoint host {1}", new Object[] { endpoint, req.getRemoteAddr() });

        StringCredentials signatureCredentials = endpoint.hookSignatureCredentials();
        if (signatureCredentials != null) {
            String signatureHeader = req.getHeader("X-Hub-Signature");
            String bitbucketAlgorithm = trimToNull(StringUtils.substringBefore(signatureHeader, "="));
            String bitbucketSignature = trimToNull(StringUtils.substringAfter(signatureHeader, "="));
            HmacAlgorithms algorithm = getAlgorithm(bitbucketAlgorithm);
            if (algorithm == null) {
                return HttpResponses.error(HttpServletResponse.SC_FORBIDDEN, "Signature " + bitbucketAlgorithm + " not supported");
            }
            HmacUtils util;
            try {
                String key = Secret.toString(signatureCredentials.getSecret());
                util = new HmacUtils(algorithm, key.getBytes(StandardCharsets.UTF_8));
                byte[] digest = util.hmac(body);
                if (!MessageDigest.isEqual(Hex.decodeHex(bitbucketSignature), digest)) {
                    return HttpResponses.error(HttpServletResponse.SC_FORBIDDEN, "Signature verification failed");
                }
            } catch (IllegalArgumentException e) {
                return HttpResponses.error(HttpServletResponse.SC_BAD_REQUEST, "Signature method not supported: " + algorithm);
            } catch (DecoderException e) {
                return HttpResponses.error(HttpServletResponse.SC_BAD_REQUEST, "Hex signature can not be decoded: " + bitbucketSignature);
            }
        } else {
            String hookId = req.getHeader("X-Hook-UUID");
            String requestId = ObjectUtils.firstNonNull(req.getHeader("X-Request-UUID"), req.getHeader("X-Request-Id"));
            String hookSignatureCredentialsId = endpoint.getHookSignatureCredentialsId();
            LOGGER.log(Level.WARNING, "No credentials {0} found to verify the signature of incoming webhook {1} request {2}", new Object[] { hookSignatureCredentialsId, hookId, requestId });
            return HttpResponses.error(HttpServletResponse.SC_FORBIDDEN, "No credentials " + hookSignatureCredentialsId + " found in Jenkins to verify the signature");
        }
        return null;
    }

    @CheckForNull
    private HmacAlgorithms getAlgorithm(String algorithm) {
        switch (StringUtils.lowerCase(algorithm)) {
        case "sha1":
            return HmacAlgorithms.HMAC_SHA_1;
        case "sha256":
            return HmacAlgorithms.HMAC_SHA_256;
        case "sha384":
            return HmacAlgorithms.HMAC_SHA_384;
        case "sha512":
            return HmacAlgorithms.HMAC_SHA_512;
        default:
            return null;
        }
    }

    /* For test purpose */
    HookProcessor getHookProcessor(HookEventType type) {
        return type.getProcessor();
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
