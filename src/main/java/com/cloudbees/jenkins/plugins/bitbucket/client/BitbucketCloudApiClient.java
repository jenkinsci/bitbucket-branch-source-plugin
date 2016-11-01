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

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
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
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketException;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
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
import hudson.ProxyConfiguration;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

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

    public BitbucketCloudApiClient(String username, String password, String owner, String repositoryName) {
        if (!StringUtils.isBlank(username) && !StringUtils.isBlank(password)) {
            this.credentials = new UsernamePasswordCredentials(username, password);
        } else {
            this.credentials = null;
        }
        this.owner = owner;
        this.repositoryName = repositoryName;
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

    public BitbucketCloudApiClient(String owner, StandardUsernamePasswordCredentials creds) {
        this(owner, null, creds);
    }

    @Override
    public String getOwner() {
        return owner;
    }

    /** {@inheritDoc} */
    @Override
    public String getRepositoryName() {
        return repositoryName;
    }

    @CheckForNull
    public String getLogin() {
        if (credentials != null) {
            return credentials.getUserName();
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public List<BitbucketPullRequestValue> getPullRequests() {
        String url = V2_API_BASE_URL + this.owner + "/" + this.repositoryName + "/pullrequests?page=%s&pagelen=50";

        try {
            List<BitbucketPullRequestValue> pullRequests = new ArrayList<BitbucketPullRequestValue>();
            Integer pageNumber = 1;
            String response = getRequest(String.format(url, pageNumber.toString()));
            BitbucketPullRequests page = parse(response, BitbucketPullRequests.class);
            pullRequests.addAll(page.getValues());
            while (page.getNext() != null && pageNumber < MAX_PAGES) {
                pageNumber++;
                response = getRequest(String.format(url, pageNumber.toString()));
                page = parse(response, BitbucketPullRequests.class);
                pullRequests.addAll(page.getValues());
            }
            return pullRequests;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "invalid repositories response", e);
        }
        return Collections.EMPTY_LIST;
    }

    /** {@inheritDoc} */
    @Override
    @CheckForNull
    public BitbucketPullRequest getPullRequestById(Integer id) {
        String response = getRequest(V2_API_BASE_URL + this.owner + "/" + this.repositoryName + "/pullrequests/" + id);
        try {
            return parse(response, BitbucketPullRequestValue.class);
        } catch(Exception e) {
            LOGGER.log(Level.WARNING, "invalid pull request response.", e);
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    @CheckForNull
    public BitbucketRepository getRepository() {
        if (repositoryName == null) {
            return null;
        }
        String response = getRequest(V2_API_BASE_URL + owner + "/" + repositoryName);
        try {
            return parse(response, BitbucketCloudRepository.class);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "invalid repository response.", e);
        }
        return null;
    }

    public void deletePullRequestComment(String pullRequestId, String commentId) {
        String path = V1_API_BASE_URL + this.owner + "/" + this.repositoryName + "/pullrequests/" + pullRequestId + "/comments/" + commentId;
        deleteRequest(path);
    }

    /** {@inheritDoc} */
    @Override
    public void postCommitComment(String hash, String comment) {
        String path = V1_API_BASE_URL + this.owner + "/" + this.repositoryName + "/changesets/" + hash + "/comments";
        try {
            NameValuePair content = new NameValuePair("content", comment);
            postRequest(path, new NameValuePair[]{ content });

        } catch (UnsupportedEncodingException e) {
            LOGGER.log(Level.SEVERE, "Enconding error", e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Can not comment on commit", e);
        }
    }

    public void deletePullRequestApproval(String pullRequestId) {
        String path = V2_API_BASE_URL + this.owner + "/" + this.repositoryName + "/pullrequests/" + pullRequestId + "/approve";
        deleteRequest(path);
    }

    /** {@inheritDoc} */
    @Override
    public boolean checkPathExists(String branch, String path) {
        int status = getRequestStatus(V1_API_BASE_URL + owner + "/" + repositoryName + "/raw/" + branch + "/" + path);
        return status == HttpStatus.SC_OK;
    }

    /** {@inheritDoc} */
    @Override
    public List<BitbucketCloudBranch> getBranches() {
        String response = getRequest(V1_API_BASE_URL + this.owner + "/" + this.repositoryName + "/branches");
        try {
            return parseBranchesJson(response);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "invalid branches response.", e);
        }
        return Collections.EMPTY_LIST;
    }

    /** {@inheritDoc} */
    @Override
    @CheckForNull
    public BitbucketCommit resolveCommit(String hash) {
        String response = getRequest(V2_API_BASE_URL + owner + "/" + repositoryName + "/commit/" + hash);
        try {
            return parse(response, BitbucketCloudCommit.class);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "cannot resolve commit " + hash + " on " + owner + "/" + repositoryName, e);
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    @CheckForNull
    public String resolveSourceFullHash(BitbucketPullRequest pull) {
        String response = getRequest(V2_API_BASE_URL + pull.getSource().getRepository().getOwnerName() + "/" + 
                pull.getSource().getRepository().getRepositoryName() + "/commit/" + pull.getSource().getCommit().getHash());
        try {
            return parse(response, BitbucketCloudCommit.class).getHash();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "cannot resolve PR commit " + pull.getSource().getCommit().getHash(), e);
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void registerCommitWebHook(BitbucketWebHook hook) {
        try {
            postRequest(V2_API_BASE_URL + owner + "/" + repositoryName + "/hooks", asJson(hook));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "cannot register webhook", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void removeCommitWebHook(BitbucketWebHook hook) {
        if (StringUtils.isBlank(hook.getUuid())) {
            throw new BitbucketException("Hook UUID required");
        }
        try {
            deleteRequest(V2_API_BASE_URL + owner + "/" + repositoryName + "/hooks/" + URLEncoder.encode(hook.getUuid(), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            LOGGER.log(Level.SEVERE, "cannot remove webhook", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<BitbucketRepositoryHook> getWebHooks() {
        String url = V2_API_BASE_URL + this.owner + "/" + this.repositoryName + "/hooks?page=%s&pagelen=50";
        try {
            List<BitbucketRepositoryHook> repositoryHooks = new ArrayList<BitbucketRepositoryHook>();
            Integer pageNumber = 1;
            String response = getRequest(String.format(url, pageNumber.toString()));
            BitbucketRepositoryHooks page = parsePaginatedRepositoryHooks(response);
            repositoryHooks.addAll(page.getValues());
            while (page.getNext() != null && pageNumber < 100) {
                pageNumber++;
                response = getRequest(String.format(url, pageNumber.toString()));
                page = parsePaginatedRepositoryHooks(response);
                repositoryHooks.addAll(page.getValues());
            }
            return repositoryHooks;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "invalid hooks response", e);
        }
        return Collections.EMPTY_LIST;
    }

    @Override
    public void postBuildStatus(BitbucketBuildStatus status) {
        String path = V2_API_BASE_URL + this.owner + "/" + this.repositoryName + "/commit/" + status.getHash() + "/statuses/build";;
        try {
            postRequest(path, serialize(status));
        } catch (UnsupportedEncodingException e) {
            LOGGER.log(Level.SEVERE, "Enconding error", e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Can not comment on commit", e);
        }
    }

    @Override
    public boolean isPrivate() {
        BitbucketRepository repo = getRepository();
        return repo != null ? repo.isPrivate() : false;
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

    /** {@inheritDoc} */
    @Override
    @CheckForNull
    public BitbucketTeam getTeam() {
        try {
            String response = getRequest(V2_TEAMS_API_BASE_URL + owner);
            return parse(response, BitbucketCloudTeam.class);
        } catch (BitbucketRequestException e) {
            if (e.getHttpCode() == HttpStatus.SC_NOT_FOUND) {
                LOGGER.log(Level.FINE, "Team not found: " + owner, e);
            } else {
                throw e;
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "invalid team profile response", e);
        }
        return null;
    }

    /**
     * The role parameter only makes sense when the request is authenticated, so
     * if there is no auth information ({@link #credentials}) the role will be omited.
     */
    @Override
    public List<BitbucketCloudRepository> getRepositories(UserRoleInRepository role) {
        String url;
        if (role != null && getLogin() != null) {
            url = V2_API_BASE_URL + owner + "?role=" + role.getId() + "&page=%s&pagelen=50";
        } else {
            url = V2_API_BASE_URL + owner + "?page=%s&pagelen=50";
        }
        try {
            List<BitbucketCloudRepository> repositories = new ArrayList<BitbucketCloudRepository>();
            Integer pageNumber = 1;
            String response = getRequest(String.format(url, pageNumber.toString()));
            PaginatedBitbucketRepository page = parse(response, PaginatedBitbucketRepository.class);
            repositories.addAll(page.getValues());
            while (page.getNext() != null && pageNumber < MAX_PAGES) {
                pageNumber++;
                response = getRequest(String.format(url, pageNumber.toString()));
                page = parse(response, PaginatedBitbucketRepository.class);
                repositories.addAll(page.getValues());
            }
            return repositories;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "invalid repositories response", e);
        }
        return Collections.EMPTY_LIST;
    }

    /** {@inheritDoc} */
    @Override
    public List<BitbucketCloudRepository> getRepositories() {
        return getRepositories(null);
    }

    private synchronized HttpClient getHttpClient() {
        if (this.client == null) {
            HttpClient client = new HttpClient(connectionManager);
            client.getParams().setConnectionManagerTimeout(10 * 1000);
            client.getParams().setSoTimeout(60 * 1000);

            client.getState().setCredentials(AuthScope.ANY, credentials);
            client.getParams().setAuthenticationPreemptive(true);

            this.client = client;
        }

        return this.client;
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

    private String getRequest(String path) {
        GetMethod httpget = new GetMethod(path);
        HttpClient client = getHttpClient();
        String response = null;
        InputStream responseBodyAsStream = null;
        try {
            executeMethod(client, httpget);
            responseBodyAsStream = httpget.getResponseBodyAsStream();
            response = IOUtils.toString(responseBodyAsStream, "UTF-8");
            if (httpget.getStatusCode() != HttpStatus.SC_OK) {
                throw new BitbucketRequestException(httpget.getStatusCode(), "HTTP request error. Status: " + httpget.getStatusCode() + ": " + httpget.getStatusText() + ".\n" + response);
            }
        } catch (HttpException e) {
            throw new BitbucketRequestException(0, "Communication error: " + e, e);
        } catch (IOException e) {
            throw new BitbucketRequestException(0, "Communication error: " + e, e);
        } finally {
            httpget.releaseConnection();
            if (responseBodyAsStream != null) {
                IOUtils.closeQuietly(responseBodyAsStream);
            }
        }
        if (response == null) {
            throw new BitbucketRequestException(0, "HTTP request error " + httpget.getStatusCode() + ":" + httpget.getStatusText());
        }
        return response;
    }

    private int getRequestStatus(String path) {
        GetMethod httpget = new GetMethod(path);
        HttpClient client = getHttpClient();
        try {
            executeMethod(client, httpget);
            return httpget.getStatusCode();
        } catch (HttpException e) {
            LOGGER.log(Level.SEVERE, "Communication error", e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Communication error", e);
        } finally {
            httpget.releaseConnection();
        }
        return -1;
    }

    private static String getMethodHost(HttpMethod method) {
        try {
            return method.getURI().getHost();
        } catch (URIException e) {
            throw new IllegalStateException("Could not obtain host part for method " + method, e);
        }
    }

    private void deleteRequest(String path) {
        DeleteMethod httppost = new DeleteMethod(path);
        HttpClient client = getHttpClient();
        try {
            executeMethod(client, httppost);
            if (httppost.getStatusCode() != HttpStatus.SC_NO_CONTENT) {
                throw new BitbucketRequestException(httppost.getStatusCode(), "HTTP request error. Status: " + httppost.getStatusCode() + ": " + httppost.getStatusText());
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Communication error", e);
        } finally {
            httppost.releaseConnection();
        }
    }

    private String postRequest(PostMethod httppost) throws UnsupportedEncodingException {
        HttpClient client = getHttpClient();
        String response = "";
        InputStream responseBodyAsStream = null;
        try {
            executeMethod(client, httppost);
            responseBodyAsStream = httppost.getResponseBodyAsStream();
            response = IOUtils.toString(responseBodyAsStream, "UTF-8");
            if (httppost.getStatusCode() != HttpStatus.SC_OK && httppost.getStatusCode() != HttpStatus.SC_CREATED) {
                throw new BitbucketRequestException(httppost.getStatusCode(), "HTTP request error. Status: " + httppost.getStatusCode() + ": " + httppost.getStatusText() + ".\n" + response);
            }
        } catch (HttpException e) {
            throw new BitbucketRequestException(0, "Communication error: " + e, e);
        } catch (IOException e) {
            throw new BitbucketRequestException(0, "Communication error: " + e, e);
        } finally {
            httppost.releaseConnection();
            if (responseBodyAsStream != null) {
                IOUtils.closeQuietly(responseBodyAsStream);
            }
        }
        if (response == null) {
            throw new BitbucketRequestException(0, "HTTP request error " + httppost.getStatusCode() + ":" + httppost.getStatusText());
        }
        return response;

    }

    private <T> String serialize(T o) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(o);
    }

    private String postRequest(String path, String content) throws UnsupportedEncodingException {
        PostMethod httppost = new PostMethod(path);
        httppost.setRequestEntity(new StringRequestEntity(content, "application/json", "UTF-8"));
        return postRequest(httppost);
    }

    private String postRequest(String path, NameValuePair[] params) throws UnsupportedEncodingException {
        PostMethod httppost = new PostMethod(path);
        httppost.setRequestBody(params);
        return postRequest(httppost);
    }

    private List<BitbucketCloudBranch> parseBranchesJson(String response) throws JsonParseException, JsonMappingException, IOException {
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
