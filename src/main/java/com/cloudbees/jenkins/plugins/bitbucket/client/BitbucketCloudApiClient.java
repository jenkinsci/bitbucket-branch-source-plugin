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
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRequestException;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketTeam;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudBranch;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudCommit;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketCloudPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketCloudPullRequestCommit;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketRepositoryHook;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketRepositorySource;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.UserRoleInRepository;
import com.cloudbees.jenkins.plugins.bitbucket.filesystem.BitbucketSCMFile;
import com.cloudbees.jenkins.plugins.bitbucket.impl.client.AbstractBitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.impl.client.ICheckedCallable;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketApiUtils;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.JsonParser;
import com.damnhandy.uri.template.UriTemplate;
import com.damnhandy.uri.template.impl.Operator;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import jenkins.scm.api.SCMFile;
import jenkins.scm.impl.avatars.AvatarImage;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicNameValuePair;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.apache.commons.lang3.StringUtils.abbreviate;

public class BitbucketCloudApiClient extends AbstractBitbucketApi implements BitbucketApi {

    private static final HttpHost API_HOST = BitbucketApiUtils.toHttpHost("https://api.bitbucket.org");
    private static final String V2_API_BASE_URL = "https://api.bitbucket.org/2.0/repositories";
    private static final String V2_WORKSPACES_API_BASE_URL = "https://api.bitbucket.org/2.0/workspaces";
    private static final String REPO_URL_TEMPLATE = V2_API_BASE_URL + "{/owner,repo}";
    // Limit images to 16k
    private static final int MAX_AVATAR_LENGTH = 16384;
    private static final int MAX_PAGE_LENGTH = 100;

