/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc., bguerin
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
package com.cloudbees.jenkins.plugins.bitbucket.client;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCloudWorkspace;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketException;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketTeam;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudBranch;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudCommit;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketPullRequestCommits;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketPullRequestValue;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketPullRequests;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketRepositoryHook;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketRepositoryHooks;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketRepositorySource;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.PaginatedBitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.UserRoleInRepository;
import com.cloudbees.jenkins.plugins.bitbucket.filesystem.BitbucketSCMFile;
import com.cloudbees.jenkins.plugins.bitbucket.impl.client.AbstractBitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.impl.credentials.BitbucketUsernamePasswordAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketApiUtils;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.JsonParser;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.damnhandy.uri.template.UriTemplate;
import com.damnhandy.uri.template.impl.Operator;
import com.fasterxml.jackson.core.type.TypeReference;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import jenkins.scm.api.SCMFile;
import jenkins.scm.impl.avatars.AvatarImage;
import org.apache.commons.lang.StringUtils;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.message.BasicNameValuePair;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.apache.commons.lang.StringUtils.abbreviate;

public class BitbucketCloudApiClient extends AbstractBitbucketApi implements BitbucketApi {

    private static final HttpHost API_HOST = BitbucketApiUtils.toHttpHost("https://api.bitbucket.org");
    private static final String V2_API_BASE_URL = "https://api.bitbucket.org/2.0/repositories";
    private static final String V2_WORKSPACES_API_BASE_URL = "https://api.bitbucket.org/2.0/workspaces";
    private static final String REPO_URL_TEMPLATE = V2_API_BASE_URL + "{/owner,repo}";
    // Limit images to 16k
    private static final int MAX_AVATAR_LENGTH = 16384;
    private static final int MAX_PAGE_LENGTH = 100;
    protected static final HttpClientConnectionManager connectionManager = connectionManager();

    private final CloseableHttpClient client;
    private final String owner;
    private final String projectKey;
    private final String repositoryName;
    private final boolean enableCache;
    private static final Cache<String, BitbucketTeam> cachedTeam = new Cache<>(6, HOURS);
    private static final Cache<String, List<BitbucketCloudRepository>> cachedRepositories = new Cache<>(3, HOURS);
    private static final Cache<String, BitbucketCloudCommit> cachedCommits = new Cache<>(24, HOURS);
    private transient BitbucketRepository cachedRepository;
    private transient String cachedDefaultBranch;

    private static HttpClientConnectionManager connectionManager() {
        try {
            int connectTimeout = Integer.getInteger("http.connect.timeout", 10);
            int socketTimeout = Integer.getInteger("http.socket.timeout", 60);

            ConnectionConfig connCfg = ConnectionConfig.custom()
                    .setConnectTimeout(connectTimeout, TimeUnit.SECONDS)
                    .setSocketTimeout(socketTimeout, TimeUnit.SECONDS)
                    .build();

            SocketConfig socketConfig = SocketConfig.custom()
                    .setSoTimeout(60, TimeUnit.SECONDS)
                    .build();

            return PoolingHttpClientConnectionManagerBuilder.create()
                    .setMaxConnPerRoute(20)
                    .setMaxConnTotal(22)
                    .setDefaultConnectionConfig(connCfg)
                    .setSocketConfigResolver(host -> host.getTargetHost().equals(API_HOST) ? socketConfig : SocketConfig.DEFAULT)
                    .build();
        } catch (Exception e) {
            // in case of exception this avoids ClassNotFoundError which prevents the classloader from loading this class again
            return null;
        }
    }

    public static List<String> stats() {
        List<String> stats = new ArrayList<>();
        stats.add("Team: " + cachedTeam.stats().toString());
        stats.add("Repositories : " + cachedRepositories.stats().toString());
        stats.add("Commits: " + cachedCommits.stats().toString());
        return stats;
    }

