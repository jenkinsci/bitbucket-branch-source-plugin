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
package com.cloudbees.jenkins.plugins.bitbucket.client;

import com.cloudbees.jenkins.plugins.bitbucket.JsonParser;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketException;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryProtocol;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryType;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRequestException;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketTeam;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketOAuth;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketOAuthService;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudBranch;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudCommit;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketPullRequestCommit;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketPullRequestCommits;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketPullRequestValue;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketPullRequests;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudTeam;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketRepositoryHook;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketRepositoryHooks;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketRepositorySource;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.PaginatedBitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.UserRoleInRepository;
import com.cloudbees.jenkins.plugins.bitbucket.filesystem.BitbucketSCMFile;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ProxyConfiguration;
import hudson.Util;
import hudson.util.Secret;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMFile;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.scribe.model.OAuthConfig;
import org.scribe.model.OAuthConstants;
import org.scribe.model.Token;
import org.codehaus.jackson.type.TypeReference;
import static com.cloudbees.jenkins.plugins.bitbucket.Utils.encodePath;

public class BitbucketCloudApiClient implements BitbucketApi {
    private static final Logger LOGGER = Logger.getLogger(BitbucketCloudApiClient.class.getName());
    private static final String V2_API_BASE_URL = "https://api.bitbucket.org/2.0/repositories/";
    private static final String V2_TEAMS_API_BASE_URL = "https://api.bitbucket.org/2.0/teams/";
    private static final int API_RATE_LIMIT_CODE = 429;
    private HttpClient client;
    private static final MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
    private final String owner;
    private final String repositoryName;
    private final UsernamePasswordCredentials credentials;
    static {
        connectionManager.getParams().setDefaultMaxConnectionsPerHost(20);
        connectionManager.getParams().setMaxTotalConnections(22);
    }

    private Token token;