    private static final HttpClientConnectionManager connectionManager = connectionManagerBuilder()
            .setMaxConnPerRoute(20)
            // for bitbucket cloud there is only one server (route)
            .setMaxConnTotal(20)
            .build();

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
        this.client = super.setupClientBuilder().build();
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
    public List<BitbucketCloudPullRequest> getPullRequests() throws IOException {
        // we can not use the default max pagelen also if documented
        // https://developer.atlassian.com/bitbucket/api/2/reference/resource/repositories/%7Busername%7D/%7Brepo_slug%7D/pullrequests#get
        // so because with values greater than 50 the API returns HTTP 400
        int pageLen = 50;
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/pullrequests{?page,pagelen}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("pagelen", pageLen)
                .expand();

        List<BitbucketCloudPullRequest> pullRequests = getPagedRequest(url, BitbucketCloudPullRequest.class);
        // PRs with missing destination branch are invalid and should be ignored.
        pullRequests.removeIf(this::shouldIgnore);

        for (BitbucketCloudPullRequest pullRequest : pullRequests) {
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

    private void setupClosureForPRBranch(BitbucketCloudPullRequest pullRequest) {
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
    public BitbucketPullRequest getPullRequestById(@NonNull Integer id) throws IOException {
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/pullrequests{/id}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("id", id)
                .expand();
        BitbucketCloudPullRequest pr = getRequestAs(url, BitbucketCloudPullRequest.class);
        setupClosureForPRBranch(pr);
        return pr;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public BitbucketRepository getRepository() throws IOException {
        if (repositoryName == null) {
            throw new UnsupportedOperationException("Cannot get a repository from an API instance that is not associated with a repository");
        }
        if (!enableCache || cachedRepository == null) {
            String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE)
                    .set("owner", owner)
                    .set("repo", repositoryName)
                    .expand();
            cachedRepository = getRequestAs(url, BitbucketCloudRepository.class);
        }
        return cachedRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postCommitComment(@NonNull String hash, @NonNull String comment) throws IOException {
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
            throw new IOException("Cannot attach comment to commit, request URL: " + path, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkPathExists(@NonNull String branchOrHash, @NonNull String path) throws IOException {
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
            logger.log(Level.FINE, "You currently do not have permissions to pull from repo: {0} at branch {1}", new Object[] { repositoryName, branchOrHash });
            return false;
        } else {
            throw new IOException("Communication error requesting URL: " + path + " status code: " + status);
        }
    }

    /**
     * {@inheritDoc}
     */
    @CheckForNull
    @Override
    public String getDefaultBranch() throws IOException {
        if (!enableCache || cachedDefaultBranch == null) {
            String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/{?fields}")
                    .set("owner", owner)
                    .set("repo", repositoryName)
                    .set("fields", "mainbranch.name")
                    .expand();
            try {
                Map resp = getRequestAs(url, Map.class);
                Map mainbranch = (Map) resp.get("mainbranch");
                if (mainbranch != null) {
                    cachedDefaultBranch = (String) mainbranch.get("name");
                }
            } catch (FileNotFoundException e) {
                logger.log(Level.FINE, "Could not find default branch for {0}/{1}",
                        new Object[]{this.owner, this.repositoryName});
                return null;
            }
        }
        return cachedDefaultBranch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BitbucketCloudBranch getTag(@NonNull String tagName) throws IOException {
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/refs/tags/{name}")
            .set("owner", owner)
            .set("repo", repositoryName)
            .set("name", tagName)
            .expand();
        return getRequestAs(url, BitbucketCloudBranch.class);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<BitbucketCloudBranch> getTags() throws IOException {
        return getBranchesByRef("/refs/tags");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BitbucketCloudBranch getBranch(@NonNull String branchName) throws IOException {
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/refs/branches/{name}")
            .set("owner", owner)
            .set("repo", repositoryName)
            .set("name", branchName)
            .expand();
        return getRequestAs(url, BitbucketCloudBranch.class);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<BitbucketCloudBranch> getBranches() throws IOException {
        return getBranchesByRef("/refs/branches");
    }

    public List<BitbucketCloudBranch> getBranchesByRef(String nodePath) throws IOException {
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + nodePath + "{?pagelen}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("pagelen", MAX_PAGE_LENGTH)
                .expand();
        return getPagedRequest(url, BitbucketCloudBranch.class).stream()
                .filter(BitbucketCloudBranch::isActive) // Filter the inactive branches out
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @CheckForNull
    public BitbucketCommit resolveCommit(@NonNull String hash) throws IOException {
        final String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/commit/{hash}")
            .set("owner", owner)
            .set("repo", repositoryName)
            .set("hash", hash)
            .expand();

        ICheckedCallable<BitbucketCloudCommit, IOException> request = () -> {
            try {
                return getRequestAs(url, BitbucketCloudCommit.class);
            } catch (FileNotFoundException e) {
                return null;
            }
        };

        if (enableCache) {
            try {
                return cachedCommits.get(hash, request);
            } catch (ExecutionException e) {
                BitbucketRequestException bre = BitbucketApiUtils.unwrap(e);
                if (bre != null) {
                    throw bre;
                } else {
                    throw new IOException(e);
                }
            }
        } else {
            return request.call();
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String resolveSourceFullHash(@NonNull BitbucketPullRequest pull) throws IOException {
        return resolveCommit(pull).getHash();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public BitbucketCommit resolveCommit(@NonNull BitbucketPullRequest pull) throws IOException {
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/pullrequests/{pullId}/commits{?fields,pagelen}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("pullId", pull.getId())
                .set("fields", "values.hash,values.author.raw,values.date,values.message")
                .set("pagelen", 1)
                .expand();
        return getPagedRequest(url, BitbucketCloudPullRequestCommit.class)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BitbucketException("Could not determine commit for pull request " + pull.getId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerCommitWebHook(@NonNull BitbucketWebHook hook) throws IOException {
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
    public void updateCommitWebHook(@NonNull BitbucketWebHook hook) throws IOException {
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
    public void removeCommitWebHook(@NonNull BitbucketWebHook hook) throws IOException {
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
    public List<BitbucketRepositoryHook> getWebHooks() throws IOException {
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/hooks{?page,pagelen}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("pagelen", MAX_PAGE_LENGTH)
                .expand();
        return getPagedRequest(url, BitbucketRepositoryHook.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postBuildStatus(@NonNull BitbucketBuildStatus status) throws IOException {
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
    public boolean isPrivate() throws IOException {
        return getRepository().isPrivate();
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

        ICheckedCallable<BitbucketTeam, IOException> request = () -> {
            try {
                return getRequestAs(url, BitbucketCloudWorkspace.class);
            } catch (FileNotFoundException e) {
                return null;
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
            } catch (BitbucketRequestException e) {
                throw e;
            } catch (IOException e) {
                throw new IOException("I/O error when parsing response from URL: " + url, e);
            }
        }
        return AvatarImage.EMPTY;
    }

    /*
     * The role parameter only makes sense when the request is authenticated, so
     * if there is no auth information ({@link #getAuthenticator()}) the role will be omitted.
     */
    @NonNull
    @Override
    public List<BitbucketCloudRepository> getRepositories(@CheckForNull UserRoleInRepository role) throws IOException {
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
        String url = template.expand();

        ICheckedCallable<List<BitbucketCloudRepository>, IOException> request = () -> {
            List<BitbucketCloudRepository> repositories = getPagedRequest(url, BitbucketCloudRepository.class);
            repositories.sort(Comparator.comparing(BitbucketCloudRepository::getRepositoryName));
            return repositories;
        };
        if (enableCache) {
            try {
                return cachedRepositories.get(cacheKey.toString(), request);
            } catch (ExecutionException e) {
                BitbucketRequestException bre = BitbucketApiUtils.unwrap(e);
                if (bre != null) {
                    throw bre;
                } else {
                    throw new IOException(e);
                }
            }
        } else {
            return request.call();
        }
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public List<BitbucketCloudRepository> getRepositories() throws IOException {
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

    @Override
    public Iterable<SCMFile> getDirectoryContent(final BitbucketSCMFile parent) throws IOException {
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/src{/branchOrHash,path}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("branchOrHash", parent.getHash())
                .set("path", parent.getPath())
                .expand();
        List<BitbucketRepositorySource> sources = getPagedRequest(url, BitbucketRepositorySource.class);
        return sources.stream()
                .map(source -> source.toBitbucketSCMFile(parent))
                .map(SCMFile.class::cast)
                .toList();
    }

    @Override
    public InputStream getFileContent(@NonNull BitbucketSCMFile file) throws IOException {
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
    public SCMFile getFile(@NonNull BitbucketSCMFile file) throws IOException {
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/src{/branchOrHash,path}?format=meta")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("branchOrHash", file.getHash() != null ? file.getHash() : file.getRef())
                .set("path", file.getPath())
                .expand();
        BitbucketRepositorySource src = getRequestAs(url, BitbucketRepositorySource.class);
        return src.toBitbucketSCMFile((BitbucketSCMFile) file.parent());
    }

    @NonNull
    @Override
    public List<BitbucketCloudCommit> getCommits(@CheckForNull String fromCommit, @NonNull String toCommit) throws IOException {
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/commits{?include,exclude}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("include", toCommit)
                .set("exclude", fromCommit)
                .expand();
        return getPagedRequest(url, BitbucketCloudCommit.class);
    }

/*
    @Override
    public List<BitbucketCloudCommitDiffStat> getCommitsChanges(@NonNull String fromCommit, @Nullable String toCommit) throws IOException {
        String url = UriTemplate.fromTemplate(REPO_URL_TEMPLATE + "/diffstat/{spec}")
                .set("owner", owner)
                .set("repo", repositoryName)
                .set("spec", StringUtils.join(new String[] { toCommit, "..", fromCommit }))
                .expand();

        String response = getRequest(url);
        try {
            BitbucketCloudPage<BitbucketCloudCommitDiffStat> page = JsonParser.toJava(response, new TypeReference<BitbucketCloudPage<BitbucketCloudCommitDiffStat>>(){});
            List<BitbucketCloudCommitDiffStat> changes = new ArrayList<>();
            changes.addAll(page.getValues());
            while (!page.isLastPage()) {
                response = getRequest(page.getNext());
                page = JsonParser.toJava(response, new TypeReference<BitbucketCloudPage<BitbucketCloudCommitDiffStat>>(){});
                changes.addAll(page.getValues());
            }

            return changes;
        } catch (JacksonException e) {
            throw new IOException("I/O error when parsing response from URL: " + url, e);
        }
    }
*/
    private <V> List<V> getPagedRequest(String url, Class<V> resultType) throws IOException {
        List<V> resources = new ArrayList<>();
        String response = getRequest(url);

        ParameterizedType parameterizedType = new ParameterizedType() {

            @Override
            public Type getRawType() {
                return BitbucketCloudPage.class;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }

            @Override
            public Type[] getActualTypeArguments() {
                return new Type[] { resultType };
            }
        };

        try {
            TypeReference<BitbucketCloudPage<V>> type = new TypeReference<BitbucketCloudPage<V>>(){
                @Override
                public Type getType() {
                    return parameterizedType;
                }
            };

            BitbucketCloudPage<V> page = JsonParser.toJava(response, type);
            resources.addAll(page.getValues());
            while (!page.isLastPage()){
                response = getRequest(page.getNext());
                page = JsonParser.toJava(response, type);
                resources.addAll(page.getValues());
            }
        } catch (JacksonException e) {
            throw new IOException("I/O error when parsing response from URL: " + url, e);
        }
        return resources;
    }

    private <V> V getRequestAs(String url, Class<V> resultType) throws IOException {
        String response = getRequest(url);
        try {
            return JsonParser.toJava(response, resultType);
        } catch (JacksonException e) {
            throw new IOException("I/O error when parsing response from URL: " + url, e);
        }
    }
}
