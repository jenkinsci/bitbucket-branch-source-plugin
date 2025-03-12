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
package com.cloudbees.jenkins.plugins.bitbucket.server.client;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketMirrorServer;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketMirroredRepository;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketMirroredRepositoryDescriptor;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRequestException;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketTeam;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.UserRoleInRepository;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketServerEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.filesystem.BitbucketSCMFile;
import com.cloudbees.jenkins.plugins.bitbucket.impl.client.AbstractBitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.impl.credentials.BitbucketUsernamePasswordAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.JsonParser;
import com.cloudbees.jenkins.plugins.bitbucket.server.BitbucketServerVersion;
import com.cloudbees.jenkins.plugins.bitbucket.server.BitbucketServerWebhookImplementation;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.branch.BitbucketServerBranch;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.branch.BitbucketServerBranches;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.branch.BitbucketServerBuildStatus;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.branch.BitbucketServerCommit;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.mirror.BitbucketMirrorServerDescriptors;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.mirror.BitbucketMirroredRepositoryDescriptors;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.pullrequest.BitbucketServerPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.pullrequest.BitbucketServerPullRequestCanMerge;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.pullrequest.BitbucketServerPullRequests;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketServerProject;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketServerRepositories;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketServerRepository;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketServerWebhooks;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.NativeBitbucketServerWebhooks;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.damnhandy.uri.template.UriTemplate;
import com.damnhandy.uri.template.impl.Operator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFile.Type;
import jenkins.scm.impl.avatars.AvatarImage;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang.StringUtils.abbreviate;
import static org.apache.commons.lang.StringUtils.substring;

/**
 * Bitbucket API client.
 * Developed and test with Bitbucket 4.3.2
 */
public class BitbucketServerAPIClient extends AbstractBitbucketApi implements BitbucketApi {

    // Max avatar image length in bytes
    private static final int MAX_AVATAR_LENGTH = 16384;

    private static final String API_BASE_PATH = "/rest/api/1.0";
    private static final String API_REPOSITORIES_PATH = API_BASE_PATH + "/projects/{owner}/repos{?start,limit}";
    private static final String API_REPOSITORY_PATH = API_BASE_PATH + "/projects/{owner}/repos/{repo}";
    private static final String API_DEFAULT_BRANCH_PATH = API_REPOSITORY_PATH + "/branches/default";
    private static final String API_BRANCHES_PATH = API_REPOSITORY_PATH + "/branches{?start,limit}";
    private static final String API_BRANCHES_FILTERED_PATH = API_REPOSITORY_PATH + "/branches{?filterText,start,limit}";
    private static final String API_TAGS_PATH = API_REPOSITORY_PATH + "/tags{?start,limit}";
    private static final String API_TAG_PATH = API_REPOSITORY_PATH + "/tags/{tagName}";
    private static final String API_PULL_REQUESTS_PATH = API_REPOSITORY_PATH + "/pull-requests{?start,limit,at,direction,state}";
    private static final String API_PULL_REQUEST_PATH = API_REPOSITORY_PATH + "/pull-requests/{id}";
    private static final String API_PULL_REQUEST_MERGE_PATH = API_REPOSITORY_PATH + "/pull-requests/{id}/merge";
    private static final String API_PULL_REQUEST_CHANGES_PATH = API_REPOSITORY_PATH + "/pull-requests/{id}/changes{?start,limit}";
    private static final String API_BROWSE_PATH = API_REPOSITORY_PATH + "/browse{/path*}{?at}";
    private static final String API_COMMITS_PATH = API_REPOSITORY_PATH + "/commits{/hash}";
    private static final String API_PROJECT_PATH = API_BASE_PATH + "/projects/{owner}";
    private static final String AVATAR_PATH = API_BASE_PATH + "/projects/{owner}/avatar.png";
    private static final String API_COMMIT_COMMENT_PATH = API_REPOSITORY_PATH + "/commits{/hash}/comments";
    private static final String API_WEBHOOKS_PATH = API_BASE_PATH + "/projects/{owner}/repos/{repo}/webhooks{/id}{?start,limit}";

    private static final String WEBHOOK_BASE_PATH = "/rest/webhook/1.0";
    private static final String WEBHOOK_REPOSITORY_PATH = WEBHOOK_BASE_PATH + "/projects/{owner}/repos/{repo}/configurations";
    private static final String WEBHOOK_REPOSITORY_CONFIG_PATH = WEBHOOK_REPOSITORY_PATH + "/{id}";

    private static final String API_COMMIT_STATUS_PATH = API_BASE_PATH + "/projects/{owner}/repos/{repo}/commits/{hash}/builds";

