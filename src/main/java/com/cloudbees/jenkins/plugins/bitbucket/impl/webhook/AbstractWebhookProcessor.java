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
package com.cloudbees.jenkins.plugins.bitbucket.impl.webhook;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookProcessor;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookProcessorException;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketCredentialsUtils;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.cloud.CloudWebhookConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.webhook.server.ServerWebhookConfiguration;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.Secret;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.SCMSourceOwners;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import static org.apache.commons.lang.StringUtils.trimToNull;

/**
 * Abstract hook processor.
 *
 * Add new hook processors by extending this class and implement {@link #process(String, String, Map, BitbucketEndpoint)},
 * extract details from the hook payload and then fire an {@link jenkins.scm.api.SCMEvent} to dispatch it to the SCM API.
 */
@Restricted(NoExternalUse.class)
public abstract class AbstractWebhookProcessor implements BitbucketWebhookProcessor {
    protected final Logger logger = Logger.getLogger(getClass().getName());

    protected static final String REQUEST_ID_CLOUD_HEADER = "X-Request-UUID";
    protected static final String REQUEST_ID_SERVER_HEADER = "X-Request-Id";
    protected static final String SIGNATURE_HEADER = "X-Hub-Signature";
    protected static final String EVENT_TYPE_HEADER = "X-Event-Key";
    protected static final String SERVER_URL_PARAMETER = "server_url";


    /**
     * To be called by implementations once the owner and the repository have been extracted from the payload.
     *
     * @param owner the repository owner as configured in the SCMSource
     * @param repository the repository name as configured in the SCMSource
     * @param mirrorId the mirror id if applicable, may be null
     */
    protected void scmSourceReIndex(final String owner, final String repository, final String mirrorId) {
        try (ACLContext context = ACL.as2(ACL.SYSTEM2)) {
            boolean reindexed = false;
            for (SCMSourceOwner scmOwner : SCMSourceOwners.all()) {
                List<SCMSource> sources = scmOwner.getSCMSources();
                for (SCMSource source : sources) {
                    // Search for the correct SCM source
                    if (source instanceof BitbucketSCMSource scmSource
                            && StringUtils.equalsIgnoreCase(scmSource.getRepoOwner(), owner)
                            && scmSource.getRepository().equals(repository)
                            && (mirrorId == null || StringUtils.equalsIgnoreCase(mirrorId, scmSource.getMirrorId()))) {
                        logger.log(Level.INFO, "Multibranch project found, reindexing {0}", scmOwner.getName());
                        // TODO: SCMSourceOwner.onSCMSourceUpdated is deprecated. We may explore options with an
                        //  SCMEventListener extension and firing SCMSourceEvents.
                        scmOwner.onSCMSourceUpdated(source);
                        reindexed = true;
                    }
                }
            }
            if (!reindexed) {
                logger.log(Level.INFO, "No multibranch project matching for reindex on {0}/{1}", new Object[] {owner, repository});
            }
        }
    }

    @NonNull
    @Override
    public String getServerURL(@NonNull Map<String, String> headers, @NonNull MultiValuedMap<String, String> parameters) {
        String serverURL = parameters.get(SERVER_URL_PARAMETER).stream()
                .findFirst()
                .orElse(null);
        if (StringUtils.isBlank(serverURL)) {
            throw new BitbucketWebhookProcessorException(HttpServletResponse.SC_BAD_REQUEST, SERVER_URL_PARAMETER + " query parameter not found or empty. Refer to the user documentation on how configure the webHook in Bitbucket at https://github.com/jenkinsci/bitbucket-branch-source-plugin/blob/master/docs/USER_GUIDE.adoc#webhooks-registering");
        }
        return serverURL;
    }

    @NonNull
    @Override
    public String getEventType(@NonNull Map<String, String> headers, @NonNull MultiValuedMap<String, String> parameters) {
        String eventType = headers.get(EVENT_TYPE_HEADER);
        if (StringUtils.isEmpty(eventType)) {
            throw new IllegalStateException(EVENT_TYPE_HEADER + " is missing or empty, this processor should not proceed after canHandle method. Please fill an issue at https://issues.jenkins.io reporting this stacktrace.");
        }
        return eventType;
    }

