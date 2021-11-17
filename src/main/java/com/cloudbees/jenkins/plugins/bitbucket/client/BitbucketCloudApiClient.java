/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 * Copyright (c) 2017-2018, bguerin
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

import com.cloudbees.jenkins.plugins.bitbucket.JsonParser;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCloudWorkspace;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketException;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryProtocol;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRequestException;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketTeam;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.api.credentials.BitbucketUsernamePasswordAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.avatars.AvatarCacheSource.AvatarImage;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudBranch;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudCommit;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketPullRequestCommit;
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
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.damnhandy.uri.template.UriTemplate;
import com.damnhandy.uri.template.impl.Operator;
import com.fasterxml.jackson.core.type.TypeReference;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ProxyConfiguration;
import hudson.Util;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.StandardHttpRequestRetryHandler;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.ProtectedExternally;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class BitbucketCloudApiClient implements BitbucketApi {

    private static final Logger LOGGER = Logger.getLogger(BitbucketCloudApiClient.class.getName());
    private static final HttpHost API_HOST = HttpHost.create("https://api.bitbucket.org");
    private static final String V2_API_BASE_URL = "https://api.bitbucket.org/2.0/repositories";
    private static final String V2_WORKSPACES_API_BASE_URL = "https://api.bitbucket.org/2.0/workspaces";
    private static final String REPO_URL_TEMPLATE = V2_API_BASE_URL + "{/owner,repo}";
    private static final int API_RATE_LIMIT_CODE = 429;
    // Limit images to 16k
    private static final int MAX_AVATAR_LENGTH = 16384;
    private static final int MAX_PAGE_LENGTH = 100;
    private static final PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
    private CloseableHttpClient client;
    private HttpClientContext context;
    private final String owner;
    private final String repositoryName;
    private final boolean enableCache;
    private final BitbucketAuthenticator authenticator;
    private static final Cache<String, BitbucketTeam> cachedTeam = new Cache<>(6, HOURS);
    private static final Cache<String, AvatarImage> cachedAvatar = new Cache<>(6, HOURS);
    private static final Cache<String, List<BitbucketCloudRepository>> cachedRepositories = new Cache<>(3, HOURS);
    private transient BitbucketRepository cachedRepository;
    private transient String cachedDefaultBranch;

    static {
        connectionManager.setDefaultMaxPerRoute(20);
        connectionManager.setMaxTotal(22);
        connectionManager.setSocketConfig(API_HOST, SocketConfig.custom().setSoTimeout(60 * 1000).build());
    }

    public static List<String> stats() {
        List<String> stats = new ArrayList<>();
        stats.add("Team: " + cachedTeam.stats().toString());
        stats.add("Repositories : " + cachedRepositories.stats().toString());
        return stats;
    }

    public static void clearCaches() {
        cachedTeam.evictAll();
        cachedRepositories.evictAll();
    }

    @Deprecated
    public BitbucketCloudApiClient(boolean enableCache, int teamCacheDuration, int repositoriesCacheDuration,
                                   String owner, String repositoryName, StandardUsernamePasswordCredentials credentials) {
        this(enableCache, teamCacheDuration, repositoriesCacheDuration, owner, repositoryName,
                new BitbucketUsernamePasswordAuthenticator(credentials));
    }

    public BitbucketCloudApiClient(boolean enableCache, int teamCacheDuration, int repositoriesCacheDuration,
            String owner, String repositoryName, BitbucketAuthenticator authenticator) {
        this.authenticator = authenticator;
        this.owner = owner;
        this.repositoryName = repositoryName;
        this.enableCache = enableCache;
        if (enableCache) {
            cachedTeam.setExpireDuration(teamCacheDuration, MINUTES);
            cachedRepositories.setExpireDuration(repositoriesCacheDuration, MINUTES);
        }

        // Create Http client
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();

        httpClientBuilder.setKeepAliveStrategy((__, ___) -> MILLISECONDS.convert(5, SECONDS));
        httpClientBuilder.setConnectionManager(connectionManager);
        httpClientBuilder.setConnectionManagerShared(true);
        httpClientBuilder.setRetryHandler(new StandardHttpRequestRetryHandler());

        if (authenticator != null) {
            authenticator.configureBuilder(httpClientBuilder);

            context = HttpClientContext.create();
            authenticator.configureContext(context, API_HOST);
        }

        setClientProxyParams("bitbucket.org", httpClientBuilder);

        this.client = httpClientBuilder.build();
    }

    @Override
    protected void finalize() throws Throwable {
        if (client != null) {
            client.close();
        }

        super.finalize();
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
    public String getRepositoryUri(@NonNull BitbucketRepositoryProtocol protocol,
                                   @CheckForNull String cloneLink,
                                   @NonNull String owner,
                                   @NonNull String repository) {
        // ignore port override on Cloud
        switch (protocol) {
            case HTTP:
                if (authenticator != null) {
                    String username = authenticator.getUserUri();
                    if (!username.isEmpty()) {
                        return "https://" + username + "@bitbucket.org/" + owner + "/" + repository + ".git";
                    }
                }
                return "https://bitbucket.org/" + owner + "/" + repository + ".git";
            case SSH:
                return "git@bitbucket.org:" + owner + "/" + repository + ".git";
            default:
                throw new IllegalArgumentException("Unsupported repository protocol: " + protocol);
        }
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

    @Deprecated
    @CheckForNull
    public String getLogin() {
        if (authenticator != null) {
            return authenticator.getId();
        }
        return null;
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
            LOGGER.log(Level.FINE, "You currently do not have permissions to pull from repo: {0} at branch {1}", new Object[]{repositoryName, branchOrHash});
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
                LOGGER.log(Level.FINE, "Could not find default branch for {0}/{1}",
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
    @NonNull
    @Override
    public List<BitbucketCloudBranch> getTags() throws IOException, InterruptedException {
        return getBranchesByRef("/refs/tags");
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
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/commit/{hash}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("hash", hash)
                .expand();
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
            for (BitbucketPullRequestCommit commit : Util.fixNull(commits.getValues())) {
                return commit;
            }
            throw new BitbucketException("Could not determine commit for pull request " + pull.getId());
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
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/commit/{hash}/statuses/build")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("hash", status.getHash())
                .expand();
        postRequest(url, JsonParser.toJson(status));
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
    public BitbucketTeam getTeam() throws IOException, InterruptedException {
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
    @Override
    @CheckForNull
    public AvatarImage getTeamAvatar() throws IOException, InterruptedException {
        try {
            final BitbucketTeam team = getTeam();
            final String url = (team!=null) ? team.getLink("avatar") : null;
            if (url == null) {
                return AvatarImage.EMPTY;
            }

            Callable<AvatarImage> request = () -> {
                try {
                    BufferedImage avatar = getImageRequest(url);
                    return new AvatarImage(avatar, System.currentTimeMillis());
                } catch (FileNotFoundException e) {
                    LOGGER.log(Level.FINE, "Failed to get avatar for team {0} from URL: " + url,
                            team.getName());
                } catch (IOException e) {
                    throw new IOException("I/O error when parsing response from URL: " + url, e);
                }
                return null;
            };

            try {
                if (enableCache) {
                    return cachedAvatar.get(owner, request);
                } else {
                    return request.call();
                }
            } catch (Exception ex) {
                return null;
            }
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Unexpected exception while loading team avatar: "+ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * The role parameter only makes sense when the request is authenticated, so
     * if there is no auth information ({@link #authenticator}) the role will be omitted.
     */
    @NonNull
    @Override
    public List<BitbucketCloudRepository> getRepositories(@CheckForNull UserRoleInRepository role)
            throws InterruptedException, IOException {
        StringBuilder cacheKey = new StringBuilder();
        cacheKey.append(owner);

        if (authenticator != null) {
            cacheKey.append("::").append(authenticator.getId());
        } else {
            cacheKey.append("::<anonymous>");
        }

        final UriTemplate template = UriTemplate.fromTemplate(V2_API_BASE_URL + "{/owner}{?role,page,pagelen}")
                .set("owner", owner)
                .set("pagelen", MAX_PAGE_LENGTH);
        if (role != null &&  authenticator != null) {
            template.set("role", role.getId());
            cacheKey.append("::").append(role.getId());
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

    private void setClientProxyParams(String host, HttpClientBuilder builder) {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        ProxyConfiguration proxyConfig = null;
        if (jenkins != null) {
            proxyConfig = jenkins.proxy;
        }

        Proxy proxy = Proxy.NO_PROXY;
        if (proxyConfig != null) {
            proxy = proxyConfig.createProxy(host);
        }

        if (proxy.type() != Proxy.Type.DIRECT) {
            final InetSocketAddress proxyAddress = (InetSocketAddress) proxy.address();
            LOGGER.fine("Jenkins proxy: " + proxy.address());
            HttpHost proxyHttpHost = new HttpHost(proxyAddress.getHostName(), proxyAddress.getPort());
            builder.setProxy(proxyHttpHost);
            String username = proxyConfig.getUserName();
            String password = proxyConfig.getPassword();
            if (username != null && !"".equals(username.trim())) {
                LOGGER.fine("Using proxy authentication (user=" + username + ")");
                if (context == null) {
                    // may have been already set in com.cloudbees.jenkins.plugins.bitbucket.api.credentials.BitbucketUsernamePasswordAuthenticator.configureContext(HttpClientContext, HttpHost)
                    context = HttpClientContext.create();
                }
                CredentialsProvider credentialsProvider = context.getCredentialsProvider();
                if (credentialsProvider == null) {
                    credentialsProvider = new BasicCredentialsProvider();
                    // may have been already set in com.cloudbees.jenkins.plugins.bitbucket.api.credentials.BitbucketUsernamePasswordAuthenticator.configureContext(HttpClientContext, HttpHost)
                    context.setCredentialsProvider(credentialsProvider);
                }
                credentialsProvider.setCredentials(new AuthScope(proxyHttpHost), new UsernamePasswordCredentials(username, password));
                AuthCache authCache = context.getAuthCache();
                if (authCache == null) {
                    authCache = new BasicAuthCache();
                    context.setAuthCache(authCache);
                }
                authCache.put(proxyHttpHost, new BasicScheme());
            }
        }
    }

    @Restricted(ProtectedExternally.class)
    protected CloseableHttpResponse executeMethod(HttpRequestBase httpMethod) throws InterruptedException, IOException {
        return executeMethod(API_HOST, httpMethod);
    }

    @Restricted(ProtectedExternally.class)
    protected CloseableHttpResponse executeMethod(HttpHost host, HttpRequestBase httpMethod) throws InterruptedException, IOException {
        HttpClientContext requestContext = null;
        if (API_HOST.equals(host)) {
            requestContext = context;
            if (authenticator != null) {
                authenticator.configureRequest(httpMethod);
            }
        }

        RequestConfig.Builder requestConfig = RequestConfig.custom();
        requestConfig.setConnectTimeout(10 * 1000);
        requestConfig.setConnectionRequestTimeout(60 * 1000);
        requestConfig.setSocketTimeout(60 * 1000);
        httpMethod.setConfig(requestConfig.build());

        CloseableHttpResponse response = client.execute(host, httpMethod, requestContext);
        while (response.getStatusLine().getStatusCode() == API_RATE_LIMIT_CODE) {
            release(httpMethod);
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            /*
                TODO: When bitbucket starts supporting rate limit expiration time, remove 5 sec wait and put code
                      to wait till expiration time is over. It should also fix the wait for ever loop.
             */
            LOGGER.fine("Bitbucket Cloud API rate limit reached, sleeping for 5 sec then retry...");
            Thread.sleep(5000);
            response = client.execute(host, httpMethod, requestContext);
        }
        return response;
    }

    /**
     * Caller's responsible to close the InputStream.
     */
    private InputStream getRequestAsInputStream(String path) throws IOException, InterruptedException {
        HttpGet httpget = new HttpGet(path);
        HttpHost host = null;

        // Extract host from URL, if present
        try {
            URI uri = new URI(path);
            if (uri.isAbsolute() && ! uri.isOpaque()) {
                host = HttpHost.create(""+uri.getScheme()+"://"+uri.getAuthority());
            }
        } catch (URISyntaxException ex) {
        }
        // Use default API Host otherwise
        if (host == null) {
            host = API_HOST;
        }

        try {
            CloseableHttpResponse response =  executeMethod(host, httpget);
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                EntityUtils.consume(response.getEntity());
                response.close();
                throw new FileNotFoundException("URL: " + path);
            }
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                String content = IOUtils.toString(response.getEntity().getContent());
                int statusCode = response.getStatusLine().getStatusCode();
                String status = response.getStatusLine().getReasonPhrase();
                EntityUtils.consume(response.getEntity());
                response.close();
                throw new BitbucketRequestException(statusCode,
                        "HTTP request error. Status: " + statusCode + ": " + status + ".\n" + content);
            }
            return new ClosingConnectionInputStream(response, httpget, connectionManager);
        } catch (BitbucketRequestException | FileNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new IOException("Communication error for url: " + path, e);
        }
    }

    private String getRequest(String path) throws IOException, InterruptedException {
        try (InputStream inputStream = getRequestAsInputStream(path)){
            return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        }
    }

    private BufferedImage getImageRequest(String path) throws IOException, InterruptedException {
        try (InputStream inputStream = getRequestAsInputStream(path)) {
            int length = MAX_AVATAR_LENGTH;
            BufferedInputStream bis = new BufferedInputStream(inputStream, length);
            BufferedImage image = ImageIO.read(bis);
            return image;
        }
    }

    private int headRequestStatus(String path) throws IOException, InterruptedException {
        HttpHead httpHead = new HttpHead(path);
        try(CloseableHttpResponse response = executeMethod(httpHead)) {
            EntityUtils.consume(response.getEntity());
            return response.getStatusLine().getStatusCode();
        } catch (IOException e) {
            throw new IOException("Communication error for url: " + path, e);
        } finally {
            release(httpHead);
        }
    }

    private void deleteRequest(String path) throws IOException, InterruptedException {
        HttpDelete httppost = new HttpDelete(path);
        try(CloseableHttpResponse response =  executeMethod(httppost)) {
            EntityUtils.consume(response.getEntity());
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                throw new FileNotFoundException("URL: " + path);
            }
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_NO_CONTENT) {
                throw new BitbucketRequestException(response.getStatusLine().getStatusCode(), "HTTP request error. Status: " + response.getStatusLine().getStatusCode() + ": " + response.getStatusLine().getReasonPhrase());
            }
        } catch (BitbucketRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new IOException("Communication error for url: " + path, e);
        } finally {
            release(httppost);
        }
    }

    private String doRequest(HttpRequestBase httppost) throws IOException, InterruptedException {
        try(CloseableHttpResponse response =  executeMethod(httppost)) {
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NO_CONTENT) {
                EntityUtils.consume(response.getEntity());
                // 204, no content
                return "";
            }
            String content = getResponseContent(response);
            EntityUtils.consume(response.getEntity());
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK && response.getStatusLine().getStatusCode() != HttpStatus.SC_CREATED) {
                throw new BitbucketRequestException(response.getStatusLine().getStatusCode(), "HTTP request error. Status: " + response.getStatusLine().getStatusCode() + ": " + response.getStatusLine().getReasonPhrase() + ".\n" + response);
            }
            return content;
        } catch (BitbucketRequestException e) {
            throw e;
        } catch (IOException e) {
            try {
                throw new IOException("Communication error for url: " + httppost.getURI(), e);
            } catch (IOException e1) {
                throw new IOException("Communication error", e);
            }
        } finally {
            release(httppost);
        }
    }

    private void release(HttpRequestBase method) {
        method.releaseConnection();
        connectionManager.closeExpiredConnections();
    }

    private String getResponseContent(CloseableHttpResponse response) throws IOException {
        String content;
        long len = response.getEntity().getContentLength();
        if (len == 0) {
            content = "";
        } else {
            ByteArrayOutputStream buf;
            if (len > 0 && len <= Integer.MAX_VALUE / 2) {
                buf = new ByteArrayOutputStream((int) len);
            } else {
                buf = new ByteArrayOutputStream();
            }
            try (InputStream is = response.getEntity().getContent()) {
                IOUtils.copy(is, buf);
            }
            content = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }
        return content;
    }

    private String putRequest(String path, String content) throws IOException, InterruptedException  {
        HttpPut request = new HttpPut(path);
        request.setEntity(new StringEntity(content, ContentType.create("application/json", "UTF-8")));
        return doRequest(request);
    }

    private String postRequest(String path, String content) throws IOException, InterruptedException {
        HttpPost httppost = new HttpPost(path);
        httppost.setEntity(new StringEntity(content, ContentType.create("application/json", "UTF-8")));
        return doRequest(httppost);
    }

    private String postRequest(String path, List<? extends NameValuePair> params) throws IOException, InterruptedException {
        HttpPost httppost = new HttpPost(path);
        httppost.setEntity(new UrlEncodedFormEntity(params));
        return doRequest(httppost);
    }

    private List<BitbucketCloudBranch> getAllBranches(String response) throws IOException, InterruptedException {
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

    @Override
    public Iterable<SCMFile> getDirectoryContent(final BitbucketSCMFile parent) throws IOException, InterruptedException {
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/src{/branchOrHash,path}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("branchOrHash", parent.getHash())
                .set("path", parent.getPath())
                .expand();
        List<SCMFile> result = new ArrayList<>();
        String response = getRequest(url);
        BitbucketCloudPage<BitbucketRepositorySource> page = JsonParser.mapper.readValue(response,
                new TypeReference<BitbucketCloudPage<BitbucketRepositorySource>>(){});

        for(BitbucketRepositorySource source:page.getValues()){
            result.add(source.toBitbucketScmFile(parent));
        }

        while (!page.isLastPage()){
            response = getRequest(page.getNext());
            page = JsonParser.mapper.readValue(response,
                    new TypeReference<BitbucketCloudPage<BitbucketRepositorySource>>(){});
            for(BitbucketRepositorySource source:page.getValues()){
                result.add(source.toBitbucketScmFile(parent));
            }
        }
        return result;
    }

    @Override
    public InputStream getFileContent(BitbucketSCMFile file) throws IOException, InterruptedException {
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/src{/branchOrHash,path}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("branchOrHash", file.getHash())
                .set("path", file.getPath())
                .expand();
        return getRequestAsInputStream(url);
    }
}