    private static final String API_MIRRORS_FOR_REPO_PATH = "/rest/mirroring/1.0/repos/{id}/mirrors";
    private static final String API_MIRRORS_PATH = "/rest/mirroring/1.0/mirrorServers";
    private static final Integer DEFAULT_PAGE_LIMIT = 200;

    protected static final HttpClientConnectionManager connectionManager = connectionManager();

    private static HttpClientConnectionManager connectionManager() {
        try {
            PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager(); // NOSONAR
            connManager.setDefaultMaxPerRoute(20);
            connManager.setMaxTotal(22);
            return connManager;
        } catch (Exception e) {
            // in case of exception this avoids ClassNotFoundError which prevents the classloader from loading this class again
            return null;
        }
    }

    /**
     * Repository owner.
     */
    private final String owner;
    /**
     * The repository that this object is managing.
     */
    private final String repositoryName;
    /**
     * Indicates if the client is using user-centric API endpoints or project API otherwise.
     */
    private final boolean userCentric;
    private final String baseURL;
    private final BitbucketServerWebhookImplementation webhookImplementation;
    private final CloseableHttpClient client;

    @Deprecated
    public BitbucketServerAPIClient(@NonNull String baseURL, @NonNull String owner, @CheckForNull String repositoryName,
                                    @CheckForNull StandardUsernamePasswordCredentials credentials, boolean userCentric) {
        this(baseURL, owner, repositoryName, credentials != null ? new BitbucketUsernamePasswordAuthenticator(credentials) : null,
            userCentric, BitbucketServerEndpoint.findWebhookImplementation(baseURL));
    }

    public BitbucketServerAPIClient(@NonNull String baseURL, @NonNull String owner, @CheckForNull String repositoryName,
                                    @CheckForNull BitbucketAuthenticator authenticator, boolean userCentric) {
        this(baseURL, owner, repositoryName, authenticator, userCentric, BitbucketServerEndpoint.findWebhookImplementation(baseURL));
    }

    public BitbucketServerAPIClient(@NonNull String baseURL, @NonNull String owner, @CheckForNull String repositoryName,
                                    @CheckForNull BitbucketAuthenticator authenticator, boolean userCentric,
                                    @NonNull BitbucketServerWebhookImplementation webhookImplementation) {
        super(authenticator);
        this.userCentric = userCentric;
        this.owner = owner;
        this.repositoryName = repositoryName;
        this.baseURL = Util.removeTrailingSlash(baseURL);
        this.webhookImplementation = requireNonNull(webhookImplementation);
        this.client = setupClientBuilder(baseURL).build();
    }

    /**
     * Bitbucket Server manages two top level entities, owner and/or project.
     * Only one of them makes sense for a specific client object.
     */
    @NonNull
    @Override
    public String getOwner() {
        return owner;
    }

