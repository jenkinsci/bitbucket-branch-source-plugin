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
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudBranch;
import com.cloudbees.jenkins.plugins.bitbucket.client.branch.BitbucketCloudCommit;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketPullRequestValue;
import com.cloudbees.jenkins.plugins.bitbucket.client.pullrequest.BitbucketPullRequests;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketCloudTeam;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketRepositoryHook;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.BitbucketRepositoryHooks;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.PaginatedBitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.UserRoleInRepository;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ProxyConfiguration;
import hudson.util.Secret;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

public class BitbucketCloudApiClient implements BitbucketApi {
    private static final Logger LOGGER = Logger.getLogger(BitbucketCloudApiClient.class.getName());
    private static final String V1_API_BASE_URL = "https://bitbucket.org/api/1.0/repositories/";
    private static final String V2_API_BASE_URL = "https://bitbucket.org/api/2.0/repositories/";
    private static final String V2_TEAMS_API_BASE_URL = "https://bitbucket.org/api/2.0/teams/";
    private static final int MAX_PAGES = 100;
    private HttpClient client;
    private static final MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
    private final String owner;
    private final String repositoryName;
    private final UsernamePasswordCredentials credentials;

    static {
        connectionManager.getParams().setDefaultMaxConnectionsPerHost(20);
        connectionManager.getParams().setMaxTotalConnections(22);
    }