    public static void clearCaches() {
        cachedTeam.evictAll();
        cachedRepositories.evictAll();
        cachedCommits.evictAll();
    }

    @Deprecated
    public BitbucketCloudApiClient(boolean enableCache, int teamCacheDuration, int repositoriesCacheDuration,
                                   String owner, String repositoryName, StandardUsernamePasswordCredentials credentials) {
        this(enableCache, teamCacheDuration, repositoriesCacheDuration, owner, null, repositoryName,
                new BitbucketUsernamePasswordAuthenticator(credentials));
    }

    public BitbucketCloudApiClient(boolean enableCache, int teamCacheDuration, int repositoriesCacheDuration,
            String owner, String projectKey, String repositoryName, BitbucketAuthenticator authenticator) {
        super(authenticator);
        this.owner = owner;
        this.projectKey = projectKey;
        this.repositoryName = repositoryName;
        this.enableCache = enableCache;
        if (enableCache) {
            cachedTeam.setExpireDuration(teamCacheDuration, MINUTES);
            cachedRepositories.setExpireDuration(repositoriesCacheDuration, MINUTES);
        }
        this.client = super.setupClientBuilder("bitbucket.org").build();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getOwner() {
        return owner;
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
    public List<BitbucketPullRequestValue> getPullRequests() throws InterruptedException, IOException {
        List<BitbucketPullRequestValue> pullRequests = new ArrayList<>();

        // we can not use the default max pagelen also if documented
        // https://developer.atlassian.com/bitbucket/api/2/reference/resource/repositories/%7Busername%7D/%7Brepo_slug%7D/pullrequests#get
        // so because with values greater than 50 the API returns HTTP 400
        int pageLen = 50;
        UriTemplate template = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/pullrequests{?page,pagelen}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("pagelen", pageLen);

        BitbucketPullRequests page;
        int pageNumber = 1;
        do {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            String url = template //
                    .set("page", pageNumber++) //
                    .expand();
            String response = getRequest(url);
            try {
                page = JsonParser.toJava(response, BitbucketPullRequests.class);
            } catch (IOException e) {
                throw new IOException("I/O error when parsing response from URL: " + url, e);
            }
            pullRequests.addAll(page.getValues());
        } while (page.getNext() != null);

        // PRs with missing destination branch are invalid and should be ignored.
        pullRequests.removeIf(this::shouldIgnore);

        for (BitbucketPullRequestValue pullRequest : pullRequests) {
            setupClosureForPRBranch(pullRequest);
        }

        return pullRequests;
    }

    /**
     * PRs with missing source / destination branch are invalid and should be ignored.
     *
     * @param pr a {@link BitbucketPullRequest}
     * @return whether the PR should be ignored
     */
    private boolean shouldIgnore(BitbucketPullRequest pr) {
        return pr.getSource().getRepository() == null
            || pr.getSource().getCommit() == null
            || pr.getDestination().getBranch() == null
            || pr.getDestination().getCommit() == null;
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

    private void setupClosureForPRBranch(BitbucketPullRequestValue pullRequest) {
        BitbucketCloudBranch branch = pullRequest.getSource().getBranch();
        if (branch != null) {
            branch.setCommitClosure(new CommitClosure(branch.getRawNode()));
        }
        branch = pullRequest.getDestination().getBranch();
        if (branch != null) {
            branch.setCommitClosure(new CommitClosure(branch.getRawNode()));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public BitbucketPullRequest getPullRequestById(@NonNull Integer id) throws IOException, InterruptedException {
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/pullrequests{/id}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("id", id)
                .expand();
        String response = getRequest(url);
        try {
            BitbucketPullRequestValue pr = JsonParser.toJava(response, BitbucketPullRequestValue.class);
            setupClosureForPRBranch(pr);
            return pr;
        } catch (IOException e) {
            throw new IOException("I/O error when parsing response from URL: " + url, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public BitbucketRepository getRepository() throws IOException, InterruptedException {
        if (repositoryName == null) {
            throw new UnsupportedOperationException("Cannot get a repository from an API instance that is not associated with a repository");
        }
        if (!enableCache || cachedRepository == null) {
            String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE)
                    .set("owner", owner)
                    .set("repo", repositoryName)
                    .expand();
            String response = getRequest(url);
            try {
                cachedRepository =  JsonParser.toJava(response, BitbucketCloudRepository.class);
            } catch (IOException e) {
                throw new IOException("I/O error when parsing response from URL: " + url, e);
            }
        }
        return cachedRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postCommitComment(@NonNull String hash, @NonNull String comment) throws IOException, InterruptedException {
        String path = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/commit{/hash}/build")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("hash", hash)
                .expand();
        try {
            postRequest(path, Collections.singletonList(new BasicNameValuePair("content", comment)));
        } catch (UnsupportedEncodingException e) {
            throw e;
        } catch (IOException e) {
            throw new IOException("Cannot comment on commit, url: " + path, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkPathExists(@NonNull String branchOrHash, @NonNull String path)
            throws IOException, InterruptedException {
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/src{/branchOrHash,path*}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("branchOrHash", branchOrHash)
                .set("path", path.split(Operator.PATH.getSeparator()))
                .expand();
        int status = headRequestStatus(url);
        if (HttpStatus.SC_OK == status) {
            return true;
        } else if (HttpStatus.SC_NOT_FOUND == status) {
            return false;
        } else if (HttpStatus.SC_FORBIDDEN == status) {
            // Needs to skip over the branch if there are permissions issues but let you know in the logs
            logger.log(Level.FINE, "You currently do not have permissions to pull from repo: {0} at branch {1}", new Object[]{repositoryName, branchOrHash});
            return false;
        } else {
            throw new IOException("Communication error for url: " + path + " status code: " + status);
        }
    }

    /**
     * {@inheritDoc}
     */
    @CheckForNull
    @Override
    public String getDefaultBranch() throws IOException, InterruptedException {
        if (!enableCache || cachedDefaultBranch == null) {
            String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/{?fields}")
                    .set("owner", owner)
                    .set("repo", repositoryName)
                    .set("fields", "mainbranch.name")
                    .expand();
            String response;
            try {
                response = getRequest(url);
            } catch (FileNotFoundException e) {
                logger.log(Level.FINE, "Could not find default branch for {0}/{1}",
                        new Object[]{this.owner, this.repositoryName});
                return null;
            }
            Map resp = JsonParser.toJava(response, Map.class);
            Map mainbranch = (Map) resp.get("mainbranch");
            if (mainbranch != null) {
                cachedDefaultBranch = (String) mainbranch.get("name");
            }
        }
        return cachedDefaultBranch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BitbucketCloudBranch getTag(@NonNull String tagName) throws IOException, InterruptedException {
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/refs/tags/{name}")
            .set("owner", owner)
            .set("repo", repositoryName)
            .set("name", tagName)
            .expand();
        String response = getRequest(url);
        try {
            return getSingleBranch(response);
        } catch (IOException e) {
            throw new IOException("I/O error when parsing response from URL: " + url, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<BitbucketCloudBranch> getTags() throws IOException, InterruptedException {
        return getBranchesByRef("/refs/tags");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BitbucketCloudBranch getBranch(@NonNull String branchName) throws IOException, InterruptedException {
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/refs/branches/{name}")
            .set("owner", owner)
            .set("repo", repositoryName)
            .set("name", branchName)
            .expand();
        String response = getRequest(url);
        try {
            return getSingleBranch(response);
        } catch (IOException e) {
            throw new IOException("I/O error when parsing response from URL: " + url, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<BitbucketCloudBranch> getBranches() throws IOException, InterruptedException {
        return getBranchesByRef("/refs/branches");
    }

    public List<BitbucketCloudBranch> getBranchesByRef(String nodePath) throws IOException, InterruptedException {
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + nodePath + "{?pagelen}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("pagelen", MAX_PAGE_LENGTH)
                .expand();
        String response = getRequest(url);
        try {
            return getAllBranches(response);
        } catch (IOException e) {
            throw new IOException("I/O error when parsing response from URL: " + url, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @CheckForNull
    public BitbucketCommit resolveCommit(@NonNull String hash) throws IOException, InterruptedException {
        final String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/commit/{hash}")
            .set("owner", owner)
            .set("repo", repositoryName)
            .set("hash", hash)
            .expand();

        Callable<BitbucketCloudCommit> request = () -> {
            String response;
            try {
                response = getRequest(url);
            } catch (FileNotFoundException e) {
                return null;
            }
            try {
                return JsonParser.toJava(response, BitbucketCloudCommit.class);
            } catch (IOException e) {
                throw new IOException("I/O error when parsing response from URL: " + url, e);
            }
        };

        try {
            if (enableCache) {
                return cachedCommits.get(hash, request);
            } else {
                return request.call();
            }
        } catch (IOException | InterruptedException ex) {
            throw ex;
        }
        catch (Exception ex) {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String resolveSourceFullHash(@NonNull BitbucketPullRequest pull) throws IOException, InterruptedException {
        return resolveCommit(pull).getHash();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public BitbucketCommit resolveCommit(@NonNull BitbucketPullRequest pull) throws IOException, InterruptedException {
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/pullrequests/{pullId}/commits{?fields,pagelen}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("pullId", pull.getId())
                .set("fields", "values.hash,values.author.raw,values.date,values.message")
                .set("pagelen", 1)
                .expand();
        String response = getRequest(url);
        try {
            BitbucketPullRequestCommits commits = JsonParser.toJava(response, BitbucketPullRequestCommits.class);
            return Util.fixNull(commits.getValues()).stream()
                .findFirst()
                .orElseThrow(() -> new BitbucketException("Could not determine commit for pull request " + pull.getId()));
        } catch (IOException e) {
            throw new IOException("I/O error when parsing response from URL: " + url, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerCommitWebHook(@NonNull BitbucketWebHook hook) throws IOException, InterruptedException {
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/hooks")
                .set("owner", owner)
                .set("repo", repositoryName)
                .expand();
        postRequest(url, JsonParser.toJson(hook));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateCommitWebHook(@NonNull BitbucketWebHook hook) throws IOException, InterruptedException {
        String url = UriTemplate
                .fromTemplate(REPO_URL_TEMPLATE + "/hooks/{hook}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("hook", hook.getUuid())
                .expand();
        putRequest(url, JsonParser.toJson(hook));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeCommitWebHook(@NonNull BitbucketWebHook hook) throws IOException, InterruptedException {
        if (StringUtils.isBlank(hook.getUuid())) {
            throw new BitbucketException("Hook UUID required");
        }
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/hooks/{uuid}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("uuid", hook.getUuid())
                .expand();
        deleteRequest(url);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<BitbucketRepositoryHook> getWebHooks() throws IOException, InterruptedException {
        List<BitbucketRepositoryHook> repositoryHooks = new ArrayList<>();
        int pageNumber = 1;
        UriTemplate template = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/hooks{?page,pagelen}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("page", pageNumber)
                .set("pagelen", MAX_PAGE_LENGTH);
        String url = template.expand();
        try {
            String response = getRequest(url);
            BitbucketRepositoryHooks page = parsePaginatedRepositoryHooks(response);
            repositoryHooks.addAll(page.getValues());
            while (page.getNext() != null) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                pageNumber++;
                response = getRequest(url = template.set("page", pageNumber).expand());
                page = parsePaginatedRepositoryHooks(response);
                repositoryHooks.addAll(page.getValues());
            }
            return repositoryHooks;
        } catch (IOException e) {
            throw new IOException("I/O error when parsing response from URL: " + url, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postBuildStatus(@NonNull BitbucketBuildStatus status) throws IOException, InterruptedException {
        BitbucketBuildStatus newStatus = new BitbucketBuildStatus(status);
        newStatus.setName(abbreviate(newStatus.getName(), 255));

        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/commit/{hash}/statuses/build")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("hash", newStatus.getHash())
                .expand();
        postRequest(url, JsonParser.toJson(newStatus));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isPrivate() throws IOException, InterruptedException {
        return getRepository().isPrivate();
    }

    private BitbucketRepositoryHooks parsePaginatedRepositoryHooks(String response) throws IOException {
        BitbucketRepositoryHooks parsedResponse;
        parsedResponse = JsonParser.toJava(response, BitbucketRepositoryHooks.class);
        return parsedResponse;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @CheckForNull
    public BitbucketTeam getTeam() throws IOException {
        final String url = UriTemplate.fromTemplate(V2_WORKSPACES_API_BASE_URL + "{/owner}")
                .set("owner", owner)
                .expand();

        Callable<BitbucketTeam> request = () -> {
            try {
                String response = getRequest(url);
                return JsonParser.toJava(response, BitbucketCloudWorkspace.class);
            } catch (FileNotFoundException e) {
                return null;
            } catch (IOException e) {
                throw new IOException("I/O error when parsing response from URL: " + url, e);
            }
        };

        try {
            if (enableCache) {
                return cachedTeam.get(owner, request);
            } else {
                return request.call();
            }
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated(since = "935.0.0", forRemoval = true)
    @Override
    @CheckForNull
    public AvatarImage getTeamAvatar() throws IOException {
        final BitbucketTeam team = getTeam();
        return getAvatar(team == null ? null : team.getAvatar());
    }

    @Override
    @CheckForNull
    public AvatarImage getAvatar(@CheckForNull String url) throws IOException {
        if (url != null) {
            try {
                BufferedImage avatar = getImageRequest(url);
                return new AvatarImage(avatar, System.currentTimeMillis());
            } catch (FileNotFoundException e) {
                logger.log(Level.FINE, "Failed to get avatar from URL {0}", url);
            } catch (IOException e) {
                throw new IOException("I/O error when parsing response from URL: " + url, e);
            }
        }
        return AvatarImage.EMPTY;
    }

    /**
     * The role parameter only makes sense when the request is authenticated, so
     * if there is no auth information ({@link #getAuthenticator()}) the role will be omitted.
     */
    @NonNull
    @Override
    public List<BitbucketCloudRepository> getRepositories(@CheckForNull UserRoleInRepository role)
            throws InterruptedException, IOException {
        StringBuilder cacheKey = new StringBuilder();
        cacheKey.append(owner);

        if (getAuthenticator() != null) {
            cacheKey.append("::").append(getAuthenticator().getId());
        } else {
            cacheKey.append("::<anonymous>");
        }

        final UriTemplate template = UriTemplate.fromTemplate(V2_API_BASE_URL + "{/owner}{?role,page,pagelen,q}")
                .set("owner", owner)
                .set("pagelen", MAX_PAGE_LENGTH);
        if (StringUtils.isNotBlank(projectKey)) {
            template.set("q", "project.key=" + "\"" + projectKey + "\""); // q=project.key="<projectKey>"
            cacheKey.append("::").append(projectKey);
        } else {
            cacheKey.append("::<undefined>");
        }
        if (role != null &&  getAuthenticator() != null) {
            template.set("role", role.getId());
            cacheKey.append("::").append(role.getId());
        } else {
            cacheKey.append("::<undefined>");
        }

        Callable<List<BitbucketCloudRepository>> request = () -> {
            List<BitbucketCloudRepository> repositories = new ArrayList<>();
            Integer pageNumber = 1;
            String url, response;
            PaginatedBitbucketRepository page;
            do {
                response = getRequest(url = template.set("page", pageNumber).expand());
                try {
                    page = JsonParser.toJava(response, PaginatedBitbucketRepository.class);
                    repositories.addAll(page.getValues());
                } catch (IOException e) {
                    throw new IOException("I/O error when parsing response from URL: " + url, e);
                }
                pageNumber++;
            } while (page.getNext() != null);
            repositories.sort(Comparator.comparing(BitbucketCloudRepository::getRepositoryName));
            return repositories;
        };
        try {
            if (enableCache) {
                return cachedRepositories.get(cacheKey.toString(), request);
            } else {
                return request.call();
            }
        } catch (Exception ex) {
            throw new IOException("Error while loading repositories from cache", ex);
        }
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public List<BitbucketCloudRepository> getRepositories() throws IOException, InterruptedException {
        return getRepositories(null);
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
    protected HttpHost getHost() {
        return API_HOST;
    }

    @NonNull
    @Override
    protected CloseableHttpClient getClient() {
        return client;
    }

    private List<BitbucketCloudBranch> getAllBranches(String response) throws IOException {
        List<BitbucketCloudBranch> branches = new ArrayList<>();
        BitbucketCloudPage<BitbucketCloudBranch> page = JsonParser.mapper.readValue(response,
                new TypeReference<BitbucketCloudPage<BitbucketCloudBranch>>(){});
        branches.addAll(page.getValues());
        while (!page.isLastPage()){
            response = getRequest(page.getNext());
            page = JsonParser.mapper.readValue(response,
                    new TypeReference<BitbucketCloudPage<BitbucketCloudBranch>>(){});
            branches.addAll(page.getValues());
        }

        // Filter the inactive branches out
        List<BitbucketCloudBranch> activeBranches = new ArrayList<>();
        for (BitbucketCloudBranch branch: branches) {
            if (branch.isActive()) {
                activeBranches.add(branch);
            }
        }

        return activeBranches;
    }

    private BitbucketCloudBranch getSingleBranch(String response) throws IOException {
        return JsonParser.mapper.readValue(response, new TypeReference<BitbucketCloudBranch>(){});
    }

    @Override
    public Iterable<SCMFile> getDirectoryContent(final BitbucketSCMFile parent) throws IOException, InterruptedException {
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/src{/branchOrHash,path}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("branchOrHash", parent.getHash())
                .set("path", parent.getPath())
                .expand();
        List<SCMFile> result = new ArrayList<>();

        String pageURL = url;
        BitbucketCloudPage<BitbucketRepositorySource> page;
        do {
            String response = getRequest(pageURL);
            page = JsonParser.mapper.readValue(response, new TypeReference<BitbucketCloudPage<BitbucketRepositorySource>>(){});

            for(BitbucketRepositorySource source : page.getValues()){
                result.add(source.toBitbucketSCMFile(parent));
            }
            pageURL = page.getNext();
        } while (!page.isLastPage());
        return result;
    }

    @Override
    public InputStream getFileContent(@NonNull BitbucketSCMFile file) throws IOException, InterruptedException {
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/src{/branchOrHash,path}{?at}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("branchOrHash", file.getHash())
                .set("path", file.getPath())
                .set("at", file.getRef())
                .expand();
        return getRequestAsInputStream(url);
    }

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    @NonNull
    @Override
    public SCMFile getFile(@NonNull BitbucketSCMFile file) throws IOException, InterruptedException {
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/src{/branchOrHash,path}?format=meta")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("branchOrHash", file.getHash() != null ? file.getHash() : file.getRef())
                .set("path", file.getPath())
                .expand();
        String response = getRequest(url);
        BitbucketRepositorySource src = JsonParser.mapper.readValue(response, BitbucketRepositorySource.class);
        return src.toBitbucketSCMFile((BitbucketSCMFile) file.parent());
    }
}