    /**
     * In Bitbucket server the top level entity is the Project, but the JSON API accepts users as a replacement
     * of Projects in most of the URLs (it's called user centric API).
     *
     * This method returns the appropriate string to be placed in request URLs taking into account if this client
     * object was created as a user centric instance or not.
     *
     * @return the ~user or project
     */
    public String getUserCentricOwner() {
        return userCentric ? "~" + owner : owner;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @CheckForNull
    public String getRepositoryName() {
        return repositoryName;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<BitbucketServerPullRequest> getPullRequests() throws IOException, InterruptedException {
        UriTemplate template = UriTemplate
                .fromTemplate(this.baseURL + API_PULL_REQUESTS_PATH)
                .set("owner", getUserCentricOwner())
                .set("repo", repositoryName);
        return getPullRequests(template);
    }

    @NonNull
    public List<BitbucketServerPullRequest> getOutgoingOpenPullRequests(String fromRef) throws IOException, InterruptedException {
        UriTemplate template = UriTemplate
                .fromTemplate(this.baseURL + API_PULL_REQUESTS_PATH)
                .set("owner", getUserCentricOwner())
                .set("repo", repositoryName)
                .set("at", fromRef)
                .set("direction", "outgoing")
                .set("state", "OPEN");
        return getPullRequests(template);
    }

    @NonNull
    public List<BitbucketServerPullRequest> getIncomingOpenPullRequests(String toRef) throws IOException, InterruptedException {
        UriTemplate template = UriTemplate
                .fromTemplate(this.baseURL + API_PULL_REQUESTS_PATH)
                .set("owner", getUserCentricOwner())
                .set("repo", repositoryName)
                .set("at", toRef)
                .set("direction", "incoming")
                .set("state", "OPEN");
        return getPullRequests(template);
    }

    private List<BitbucketServerPullRequest> getPullRequests(UriTemplate template)
        throws IOException, InterruptedException {
        List<BitbucketServerPullRequest> pullRequests = getResources(template, BitbucketServerPullRequests.class);

        pullRequests.removeIf(this::shouldIgnore);

        BitbucketServerEndpoint endpoint = BitbucketEndpointConfiguration.get()
                .findEndpoint(this.baseURL, BitbucketServerEndpoint.class)
                .orElse(null);

        for (BitbucketServerPullRequest pullRequest : pullRequests) {
            setupPullRequest(pullRequest, endpoint);
        }

        if (endpoint != null) {
            // Get PRs again as revisions could be changed by other events during setupPullRequest
            if (endpoint.isCallChanges() && BitbucketServerVersion.VERSION_7.equals(endpoint.getServerVersion())) {
                pullRequests = getResources(template, BitbucketServerPullRequests.class);
                pullRequests.removeIf(this::shouldIgnore);
            }
        }

        return pullRequests;
    }

    private void setupPullRequest(BitbucketServerPullRequest pullRequest, BitbucketServerEndpoint endpoint) throws IOException, InterruptedException {
        // set commit closure to make commit information available when needed, in a similar way to when request branches
        setupClosureForPRBranch(pullRequest);

        if (endpoint != null) {
            // This is required for Bitbucket Server to update the refs/pull-requests/* references
            // See https://community.atlassian.com/t5/Bitbucket-questions/Change-pull-request-refs-after-Commit-instead-of-after-Approval/qaq-p/194702#M6829
            if (endpoint.isCallCanMerge()) {
                try {
                    pullRequest.setCanMerge(getPullRequestCanMergeById(pullRequest.getId()));
                } catch (BitbucketRequestException e) {
                    // see JENKINS-65718 https://docs.atlassian.com/bitbucket-server/rest/7.2.1/bitbucket-rest.html#errors-and-validation
                    // in this case we just say cannot merge this one
                    if(e.getHttpCode()==409){
                        pullRequest.setCanMerge(false);
                    } else {
                        throw e;
                    }
                }
            }
            if (endpoint.isCallChanges() && BitbucketServerVersion.VERSION_7.equals(endpoint.getServerVersion())) {
                callPullRequestChangesById(pullRequest.getId());
            }
        }
    }

    /**
     * PRs with missing source / destination branch are invalid and should be ignored.
     *
     * @param pullRequest a {@link BitbucketPullRequest}
     * @return whether the PR should be ignored
     */
    private boolean shouldIgnore(BitbucketPullRequest pullRequest) {
        return pullRequest.getSource().getRepository() == null
            || pullRequest.getSource().getBranch() == null
            || pullRequest.getDestination().getBranch() == null;
    }

    /**
     * Make available commit information in a lazy way.
     *
     * @author Nikolas Falco
     */
    private class CommitClosure implements Callable<BitbucketCommit> {
        private final String hash;

        public CommitClosure(@NonNull String hash) {
            this.hash = hash;
        }

        @Override
        public BitbucketCommit call() throws Exception {
            return resolveCommit(hash);
        }
    }

    @SuppressFBWarnings(value = "DCN_NULLPOINTER_EXCEPTION", justification = "TODO needs triage")
    private void setupClosureForPRBranch(BitbucketServerPullRequest pr) {
        try {
            BitbucketServerBranch branch = (BitbucketServerBranch) pr.getSource().getBranch();
            if (branch != null) {
                branch.setCommitClosure(new CommitClosure(branch.getRawNode()));
            }
            branch = (BitbucketServerBranch) pr.getDestination().getBranch();
            if (branch != null) {
                branch.setCommitClosure(new CommitClosure(branch.getRawNode()));
            }
        } catch (NullPointerException e) {
            logger.log(Level.SEVERE, "setupClosureForPRBranch", e);
        }
    }

    private void callPullRequestChangesById(@NonNull String id) throws IOException, InterruptedException {
        String url = UriTemplate
                .fromTemplate(this.baseURL + API_PULL_REQUEST_CHANGES_PATH)
                .set("owner", getUserCentricOwner())
                .set("repo", repositoryName)
                .set("id", id).set("limit", 1)
                .expand();
        getRequest(url);
    }

    private boolean getPullRequestCanMergeById(@NonNull String id) throws IOException, InterruptedException {
        String url = UriTemplate
                .fromTemplate(this.baseURL + API_PULL_REQUEST_MERGE_PATH)
                .set("owner", getUserCentricOwner())
                .set("repo", repositoryName)
                .set("id", id)
                .expand();
        String response = getRequest(url);
        return JsonParser.toJava(response, BitbucketServerPullRequestCanMerge.class).isCanMerge();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public BitbucketPullRequest getPullRequestById(@NonNull Integer id) throws IOException, InterruptedException {
        String url = UriTemplate
                .fromTemplate(this.baseURL + API_PULL_REQUEST_PATH)
                .set("owner", getUserCentricOwner())
                .set("repo", repositoryName)
                .set("id", id)
                .expand();
        String response = getRequest(url);
        BitbucketServerPullRequest pr = JsonParser.toJava(response, BitbucketServerPullRequest.class);
        setupClosureForPRBranch(pr);

        BitbucketServerEndpoint endpoint = BitbucketEndpointConfiguration.get()
                .findEndpoint(this.baseURL, BitbucketServerEndpoint.class)
                .orElse(null);
        setupPullRequest(pr, endpoint);
        return pr;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public BitbucketRepository getRepository() throws IOException, InterruptedException {
        if (repositoryName == null) {
            throw new UnsupportedOperationException(
                    "Cannot get a repository from an API instance that is not associated with a repository");
        }
        String url = UriTemplate
                .fromTemplate(this.baseURL + API_REPOSITORY_PATH)
                .set("owner", getUserCentricOwner())
                .set("repo", repositoryName)
                .expand();
        String response = getRequest(url);
        return JsonParser.toJava(response, BitbucketServerRepository.class);
    }

    /**
     * Returns the mirror servers.
     *
     * @return the mirror servers
     * @throws IOException          if there was a network communications error.
     * @throws InterruptedException if interrupted while waiting on remote communications.
     */
    @NonNull
    public List<BitbucketMirrorServer> getMirrors() throws IOException, InterruptedException {
        UriTemplate uriTemplate = UriTemplate
                .fromTemplate(this.baseURL + API_MIRRORS_PATH);
        return getResources(uriTemplate, BitbucketMirrorServerDescriptors.class);
    }

    /**
     * Returns the repository mirror descriptors.
     *
     * @return the repository mirror descriptors for given repository id.
     * @throws IOException          if there was a network communications error.
     * @throws InterruptedException if interrupted while waiting on remote communications.
     */
    @NonNull
    public List<BitbucketMirroredRepositoryDescriptor> getMirrors(@NonNull Long repositoryId) throws IOException, InterruptedException {
        UriTemplate uriTemplate = UriTemplate
                .fromTemplate(this.baseURL + API_MIRRORS_FOR_REPO_PATH)
                .set("id", repositoryId);
        return getResources(uriTemplate, BitbucketMirroredRepositoryDescriptors.class);
    }

    /**
     * Retrieves all available clone urls for the specified repository.
     *
     * @param url mirror repository self-url
     * @return all available clone urls for the specified repository.
     * @throws IOException          if there was a network communications error.
     * @throws InterruptedException if interrupted while waiting on remote communications.
     */
    @NonNull
    public BitbucketMirroredRepository getMirroredRepository(@NonNull String url) throws IOException, InterruptedException {
        HttpGet request = new HttpGet(url);
        String response = doRequest(request, false);
        try {
            return JsonParser.toJava(response, BitbucketMirroredRepository.class);
        } catch (IOException e) {
            throw new IOException("I/O error when accessing URL: " + url, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postCommitComment(@NonNull String hash, @NonNull String comment) throws IOException, InterruptedException {
        postRequest(
            UriTemplate
                .fromTemplate(this.baseURL + API_COMMIT_COMMENT_PATH)
                .set("owner", getUserCentricOwner())
                .set("repo", repositoryName)
                .set("hash", hash)
                .expand(),
            Collections.singletonList(
                new BasicNameValuePair("text", comment)
            )
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postBuildStatus(@NonNull BitbucketBuildStatus status) throws IOException, InterruptedException {
        BitbucketServerBuildStatus newStatus = new BitbucketServerBuildStatus(status);
        newStatus.setName(abbreviate(newStatus.getName(), 255));

        String key = status.getKey();
        if (StringUtils.length(key) > 255) {
            newStatus.setKey(substring(key, 0, 255 - 33) + '/' + DigestUtils.md5Hex(key));
        }

        String url = UriTemplate.fromTemplate(this.baseURL + API_COMMIT_STATUS_PATH)
                .set("owner", getUserCentricOwner())
                .set("repo", repositoryName)
                .set("hash", newStatus.getHash())
                .expand();
        postRequest(url, JsonParser.toJson(newStatus));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkPathExists(@NonNull String branchOrHash, @NonNull String path) throws IOException, InterruptedException {
        String url = UriTemplate
                .fromTemplate(this.baseURL + API_BROWSE_PATH)
                .set("owner", getUserCentricOwner())
                .set("repo", repositoryName)
                .set("path", path.split(Operator.PATH.getSeparator()))
                .set("at", branchOrHash)
                .expand();
        int status = headRequestStatus(new HttpHead(url));
        if (HttpStatus.SC_OK == status) {
            return true;
            // Bitbucket returns UNAUTHORIZED when no credentials are provided
            // https://support.atlassian.com/bitbucket-cloud/docs/use-bitbucket-rest-api-version-1/
        } else if (HttpStatus.SC_NOT_FOUND == status || HttpStatus.SC_UNAUTHORIZED == status) {
            return false;
        } else {
            throw new IOException("Communication error for url: " + path + " status code: " + status);
        }
    }

    @CheckForNull
    @Override
    public String getDefaultBranch() throws IOException, InterruptedException {
        String url = UriTemplate
                .fromTemplate(this.baseURL + API_DEFAULT_BRANCH_PATH)
                .set("owner", getUserCentricOwner())
                .set("repo", repositoryName)
                .expand();
        try {
            String response = getRequest(url);
            return JsonParser.toJava(response, BitbucketServerBranch.class).getName();
        } catch (FileNotFoundException e) {
            logger.log(Level.FINE, "Could not find default branch for {0}/{1}",
                    new Object[]{this.owner, this.repositoryName});
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BitbucketServerBranch getTag(@NonNull String tagName) throws IOException, InterruptedException {
        String url = UriTemplate.fromTemplate(this.baseURL + API_TAG_PATH)
            .set("owner", getUserCentricOwner())
            .set("repo", repositoryName)
            .set("tagName", tagName)
            .expand()
            .replace("%2F", "/");

        String response = getRequest(url);
        BitbucketServerBranch tag = JsonParser.toJava(response, BitbucketServerBranch.class);
        if (tag != null) {
            tag.setCommitClosure(new CommitClosure(tag.getRawNode()));
        }
        return tag;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public List<BitbucketServerBranch> getTags() throws IOException, InterruptedException {
        return getServerBranches(API_TAGS_PATH);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BitbucketServerBranch getBranch(@NonNull String branchName) throws IOException, InterruptedException {
        return getSingleBranch(branchName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public List<BitbucketServerBranch> getBranches() throws IOException, InterruptedException {
        return getServerBranches(API_BRANCHES_PATH);
    }

    private List<BitbucketServerBranch> getServerBranches(String apiPath) throws IOException, InterruptedException {
        UriTemplate template = UriTemplate
                .fromTemplate(this.baseURL + apiPath)
                .set("owner", getUserCentricOwner())
                .set("repo", repositoryName);

        List<BitbucketServerBranch> branches = getResources(template, BitbucketServerBranches.class);
        for (final BitbucketServerBranch branch : branches) {
            if (branch != null) {
                branch.setCommitClosure(new CommitClosure(branch.getRawNode()));
            }
        }

        return branches;
    }

    private BitbucketServerBranch getSingleBranch(String branchName) throws IOException, InterruptedException {
        UriTemplate template = UriTemplate
            .fromTemplate(this.baseURL + API_BRANCHES_FILTERED_PATH)
            .set("owner", getUserCentricOwner())
            .set("repo", repositoryName)
            .set("filterText", branchName);

        BitbucketServerBranch br = getResource(template, BitbucketServerBranches.class,
            branch -> branchName.equals(branch.getName()));
        if(br != null) {
            br.setCommitClosure(new CommitClosure(br.getRawNode()));
        }
        return br;
    }

    /**
     * {@inheritDoc}
     **/
    @NonNull
    @Override
    public BitbucketCommit resolveCommit(@NonNull String hash) throws IOException, InterruptedException {
        String url = UriTemplate
                .fromTemplate(this.baseURL + API_COMMITS_PATH)
                .set("owner", getUserCentricOwner())
                .set("repo", repositoryName)
                .set("hash", hash)
                .expand();
        String response = getRequest(url);
        return JsonParser.toJava(response, BitbucketServerCommit.class);
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public String resolveSourceFullHash(@NonNull BitbucketPullRequest pull) {
        return pull.getSource().getCommit().getHash();
    }

    @NonNull
    @Override
    public BitbucketCommit resolveCommit(@NonNull BitbucketPullRequest pull) throws IOException, InterruptedException {
        return resolveCommit(resolveSourceFullHash(pull));
    }

    @Override
    public void registerCommitWebHook(BitbucketWebHook hook) throws IOException, InterruptedException {
        switch (webhookImplementation) {
            case PLUGIN:
                putRequest(
                        UriTemplate
                            .fromTemplate(this.baseURL + WEBHOOK_REPOSITORY_PATH)
                            .set("owner", getUserCentricOwner())
                            .set("repo", repositoryName)
                            .expand(),
                        JsonParser.toJson(hook)
                    );
                break;

            case NATIVE:
                postRequest(
                        UriTemplate
                            .fromTemplate(this.baseURL + API_WEBHOOKS_PATH)
                            .set("owner", getUserCentricOwner())
                            .set("repo", repositoryName)
                            .expand(),
                        JsonParser.toJson(hook)
                    );
                break;

            default:
                logger.log(Level.WARNING, "Cannot register {0} webhook.", webhookImplementation);
                break;
        }
    }

    @Override
    public void updateCommitWebHook(BitbucketWebHook hook) throws IOException, InterruptedException {
        switch (webhookImplementation) {
            case PLUGIN:
                postRequest(
                        UriTemplate
                            .fromTemplate(this.baseURL + WEBHOOK_REPOSITORY_CONFIG_PATH)
                            .set("owner", getUserCentricOwner())
                            .set("repo", repositoryName)
                            .set("id", hook.getUuid())
                            .expand(), JsonParser.toJson(hook)
                    );
                break;

            case NATIVE:
                putRequest(
                        UriTemplate
                            .fromTemplate(this.baseURL + API_WEBHOOKS_PATH)
                            .set("owner", getUserCentricOwner())
                            .set("repo", repositoryName)
                            .set("id", hook.getUuid())
                            .expand(), JsonParser.toJson(hook)
                    );
                break;

            default:
                logger.log(Level.WARNING, "Cannot update {0} webhook.", webhookImplementation);
                break;
        }
    }

    @Override
    public void removeCommitWebHook(BitbucketWebHook hook) throws IOException, InterruptedException {
        switch (webhookImplementation) {
            case PLUGIN:
                deleteRequest(
                        UriTemplate
                            .fromTemplate(this.baseURL + WEBHOOK_REPOSITORY_CONFIG_PATH)
                            .set("owner", getUserCentricOwner())
                            .set("repo", repositoryName)
                            .set("id", hook.getUuid())
                            .expand()
                    );
                break;

            case NATIVE:
                deleteRequest(
                        UriTemplate
                            .fromTemplate(this.baseURL + API_WEBHOOKS_PATH)
                            .set("owner", getUserCentricOwner())
                            .set("repo", repositoryName)
                            .set("id", hook.getUuid())
                            .expand()
                    );
                break;

            default:
                logger.log(Level.WARNING, "Cannot remove {0} webhook.", webhookImplementation);
                break;
        }
    }

    @NonNull
    @Override
    public List<? extends BitbucketWebHook> getWebHooks() throws IOException, InterruptedException {
        switch (webhookImplementation) {
            case PLUGIN:
                String url = UriTemplate
                        .fromTemplate(this.baseURL + WEBHOOK_REPOSITORY_PATH)
                        .set("owner", getUserCentricOwner())
                        .set("repo", repositoryName)
                        .expand();
                String response = getRequest(url);
                return JsonParser.toJava(response, BitbucketServerWebhooks.class);
            case NATIVE:
                UriTemplate urlTemplate = UriTemplate
                        .fromTemplate(this.baseURL + API_WEBHOOKS_PATH)
                        .set("owner", getUserCentricOwner())
                        .set("repo", repositoryName);
                return getResources(urlTemplate, NativeBitbucketServerWebhooks.class);
        }

        return Collections.emptyList();
    }

    /**
     * There is no such Team concept in Bitbucket Server but Project.
     */
    @Override
    public BitbucketTeam getTeam() throws IOException, InterruptedException {
        if (userCentric) {
            return null;
        } else {
            String url = UriTemplate.fromTemplate(this.baseURL + API_PROJECT_PATH)
                    .set("owner", getOwner())
                    .expand();
            try {
                String response = getRequest(url);
                return JsonParser.toJava(response, BitbucketServerProject.class);
            } catch (FileNotFoundException e) {
                return null;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    @Override
    public AvatarImage getTeamAvatar() throws IOException {
        if (userCentric) {
            return AvatarImage.EMPTY;
        } else {
            String url = UriTemplate.fromTemplate(this.baseURL + AVATAR_PATH)
                    .set("owner", getOwner())
                    .expand();
            return getAvatar(url);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AvatarImage getAvatar(@NonNull String url) throws IOException {
        try {
            BufferedImage response = getImageRequest(url);
            return new AvatarImage(response, System.currentTimeMillis());
        } catch (FileNotFoundException e) {
            return AvatarImage.EMPTY;
        }
    }

    /**
     * The role parameter is ignored for Bitbucket Server.
     */
    @NonNull
    @Override
    public List<BitbucketServerRepository> getRepositories(@CheckForNull UserRoleInRepository role)
            throws IOException, InterruptedException {
        UriTemplate template = UriTemplate
                .fromTemplate(this.baseURL + API_REPOSITORIES_PATH)
                .set("owner", getUserCentricOwner());

        List<BitbucketServerRepository> repositories;
        try {
            repositories = getResources(template, BitbucketServerRepositories.class);
        } catch (FileNotFoundException e) {
            return new ArrayList<>();
        }
        repositories.removeIf(BitbucketServerRepository::isArchived);
        repositories.sort(Comparator.comparing(BitbucketServerRepository::getRepositoryName));

        return repositories;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public List<BitbucketServerRepository> getRepositories() throws IOException, InterruptedException {
        return getRepositories(null);
    }

    @Override
    public boolean isPrivate() throws IOException, InterruptedException {
        return getRepository().isPrivate();
    }

    private <V> List<V> getResources(UriTemplate template, Class<? extends PagedApiResponse<V>> clazz) throws IOException, InterruptedException {
        List<V> resources = new ArrayList<>();

        PagedApiResponse<V> page;
        Integer pageNumber = 0;
        Integer limit = DEFAULT_PAGE_LIMIT;
        do {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            String url = template //
                    .set("start", pageNumber) //
                    .set("limit", limit) //
                    .expand();
            String response = getRequest(url);
            try {
                page = JsonParser.toJava(response, clazz);
            } catch (IOException e) {
                throw new IOException("I/O error when parsing response from URL: " + url, e);
            }
            resources.addAll(page.getValues());

            limit = page.getLimit();
            pageNumber = page.getNextPageStart();
        } while (!page.isLastPage());


        return resources;
    }

    private <V> V getResource(UriTemplate template, Class<? extends PagedApiResponse<V>> clazz, Predicate<V> filter) throws IOException, InterruptedException {

        PagedApiResponse<V> page;
        Integer pageNumber = 0;
        Integer limit = DEFAULT_PAGE_LIMIT;
        do {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            String url = template //
                .set("start", pageNumber) //
                .set("limit", limit) //
                .expand();
            String response = getRequest(url);
            try {
                page = JsonParser.toJava(response, clazz);
            } catch (IOException e) {
                throw new IOException("I/O error when parsing response from URL: " + url, e);
            }

            for(V item : page.getValues()) {
                if(filter.test(item)) {
                    return item;
                }
            }

            limit = page.getLimit();
            pageNumber = page.getNextPageStart();
        } while (!page.isLastPage());


        return null;
    }

    private BufferedImage getImageRequest(String path) throws IOException {
        try (InputStream inputStream = getRequestAsInputStream(path)) {
            int length = MAX_AVATAR_LENGTH;
            BufferedInputStream bis = new BufferedInputStream(inputStream, length);
            return ImageIO.read(bis);
        }
    }

    @Override
    protected HttpClientConnectionManager getConnectionManager() {
        return connectionManager;
    }

    @NonNull
    @Override
    protected CloseableHttpClient getClient() {
        return client;
    }

    @NonNull
    @Override
    protected HttpHost getHost() {
        String url = baseURL;
        try {
            // it's needed because the serverURL can contains a context root different than '/' and the HttpHost must contains only schema, host and port
            URL tmp = new URL(baseURL);
            String schema = tmp.getProtocol() == null ? "http" : tmp.getProtocol();
            return new HttpHost(tmp.getHost(), tmp.getPort(), schema);
        } catch (MalformedURLException e) {
            return HttpHost.create(url);
        }
    }

    @Override
    public Iterable<SCMFile> getDirectoryContent(BitbucketSCMFile directory) throws IOException, InterruptedException {
        List<SCMFile> files = new ArrayList<>();
        int start=0;
        String branchOrHash = directory.getHash().contains("+") ? directory.getRef() : directory.getHash();
        UriTemplate template = UriTemplate
                .fromTemplate(this.baseURL + API_BROWSE_PATH + "{&start,limit}")
                .set("owner", getUserCentricOwner())
                .set("repo", repositoryName)
                .set("path", directory.getPath().split(Operator.PATH.getSeparator()))
                .set("at", branchOrHash)
                .set("start", start)
                .set("limit", 500);
        String url = template.expand();
        String response = getRequest(url);
        Map<String,Object> content = JsonParser.mapper.readValue(response, new TypeReference<Map<String,Object>>(){});
        Map page = (Map) content.get("children");
        List<Map> values = (List<Map>) page.get("values");
        collectFileAndDirectories(directory, values, files);
        while (!(boolean)page.get("isLastPage")){
            start += (int) content.get("size");
            url = template
                    .set("start", start)
                    .expand();
            response = getRequest(url);
            content = JsonParser.mapper.readValue(response, new TypeReference<Map<String,Object>>(){});
            page = (Map) content.get("children");
        }
        return files;
    }

    private void collectFileAndDirectories(BitbucketSCMFile parent, List<Map> values, List<SCMFile> files) {
        for(Map file:values) {
            String type = (String) file.get("type");
            List<String> components = (List<String>) ((Map)file.get("path")).get("components");
            SCMFile.Type fileType = null;
            if (type.equals("FILE")) {
                fileType = SCMFile.Type.REGULAR_FILE;
            } else if(type.equals("DIRECTORY")){
                fileType = SCMFile.Type.DIRECTORY;
            }
            if (!components.isEmpty() && fileType != null) {
                // revision is set to null as fetched values from server API do not give us revision hash
                // Later on hash is not needed anyways when file content is fetched from server API
                files.add(new BitbucketSCMFile(parent, components.get(0), fileType, null));
            }
        }
    }

    @Override
    public InputStream getFileContent(BitbucketSCMFile file) throws IOException, InterruptedException {
        List<String> lines = new ArrayList<>();
        int start=0;
        String branchOrHash = file.getHash().contains("+") ? file.getRef() : file.getHash();
        UriTemplate template = UriTemplate
                .fromTemplate(this.baseURL + API_BROWSE_PATH + "{&start,limit}")
                .set("owner", getUserCentricOwner())
                .set("repo", repositoryName)
                .set("path", file.getPath().split(Operator.PATH.getSeparator()))
                .set("at", branchOrHash)
                .set("start", start)
                .set("limit", 500);
        String url = template.expand();
        String response = getRequest(url);
        Map<String,Object> content = collectLines(response, lines);

        while(!(boolean)content.get("isLastPage")){
            start += (int) content.get("size");
            url = template
                    .set("start", start)
                    .expand();
            response = getRequest(url);
            content = collectLines(response, lines);
        }
        return IOUtils.toInputStream(StringUtils.join(lines,'\n'), StandardCharsets.UTF_8);
    }

    private Map<String,Object> collectLines(String response, final List<String> lines) throws IOException {
        Map<String,Object> content = JsonParser.mapper.readValue(response, new TypeReference<Map<String,Object>>(){});
        List<Map<String, String>> lineMap = (List<Map<String, String>>) content.get("lines");
        for(Map<String,String> line: lineMap){
            String text = line.get("text");
            if(text != null){
                lines.add(text);
            }
        }
        return content;
    }

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    @NonNull
    @Override
    public SCMFile getFile(@NonNull BitbucketSCMFile file) throws IOException, InterruptedException {
        String branchOrHash = file.getHash().contains("+") ? file.getRef() : file.getHash();
        String url = UriTemplate.fromTemplate(this.baseURL + API_BROWSE_PATH + "{&type,blame}")
                .set("owner", getUserCentricOwner())
                .set("repo", repositoryName)
                .set("path", file.getPath().split(Operator.PATH.getSeparator()))
                .set("at", branchOrHash)
                .set("type", true)
                .set("blame", false)
                .expand();
        Type type = Type.OTHER;
        try {
            String response = getRequest(url);
            JsonNode typeNode = JsonParser.mapper.readTree(response).path("type");
            if (!typeNode.isMissingNode() && !typeNode.isNull()) {
                String responseType = typeNode.asText();
                if ("FILE".equals(responseType)) {
                    type = Type.REGULAR_FILE;
                    // type = Type.LINK; does not matter if getFileContent on the linked file/directory returns the content
                } else if ("DIRECTORY".equals(responseType)) {
                    type = Type.DIRECTORY;
                } else if ("SUBMODULE".equals(responseType)) {
                    type = Type.OTHER; // NOSONAR
                }
            }
        } catch (FileNotFoundException e) {
            type = Type.NONEXISTENT;
        }
        return new BitbucketSCMFile((BitbucketSCMFile) file.parent(), file.getName(), type, file.getHash());
    }

}