    public BitbucketCloudApiClient(String owner, String repositoryName, StandardUsernamePasswordCredentials creds) {
        if (creds != null) {
            this.credentials = new UsernamePasswordCredentials(creds.getUsername(), Secret.toString(creds.getPassword()));
        } else {
            this.credentials = null;
        }
        this.owner = owner;
        this.repositoryName = repositoryName;

        // Create Http client
        HttpClient client = new HttpClient(connectionManager);
        client.getParams().setConnectionManagerTimeout(10 * 1000);
        client.getParams().setSoTimeout(60 * 1000);

        if (credentials != null) {
            if(this.token == null) {
                OAuthConfig config = new OAuthConfig(credentials.getUserName(), credentials.getPassword());
                BitbucketOAuthService OAuthService = (BitbucketOAuthService) new BitbucketOAuth().createService(config);
                Token token = OAuthService.getAccessToken(OAuthConstants.EMPTY_TOKEN, null);
                this.token = token;
            }
//                client.getState().setCredentials(AuthScope.ANY, credentials);
//                client.getParams().setAuthenticationPreemptive(true);
            client.getParams().setAuthenticationPreemptive(false);
        } else {
            client.getParams().setAuthenticationPreemptive(false);
        }

        setClientProxyParams("bitbucket.org", client);
        this.client = client;
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
    public String getRepositoryUri(@NonNull BitbucketRepositoryType type,
                                   @NonNull BitbucketRepositoryProtocol protocol,
                                   @CheckForNull Integer protocolPortOverride,
                                   @NonNull String owner,
                                   @NonNull String repository) {
        // ignore port override on Cloud
        switch (type) {
            case GIT:
                switch (protocol) {
                    case HTTP:
                        return "https://bitbucket.org/" + owner + "/" + repository + ".git";
                    case SSH:
                        return "git@bitbucket.org:" + owner + "/" + repository + ".git";
                    default:
                        throw new IllegalArgumentException("Unsupported repository protocol: " + protocol);
                }
            case MERCURIAL:
                switch (protocol) {
                    case HTTP:
                        return "https://bitbucket.org/" + owner + "/" + repository;
                    case SSH:
                        return "ssh://hg@bitbucket.org/" + owner + "/" + repository;
                    default:
                        throw new IllegalArgumentException("Unsupported repository protocol: " + protocol);
                }
            default:
                throw new IllegalArgumentException("Unsupported repository type: " + type);
        }
    }

    @CheckForNull
    public String getLogin() {
        if (credentials != null) {
            return credentials.getUserName();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<BitbucketPullRequestValue> getPullRequests() throws InterruptedException, IOException {
        String urlTemplate = V2_API_BASE_URL + this.owner + "/" + this.repositoryName + "/pullrequests?page=%d&pagelen=50";
        String url;

        List<BitbucketPullRequestValue> pullRequests = new ArrayList<BitbucketPullRequestValue>();
        int pageNumber = 1;
        String response = getRequest(url = String.format(urlTemplate, pageNumber));
        BitbucketPullRequests page;
        try {
            page = JsonParser.toJava(response, BitbucketPullRequests.class);
        } catch (IOException e) {
            throw new IOException("I/O error when parsing response from URL: " + url, e);
        }
        pullRequests.addAll(page.getValues());
        while (page.getNext() != null) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            pageNumber++;
            response = getRequest(url = String.format(urlTemplate, pageNumber));
            try {
                page = JsonParser.toJava(response, BitbucketPullRequests.class);
            } catch (IOException e) {
                throw new IOException("I/O error when parsing response from URL: " + url, e);
            }
            pullRequests.addAll(page.getValues());
        }
        return pullRequests;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public BitbucketPullRequest getPullRequestById(@NonNull Integer id) throws IOException, InterruptedException {
        String url = V2_API_BASE_URL + this.owner + "/" + this.repositoryName + "/pullrequests/" + id;
        String response = getRequest(url);
        try {
            return JsonParser.toJava(response, BitbucketPullRequestValue.class);
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
        String url = V2_API_BASE_URL + owner + "/" + repositoryName;
        String response = getRequest(url);
        try {
            return JsonParser.toJava(response, BitbucketCloudRepository.class);
        } catch (IOException e) {
            throw new IOException("I/O error when parsing response from URL: " + url, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postCommitComment(@NonNull String hash, @NonNull String comment) throws IOException, InterruptedException {
        String path = V2_API_BASE_URL + this.owner + "/" + this.repositoryName + "/commit/" + hash + "/build";
        try {
            NameValuePair content = new NameValuePair("content", comment);
            postRequest(path, new NameValuePair[]{ content });
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
        StringBuilder url = new StringBuilder(V2_API_BASE_URL);
        url.append(owner);
        url.append('/');
        url.append(repositoryName);
        url.append("/src/");
        url.append(Util.rawEncode(branchOrHash));
        for (String segment : StringUtils.split(path, "/")) {
            url.append('/');
            url.append(Util.rawEncode(segment));
        }
        int status = headRequestStatus(url.toString());
        return status == HttpStatus.SC_OK;
    }

    /**
     * {@inheritDoc}
     */
    @CheckForNull
    @Override
    public String getDefaultBranch() throws IOException, InterruptedException {
        String url = V2_API_BASE_URL + this.owner + "/" + this.repositoryName + "/?fields=mainbranch.name";
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
            return (String) mainbranch.get("name");
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<BitbucketCloudBranch> getBranches() throws IOException, InterruptedException {
        String url = V2_API_BASE_URL + this.owner + "/" + this.repositoryName + "/refs/branches";
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
        String url = V2_API_BASE_URL + owner + "/" + repositoryName + "/commit/" + hash;
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
        String url = V2_API_BASE_URL + owner + "/" + repositoryName + "/pullrequests/" + pull.getId()
                + "/commits?fields=values.hash&pagelen=1";
        String response = getRequest(url);
        try {
            BitbucketPullRequestCommits commits = JsonParser.toJava(response, BitbucketPullRequestCommits.class);
            for (BitbucketPullRequestCommit commit : Util.fixNull(commits.getValues())) {
                return commit.getHash();
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
        postRequest(V2_API_BASE_URL + owner + "/" + repositoryName + "/hooks", JsonParser.toJson(hook));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateCommitWebHook(@NonNull BitbucketWebHook hook) throws IOException, InterruptedException {
        putRequest(V2_API_BASE_URL + owner + "/" + repositoryName + "/hooks/" + Util.rawEncode(hook.getUuid()), JsonParser.toJson(hook));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeCommitWebHook(@NonNull BitbucketWebHook hook) throws IOException, InterruptedException {
        if (StringUtils.isBlank(hook.getUuid())) {
            throw new BitbucketException("Hook UUID required");
        }
        deleteRequest(V2_API_BASE_URL + owner + "/" + repositoryName + "/hooks/" + Util.rawEncode(hook.getUuid()));
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<BitbucketRepositoryHook> getWebHooks() throws IOException, InterruptedException {
        String urlTemplate = V2_API_BASE_URL + this.owner + "/" + this.repositoryName + "/hooks?page=%d&pagelen=50";
        String url = urlTemplate;
        try {
            List<BitbucketRepositoryHook> repositoryHooks = new ArrayList<BitbucketRepositoryHook>();
            int pageNumber = 1;
            String response = getRequest(url = String.format(urlTemplate, pageNumber));
            BitbucketRepositoryHooks page = parsePaginatedRepositoryHooks(response);
            repositoryHooks.addAll(page.getValues());
            while (page.getNext() != null) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                pageNumber++;
                response = getRequest(url = String.format(urlTemplate, pageNumber));
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
        String path = V2_API_BASE_URL + this.owner + "/" + this.repositoryName + "/commit/" + status.getHash()
                + "/statuses/build";
        postRequest(path, JsonParser.toJson(status));
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
        try {
            String response = getRequest(V2_TEAMS_API_BASE_URL + owner);
            return JsonParser.toJava(response, BitbucketCloudTeam.class);
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            throw new IOException("I/O error when parsing response from URL: " + V2_TEAMS_API_BASE_URL + owner, e);

        }
    }

    /**
     * The role parameter only makes sense when the request is authenticated, so
     * if there is no auth information ({@link #credentials}) the role will be omited.
     */
    @NonNull
    @Override
    public List<BitbucketCloudRepository> getRepositories(@CheckForNull UserRoleInRepository role)
            throws InterruptedException, IOException {
        String urlTemplate;
        if (role != null && getLogin() != null) {
            urlTemplate = V2_API_BASE_URL + owner + "?role=" + role.getId() + "&page=%s&pagelen=50";
        } else {
            urlTemplate = V2_API_BASE_URL + owner + "?page=%s&pagelen=50";
        }
        String url;
        List<BitbucketCloudRepository> repositories = new ArrayList<BitbucketCloudRepository>();
        Integer pageNumber = 1;
        String response = getRequest(url = String.format(urlTemplate, pageNumber.toString()));
        PaginatedBitbucketRepository page;
        try {
            page = JsonParser.toJava(response, PaginatedBitbucketRepository.class);
            repositories.addAll(page.getValues());
        } catch (IOException e) {
            throw new IOException("I/O error when parsing response from URL: " + url, e);
        }
        while (page.getNext() != null) {
                pageNumber++;
                response = getRequest(url = String.format(urlTemplate, pageNumber.toString()));
            try {
                page = JsonParser.toJava(response, PaginatedBitbucketRepository.class);
                repositories.addAll(page.getValues());
            } catch (IOException e) {
                throw new IOException("I/O error when parsing response from URL: " + url, e);
            }
        }
        Collections.sort(repositories, new Comparator<BitbucketCloudRepository>() {
            @Override
            public int compare(BitbucketCloudRepository o1, BitbucketCloudRepository o2) {
                return o1.getRepositoryName().compareTo(o2.getRepositoryName());
            }
        });
        return repositories;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public List<BitbucketCloudRepository> getRepositories() throws IOException, InterruptedException {
        return getRepositories(null);
    }

    private synchronized void signRequest(HttpMethod request) {
        request.addRequestHeader(OAuthConstants.HEADER, "Bearer " + this.token.getToken());
    }

    private static void setClientProxyParams(String host, HttpClient client) {
        Jenkins jenkins = Jenkins.getInstance();
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
            client.getHostConfiguration().setProxy(proxyAddress.getHostString(), proxyAddress.getPort());
            String username = proxyConfig.getUserName();
            String password = proxyConfig.getPassword();
            if (username != null && !"".equals(username.trim())) {
                LOGGER.fine("Using proxy authentication (user=" + username + ")");
                client.getState().setProxyCredentials(AuthScope.ANY,
                        new UsernamePasswordCredentials(username, password));
            }
        }
    }

    private int executeMethod(HttpMethod httpMethod) throws InterruptedException, IOException {
        int status = client.executeMethod(httpMethod);
        while (status == API_RATE_LIMIT_CODE) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            /*
                TODO: When bitbucket starts supporting rate limit expiration time, remove 5 sec wait and put code
                      to wait till expiration time is over. It should also fix the wait for ever loop.
             */
            LOGGER.fine("Bitbucket Cloud API rate limit reached, sleeping for 5 sec then retry...");
            Thread.sleep(5000);
            status = client.executeMethod(httpMethod);
        }
        return status;
    }

    /**
     * Caller's responsbile to close the InputStream.
     */
    private InputStream getRequestAsInputStream(String path) throws IOException, InterruptedException {
        GetMethod httpget = new GetMethod(path);
        signRequest(httpget);
        try {
            executeMethod(httpget);
            if (httpget.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                throw new FileNotFoundException("URL: " + path);
            }
            InputStream response =  httpget.getResponseBodyAsStream();
            if (httpget.getStatusCode() != HttpStatus.SC_OK) {
                throw new BitbucketRequestException(httpget.getStatusCode(),
                        "HTTP request error. Status: " + httpget.getStatusCode() + ": " + httpget.getStatusText()
                                + ".\n" + IOUtils.toString(response));
            }
            return response;
        } catch (BitbucketRequestException | FileNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new IOException("Communication error for url: " + path, e);
        }
    }

    private String getRequest(String path) throws IOException, InterruptedException {
        try (InputStream inputStream = getRequestAsInputStream(path)){
            return IOUtils.toString(inputStream, "UTF-8");
        }
    }

    private int headRequestStatus(String path) throws IOException, InterruptedException {
        HeadMethod httpHead = new HeadMethod(path);
        try {
            return executeMethod(httpHead);
        } catch (IOException e) {
            throw new IOException("Communication error for url: " + path, e);
        } finally {
            httpHead.releaseConnection();
        }
    }

    private void deleteRequest(String path) throws IOException, InterruptedException {
        DeleteMethod httppost = new DeleteMethod(path);
        signRequest(httppost);
        try {
            executeMethod(httppost);
            if (httppost.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                throw new FileNotFoundException("URL: " + path);
            }
            if (httppost.getStatusCode() != HttpStatus.SC_NO_CONTENT) {
                throw new BitbucketRequestException(httppost.getStatusCode(), "HTTP request error. Status: " + httppost.getStatusCode() + ": " + httppost.getStatusText());
            }
        } catch (BitbucketRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new IOException("Communication error for url: " + path, e);
        } finally {
            httppost.releaseConnection();
        }
    }

    private String doRequest(HttpMethodBase httppost) throws IOException, InterruptedException {
        signRequest(httppost);
        try {
            executeMethod(httppost);
            if (httppost.getStatusCode() == HttpStatus.SC_NO_CONTENT) {
                // 204, no content
                return "";
            }
            String response = getResponseContent(httppost, httppost.getResponseContentLength());
            if (httppost.getStatusCode() != HttpStatus.SC_OK && httppost.getStatusCode() != HttpStatus.SC_CREATED) {
                throw new BitbucketRequestException(httppost.getStatusCode(), "HTTP request error. Status: " + httppost.getStatusCode() + ": " + httppost.getStatusText() + ".\n" + response);
            }
            return response;
        } catch (BitbucketRequestException e) {
            throw e;
        } catch (IOException e) {
            try {
                throw new IOException("Communication error for url: " + httppost.getURI(), e);
            } catch (IOException e1) {
                throw new IOException("Communication error", e);
            }
        } finally {
            httppost.releaseConnection();
        }

    }

    private String getResponseContent(HttpMethod httppost, long len) throws IOException {
        String response;
        if (len == 0) {
            response = "";
        } else {
            ByteArrayOutputStream buf;
            if (len > 0 && len <= Integer.MAX_VALUE / 2) {
                buf = new ByteArrayOutputStream((int) len);
            } else {
                buf = new ByteArrayOutputStream();
            }
            try (InputStream is = httppost.getResponseBodyAsStream()) {
                IOUtils.copy(is, buf);
            }
            response = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }
        return response;
    }

    private String putRequest(String path, String content) throws IOException, InterruptedException  {
        PutMethod request = new PutMethod(path);
        request.setRequestEntity(new StringRequestEntity(content, "application/json", "UTF-8"));
        return doRequest(request);
    }

    private String postRequest(String path, String content) throws IOException, InterruptedException {
        PostMethod httppost = new PostMethod(path);
        httppost.setRequestEntity(new StringRequestEntity(content, "application/json", "UTF-8"));
        return doRequest(httppost);
    }

    private String postRequest(String path, NameValuePair[] params) throws IOException, InterruptedException {
        PostMethod httppost = new PostMethod(path);
        httppost.setRequestBody(params);
        return doRequest(httppost);
    }

    private List<BitbucketCloudBranch> getAllBranches(String response) throws IOException, InterruptedException {
        List<BitbucketCloudBranch> branches = new ArrayList<BitbucketCloudBranch>();
        BitbucketCloudPage<BitbucketCloudBranch> page = JsonParser.mapper.readValue(response,
                new TypeReference<BitbucketCloudPage<BitbucketCloudBranch>>(){});
        branches.addAll(page.getValues());
        while (!page.isLastPage()){
            response = getRequest(page.getNext());
            page = JsonParser.mapper.readValue(response,
                    new TypeReference<BitbucketCloudPage<BitbucketCloudBranch>>(){});
            branches.addAll(page.getValues());
        }
        return branches;
    }

    public Iterable<SCMFile> getDirectoryContent(final BitbucketSCMFile parent) throws IOException, InterruptedException {
        StringBuilder url = new StringBuilder(V2_API_BASE_URL);
        url.append(owner);
        url.append('/');
        url.append(repositoryName);
        url.append("/src/");
        url.append(Util.rawEncode(parent.getRef()));
        url.append('/');

        url.append(encodePath(parent.getPath()));
        List<SCMFile> result = new ArrayList<>();
        String response = getRequest(url.toString());
        BitbucketCloudPage<BitbucketRepositorySource> page = JsonParser.mapper.readValue(response,
                new TypeReference<BitbucketCloudPage<BitbucketRepositorySource>>(){});

        for(BitbucketRepositorySource source:page.getValues()){
            result.add(source.toBitbucketScmFile(parent));
        }

        while (!page.isLastPage()){
            response = getRequest(page.getNext());
            page = JsonParser.mapper.readValue(response,
                    new TypeReference<BitbucketCloudPage<Map>>(){});
            for(BitbucketRepositorySource source:page.getValues()){
                result.add(source.toBitbucketScmFile(parent));
            }
        }
        return result;
    }

    public InputStream getFileContent(BitbucketSCMFile file) throws IOException, InterruptedException {
        String url = V2_API_BASE_URL + owner +
                '/' +
                repositoryName +
                "/src/" +
                Util.rawEncode(file.getRef()) +
                '/' +
                encodePath(file.getPath());
        return getRequestAsInputStream(url);
    }
}