    public BitbucketCloudApiClient(String owner, String repositoryName, StandardUsernamePasswordCredentials creds) {
        if (creds != null) {
            this.credentials = new UsernamePasswordCredentials(creds.getUsername(), Secret.toString(creds.getPassword()));
        } else {
            this.credentials = null;
        }
        this.owner = owner;
        this.repositoryName = repositoryName;
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
            page = parse(response, BitbucketPullRequests.class);
        } catch (IOException e) {
            throw new IOException("I/O error when parsing response from URL: " + url, e);
        }
        pullRequests.addAll(page.getValues());
        while (page.getNext() != null && pageNumber < MAX_PAGES) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            pageNumber++;
            response = getRequest(url = String.format(urlTemplate, pageNumber));
            try {
                page = parse(response, BitbucketPullRequests.class);
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
            return parse(response, BitbucketPullRequestValue.class);
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
            return parse(response, BitbucketCloudRepository.class);
        } catch (IOException e) {
            throw new IOException("I/O error when parsing response from URL: " + url, e);
        }
    }

    public void deletePullRequestComment(String pullRequestId, String commentId) throws IOException, InterruptedException {
        String path = V1_API_BASE_URL + this.owner + "/" + this.repositoryName + "/pullrequests/" + pullRequestId + "/comments/" + commentId;
        deleteRequest(path);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postCommitComment(@NonNull String hash, @NonNull String comment) throws IOException, InterruptedException {
        String path = V1_API_BASE_URL + this.owner + "/" + this.repositoryName + "/changesets/" + hash + "/comments";
        try {
            NameValuePair content = new NameValuePair("content", comment);
            postRequest(path, new NameValuePair[]{ content });
        } catch (UnsupportedEncodingException e) {
            throw e;
        } catch (IOException e) {
            throw new IOException("Cannot comment on commit, url: " + path, e);
        }
    }

    public void deletePullRequestApproval(String pullRequestId) throws IOException, InterruptedException {
        String path = V2_API_BASE_URL + this.owner + "/" + this.repositoryName + "/pullrequests/" + pullRequestId + "/approve";
        deleteRequest(path);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkPathExists(@NonNull String branch, @NonNull String path) throws IOException, InterruptedException {
        int status = getRequestStatus(V1_API_BASE_URL + owner + "/" + repositoryName + "/raw/" + branch + "/" + path);
        return status == HttpStatus.SC_OK;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getDefaultBranch() throws IOException, InterruptedException {
        String url = V1_API_BASE_URL + this.owner + "/" + this.repositoryName + "/main-branch";
        String response = getRequest(url);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode name = mapper.readTree(response).get("name");
        if (name != null) {
            return name.getTextValue();
        }
        throw new IOException("I/O error when parsing response from URL: " + url);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<BitbucketCloudBranch> getBranches() throws IOException, InterruptedException {
        String url = V1_API_BASE_URL + this.owner + "/" + this.repositoryName + "/branches";
        String response = getRequest(url);
        try {
            return parseBranchesJson(response);
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
            return parse(response, BitbucketCloudCommit.class);
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
        String url = V2_API_BASE_URL + pull.getSource().getRepository().getOwnerName() + "/" +
                pull.getSource().getRepository().getRepositoryName() + "/commit/" + pull.getSource().getCommit()
                .getHash();
        String response = getRequest(url);
        try {
            return parse(response, BitbucketCloudCommit.class).getHash();
        } catch (IOException e) {
            throw new IOException("I/O error when parsing response from URL: " + url, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerCommitWebHook(@NonNull BitbucketWebHook hook) throws IOException, InterruptedException {
        postRequest(V2_API_BASE_URL + owner + "/" + repositoryName + "/hooks", asJson(hook));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeCommitWebHook(@NonNull BitbucketWebHook hook) throws IOException, InterruptedException {
        if (StringUtils.isBlank(hook.getUuid())) {
            throw new BitbucketException("Hook UUID required");
        }
        deleteRequest(V2_API_BASE_URL + owner + "/" + repositoryName + "/hooks/" + URLEncoder.encode(hook.getUuid(), "UTF-8"));
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
            while (page.getNext() != null && pageNumber < 100) {
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
    public void postBuildStatus(@NonNull BitbucketBuildStatus status) throws IOException {
        String path = V2_API_BASE_URL + this.owner + "/" + this.repositoryName + "/commit/" + status.getHash() + "/statuses/build";;
        postRequest(path, serialize(status));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isPrivate() throws IOException, InterruptedException {
        return getRepository().isPrivate();
    }

    private BitbucketRepositoryHooks parsePaginatedRepositoryHooks(String response) throws JsonParseException, JsonMappingException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        BitbucketRepositoryHooks parsedResponse;
        parsedResponse = mapper.readValue(response, BitbucketRepositoryHooks.class);
        return parsedResponse;
    }

    private String asJson(BitbucketWebHook hook) throws JsonGenerationException, JsonMappingException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(hook);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @CheckForNull
    public BitbucketTeam getTeam() throws IOException, InterruptedException {
        try {
            String response = getRequest(V2_TEAMS_API_BASE_URL + owner);
            return parse(response, BitbucketCloudTeam.class);
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
            page = parse(response, PaginatedBitbucketRepository.class);
            repositories.addAll(page.getValues());
        } catch (IOException e) {
            throw new IOException("I/O error when parsing response from URL: " + url, e);
        }
        while (page.getNext() != null && pageNumber < MAX_PAGES) {
                pageNumber++;
                response = getRequest(url = String.format(urlTemplate, pageNumber.toString()));
            try {
                page = parse(response, PaginatedBitbucketRepository.class);
                repositories.addAll(page.getValues());
            } catch (IOException e) {
                throw new IOException("I/O error when parsing response from URL: " + url, e);
            }
        }
        return repositories;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public List<BitbucketCloudRepository> getRepositories() throws IOException, InterruptedException {
        return getRepositories(null);
    }

    private synchronized HttpClient getHttpClient() {
        if (this.client == null) {
            HttpClient client = new HttpClient(connectionManager);
            client.getParams().setConnectionManagerTimeout(10 * 1000);
            client.getParams().setSoTimeout(60 * 1000);

            client.getState().setCredentials(AuthScope.ANY, credentials);
            client.getParams().setAuthenticationPreemptive(true);

            setClientProxyParams("bitbucket.org", client);
            this.client = client;
        }

        return this.client;
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

    private static int executeMethod(HttpClient client, HttpMethod method) throws IOException {
        Jenkins jenkins = Jenkins.getInstance();
        ProxyConfiguration proxyConfig = null;
        if (jenkins != null) {
            proxyConfig = jenkins.proxy;
        }

        Proxy proxy = Proxy.NO_PROXY;
        if (proxyConfig != null) {
            proxy = proxyConfig.createProxy(getMethodHost(method));
        }

        if (proxy.type() != Proxy.Type.DIRECT) {
            final InetSocketAddress proxyAddress = (InetSocketAddress)proxy.address();
            LOGGER.fine("Jenkins proxy: " + proxy.address());

            final HostConfiguration hc = new HostConfiguration(client.getHostConfiguration());
            hc.setProxy(proxyAddress.getHostString(), proxyAddress.getPort());

            String username = proxyConfig.getUserName();
            String password = proxyConfig.getPassword();
            if (username != null && !"".equals(username.trim())) {
                LOGGER.fine("Using proxy authentication (user=" + username + ")");

                final HttpState state = new HttpState();
                state.setProxyCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
                return client.executeMethod(hc, method, state);
            } else {
                return client.executeMethod(hc, method);
            }
        } else {
            return client.executeMethod(method);
        }
    }

    private String getRequest(String path) throws IOException, InterruptedException {
        HttpClient client = getHttpClient();
        GetMethod httpget = new GetMethod(path);
        try {
            executeMethod(client, httpget);
            String response = new String(httpget.getResponseBody(), "UTF-8");
            if (httpget.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                throw new FileNotFoundException("URL: " + path);
            }
            if (httpget.getStatusCode() != HttpStatus.SC_OK) {
                throw new BitbucketRequestException(httpget.getStatusCode(),
                        "HTTP request error. Status: " + httpget.getStatusCode() + ": " + httpget.getStatusText()
                                + ".\n" + response);
            }
            return response;
        } catch (BitbucketRequestException | FileNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new IOException("Communication error for url: " + path, e);
        } finally {
            httpget.releaseConnection();
        }
    }

    private int getRequestStatus(String path) throws IOException {
        HttpClient client = getHttpClient();
        GetMethod httpget = new GetMethod(path);
        try {
            executeMethod(client, httpget);
            return httpget.getStatusCode();
        } catch (IOException e) {
            throw new IOException("Communication error for url: " + path, e);
        } finally {
            httpget.releaseConnection();
        }
    }

    private static String getMethodHost(HttpMethod method) {
        try {
            return method.getURI().getHost();
        } catch (URIException e) {
            throw new IllegalStateException("Could not obtain host part for method " + method, e);
        }
    }

    private void deleteRequest(String path) throws IOException {
        HttpClient client = getHttpClient();
        DeleteMethod httppost = new DeleteMethod(path);
        try {
            executeMethod(client, httppost);
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

    private String postRequest(PostMethod httppost) throws IOException {
        HttpClient client = getHttpClient();
        try {
            executeMethod(client, httppost);
            if (httppost.getStatusCode() == HttpStatus.SC_NO_CONTENT) {
                // 204, no content
                return "";
            }
            String response = new String(httppost.getResponseBody(), "UTF-8");
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

    private <T> String serialize(T o) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(o);
    }

    private String postRequest(String path, String content) throws IOException {
        PostMethod httppost = new PostMethod(path);
        httppost.setRequestEntity(new StringRequestEntity(content, "application/json", "UTF-8"));
        return postRequest(httppost);
    }

    private String postRequest(String path, NameValuePair[] params) throws IOException {
        PostMethod httppost = new PostMethod(path);
        httppost.setRequestBody(params);
        return postRequest(httppost);
    }

    private List<BitbucketCloudBranch> parseBranchesJson(String response) throws IOException {
        List<BitbucketCloudBranch> branches = new ArrayList<BitbucketCloudBranch>();
        ObjectMapper mapper = new ObjectMapper();
        JSONObject obj = JSONObject.fromObject(response);
        for (Object name : obj.keySet()) {
            BitbucketCloudBranch b = mapper.readValue(obj.getJSONObject((String) name).toString(), BitbucketCloudBranch.class);
            if (b.getName() == null) {
                // The branch name is null sometimes in API JSON response (unknown reason)
                b.setName((String) name);
            }
            branches.add(b);
        }
        return branches;
    }

    private <T> T parse(String response, Class<T> clazz) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(response, clazz);
    }

}