    @Override
    public void verifyPayload(@NonNull Map<String, String> headers, @NonNull String body, @NonNull BitbucketEndpoint endpoint) throws BitbucketWebhookProcessorException {
        BitbucketWebhookConfiguration webhook = endpoint.getWebhook();
        boolean signatureEnabled = false;
        String signatureCredentialsId = null;
        if (webhook instanceof CloudWebhookConfiguration cloud) {
            signatureEnabled = cloud.isEnableHookSignature();
            signatureCredentialsId = cloud.getHookSignatureCredentialsId();
        } else if (webhook instanceof ServerWebhookConfiguration server) {
            signatureEnabled = server.isEnableHookSignature();
            signatureCredentialsId = server.getHookSignatureCredentialsId();
        } else {
            logger.warning(() -> "Webhook implementation " + webhook.getId() + " not supported for payload verification, it should implements also an own WebookProcessor");
        }

        if (signatureEnabled && !headers.containsKey(SIGNATURE_HEADER)) {
            throw new BitbucketWebhookProcessorException(HttpServletResponse.SC_FORBIDDEN, "Payload has not be signed, configure the webHook secret in Bitbucket as documented at https://github.com/jenkinsci/bitbucket-branch-source-plugin/blob/master/docs/USER_GUIDE.adoc#webhooks-registering");
        }
        if (signatureEnabled && signatureCredentialsId != null) {
            StringCredentials signatureCredentials = lookupCredentials(signatureCredentialsId, endpoint.getServerURL());
            if (signatureCredentials == null) {
                String hookId = headers.get("X-Hook-UUID");
                String requestId = ObjectUtils.firstNonNull(headers.get("X-Request-UUID"), headers.get("X-Request-Id"));
                logger.log(Level.WARNING, "No credentials {0} found to verify the signature of incoming webhook {1} request {2}", new Object[] { signatureCredentialsId, hookId, requestId });
                throw new BitbucketWebhookProcessorException(HttpServletResponse.SC_FORBIDDEN, "No credentials " + signatureCredentialsId + " found in Jenkins to verify the signature");
            } else {
                verifyPayload(headers, body, signatureCredentials);
            }
        }
    }

    /* For test purpose */ StringCredentials lookupCredentials(@NonNull String signatureCredentialsId, @Nullable String serverURL) {
        return BitbucketCredentialsUtils.lookupCredentials(Jenkins.get(), serverURL, signatureCredentialsId, StringCredentials.class);
    }

    private void verifyPayload(@NonNull Map<String, String> headers, @NonNull String body, @NonNull StringCredentials signatureCredentials) {
        String signatureHeader = headers.get(SIGNATURE_HEADER);
        String bitbucketAlgorithm = trimToNull(StringUtils.substringBefore(signatureHeader, "="));
        String bitbucketSignature = trimToNull(StringUtils.substringAfter(signatureHeader, "="));
        HmacAlgorithms algorithm = getAlgorithm(bitbucketAlgorithm);
        if (algorithm == null) {
            throw new BitbucketWebhookProcessorException(HttpServletResponse.SC_FORBIDDEN, "Signature " + bitbucketAlgorithm + " not supported");
        }
        HmacUtils util;
        try {
            String key = Secret.toString(signatureCredentials.getSecret());
            util = new HmacUtils(algorithm, key.getBytes(StandardCharsets.UTF_8));
            byte[] digest = util.hmac(body);
            if (!MessageDigest.isEqual(Hex.decodeHex(bitbucketSignature), digest)) {
                throw new BitbucketWebhookProcessorException(HttpServletResponse.SC_FORBIDDEN, "Signature verification failed");
            }
        } catch (IllegalArgumentException e) {
            throw new BitbucketWebhookProcessorException(HttpServletResponse.SC_BAD_REQUEST, "Signature method not supported: " + algorithm);
        } catch (DecoderException e) {
            throw new BitbucketWebhookProcessorException(HttpServletResponse.SC_BAD_REQUEST, "Hex signature can not be decoded: " + bitbucketSignature);
        }
    }

    protected String getOrigin(Map<String, Object> context) {
        return StringUtils.firstNonBlank((String) context.get("origin"), "unknow");
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
}
