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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryProtocol;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryType;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.codehaus.jackson.map.ObjectMapper;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRequestException;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketTeam;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketWebHook;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.UserRoleInRepository;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.branch.BitbucketServerBranch;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.branch.BitbucketServerBranches;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.branch.BitbucketServerCommit;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.pullrequest.BitbucketServerPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.pullrequest.BitbucketServerPullRequests;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketServerProject;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketServerRepositories;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketServerRepository;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import hudson.ProxyConfiguration;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

/**
 * Bitbucket API client.
 * Developed and test with Bitbucket 4.3.2
 */
public class BitbucketServerAPIClient implements BitbucketApi {

    private static final Logger LOGGER = Logger.getLogger(BitbucketServerAPIClient.class.getName());
    private static final String API_BASE_PATH = "/rest/api/1.0";
    private static final String API_REPOSITORIES_PATH = API_BASE_PATH + "/projects/%s/repos?start=%s";
    private static final String API_REPOSITORY_PATH = API_BASE_PATH + "/projects/%s/repos/%s";
    private static final String API_DEFAULT_BRANCH_PATH = API_BASE_PATH + "/projects/%s/repos/%s/branches/default";
    private static final String API_BRANCHES_PATH = API_BASE_PATH + "/projects/%s/repos/%s/branches?start=%s";
    private static final String API_PULL_REQUESTS_PATH = API_BASE_PATH + "/projects/%s/repos/%s/pull-requests?start=%s";
    private static final String API_PULL_REQUEST_PATH = API_BASE_PATH + "/projects/%s/repos/%s/pull-requests/%s";
    private static final String API_BROWSE_PATH = API_REPOSITORY_PATH + "/browse/%s?at=%s";
    private static final String API_COMMITS_PATH = API_REPOSITORY_PATH + "/commits/%s";
    private static final String API_PROJECT_PATH = API_BASE_PATH + "/projects/%s";
    private static final String API_COMMIT_COMMENT_PATH = API_REPOSITORY_PATH + "/commits/%s/comments";

    private static final String API_COMMIT_STATUS_PATH = "/rest/build-status/1.0/commits/%s";

    private static final int MAX_PAGES = 100;

    /**
     * Repository owner.
     * This must be null if {@link #project} is not null.
     */
    private String owner;

    /**
     * Thre repository that this object is managing.
     */
    private String repositoryName;

    /**
     * Indicates if the client is using user-centric API endpoints or project API otherwise.
     */
    private boolean userCentric = false;

    /**
     * Credentials to access API services.
     * Almost @NonNull (but null is accepted for annonymous access).
     */
    private UsernamePasswordCredentials credentials;

    private String baseURL;

    public BitbucketServerAPIClient(String baseURL, String owner, String repositoryName, StandardUsernamePasswordCredentials creds, boolean userCentric) {
        if (creds != null) {
            this.credentials = new UsernamePasswordCredentials(creds.getUsername(), Secret.toString(creds.getPassword()));
        }
        this.userCentric = userCentric;
        this.owner = owner;
        this.repositoryName = repositoryName;
        this.baseURL = baseURL;
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
     * This method returns the appropiate string to be placed in request URLs taking into account if this client
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
    public String getRepositoryUri(@NonNull BitbucketRepositoryType type,
                                   @NonNull BitbucketRepositoryProtocol protocol,
                                   @CheckForNull Integer protocolPortOverride,
                                   @NonNull String owner,
                                   @NonNull String repository) {
        switch (type) {
            case GIT:
                URL url;
                try {
                    url = new URL(baseURL);
                } catch (MalformedURLException e) {
                    throw new IllegalStateException("Server URL is not a valid URL", e);
                }
                StringBuilder result = new StringBuilder();
                switch (protocol) {
                    case HTTP:
                        result.append(url.getProtocol());
                        result.append("://");
                        if (protocolPortOverride != null && protocolPortOverride > 0) {
                            result.append(url.getHost());
                            result.append(':');
                            result.append(protocolPortOverride);
                        } else {
                            result.append(url.getAuthority());
                        }
                        result.append("/scm/");
                        result.append(owner);
                        result.append('/');
                        result.append(repository);
                        result.append(".git");
                        break;
                    case SSH:
                        result.append("ssh://git@");
                        result.append(url.getHost());
                        if (protocolPortOverride  != null && protocolPortOverride > 0) {
                            result.append(':');
                            result.append(protocolPortOverride);
                        }
                        result.append('/');
                        result.append(owner);
                        result.append('/');
                        result.append(repository);
                        result.append(".git");
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported repository protocol: " + protocol);
                }
                return result.toString();
                default:
                    throw new IllegalArgumentException("Unsupported repository type: " + type);
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<BitbucketServerPullRequest> getPullRequests() throws IOException, InterruptedException {
        String url = String.format(API_PULL_REQUESTS_PATH, getUserCentricOwner(), repositoryName, 0);

        try {
            List<BitbucketServerPullRequest> pullRequests = new ArrayList<BitbucketServerPullRequest>();
            Integer pageNumber = 1;
            String response = getRequest(url);
            BitbucketServerPullRequests page = parse(response, BitbucketServerPullRequests.class);
            pullRequests.addAll(page.getValues());
            while (!page.isLastPage() && pageNumber < MAX_PAGES) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                pageNumber++;
                url = String.format(API_PULL_REQUESTS_PATH, getUserCentricOwner(), repositoryName,
                        page.getNextPageStart());
                response = getRequest(url);
                page = parse(response, BitbucketServerPullRequests.class);
                pullRequests.addAll(page.getValues());
            }
            return pullRequests;
        } catch (IOException e) {
            throw new IOException("I/O error when accessing URL: " + url, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public BitbucketPullRequest getPullRequestById(@NonNull Integer id) throws IOException {
        String url = String.format(API_PULL_REQUEST_PATH, getUserCentricOwner(), repositoryName, id);
        String response = getRequest(url);
        try {
            return parse(response, BitbucketServerPullRequest.class);
        } catch (IOException e) {
            throw new IOException("I/O error when accessing URL: " + url, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public BitbucketRepository getRepository() throws IOException {
        if (repositoryName == null) {
            throw new UnsupportedOperationException(
                    "Cannot get a repository from an API instance that is not associated with a repository");
        }
        String url = String.format(API_REPOSITORY_PATH, getUserCentricOwner(), repositoryName);
        String response = getRequest(url);
        try {
            return parse(response, BitbucketServerRepository.class);
        } catch (IOException e) {
            throw new IOException("I/O error when accessing URL: " + url, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postCommitComment(@NonNull String hash, @NonNull String comment) throws IOException {
        postRequest(String.format(API_COMMIT_COMMENT_PATH, getUserCentricOwner(), repositoryName, hash), new NameValuePair[]{ new NameValuePair("text", comment) });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postBuildStatus(@NonNull BitbucketBuildStatus status) throws IOException {
        postRequest(String.format(API_COMMIT_STATUS_PATH, status.getHash()), serialize(status));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkPathExists(@NonNull String branch, @NonNull String path) throws IOException {
        return HttpStatus.SC_OK == getRequestStatus(String.format(API_BROWSE_PATH, getUserCentricOwner(), repositoryName, path, branch));
    }

    @NonNull
    @Override
    public String getDefaultBranch() throws IOException {
        String url = String.format(API_DEFAULT_BRANCH_PATH, getUserCentricOwner(), repositoryName);
        try {
            String response = getRequest(url);
            return parse(response, BitbucketServerBranch.class).getName();
        } catch (IOException e) {
            throw new IOException("I/O error when accessing URL: " + url, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public List<BitbucketServerBranch> getBranches() throws IOException, InterruptedException {
        String url = String.format(API_BRANCHES_PATH, getUserCentricOwner(), repositoryName, 0);

        try {
            List<BitbucketServerBranch> branches = new ArrayList<BitbucketServerBranch>();
            Integer pageNumber = 1;
            String response = getRequest(url);
            BitbucketServerBranches page = parse(response, BitbucketServerBranches.class);
            branches.addAll(page.getValues());
            while (!page.isLastPage() && pageNumber < MAX_PAGES) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                pageNumber++;
                url = String.format(API_BRANCHES_PATH, getUserCentricOwner(), repositoryName, page.getNextPageStart());
                response = getRequest(url);
                page = parse(response, BitbucketServerBranches.class);
                branches.addAll(page.getValues());
            }
            return branches;
        } catch (IOException e) {
            throw new IOException("I/O error when accessing URL: " + url, e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public BitbucketCommit resolveCommit(@NonNull String hash) throws IOException {
        String url = String.format(API_COMMITS_PATH, getUserCentricOwner(), repositoryName, hash);
        try {
            String response = getRequest(url);
            return parse(response, BitbucketServerCommit.class);
        } catch (IOException e) {
            throw new IOException("I/O error when accessing URL: " + url, e);
        }
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public String resolveSourceFullHash(@NonNull BitbucketPullRequest pull) {
        return pull.getSource().getCommit().getHash();
    }

    @Override
    public void registerCommitWebHook(@NonNull BitbucketWebHook hook) {
        // TODO
    }

    @Override
    public void removeCommitWebHook(@NonNull BitbucketWebHook hook) {
        // TODO
    }

    @NonNull
    @Override
    public List<BitbucketWebHook> getWebHooks() {
        // TODO
        return Collections.emptyList();
    }

    /**
     * There is no such Team concept in Bitbucket Server but Project.
     */
    @Override
    public BitbucketTeam getTeam() throws IOException {
        if (userCentric) {
            return null;
        } else {
            String url = String.format(API_PROJECT_PATH, getOwner());
            try {
                String response = getRequest(url);
                return parse(response, BitbucketServerProject.class);
            } catch (IOException e) {
                throw new IOException("I/O error when accessing URL: " + url, e);
            }
        }
    }

    /**
     * The role parameter is ignored for Bitbucket Server.
     */
    @NonNull
    @Override
    public List<BitbucketServerRepository> getRepositories(@CheckForNull UserRoleInRepository role)
            throws IOException, InterruptedException {
        String url = String.format(API_REPOSITORIES_PATH, getUserCentricOwner(), 0);

        try {
            List<BitbucketServerRepository> repositories = new ArrayList<BitbucketServerRepository>();
            Integer pageNumber = 1;
            String response = getRequest(url);
            BitbucketServerRepositories page = parse(response, BitbucketServerRepositories.class);
            repositories.addAll(page.getValues());
            while (!page.isLastPage() && pageNumber < MAX_PAGES) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                pageNumber++;
                url = String.format(API_REPOSITORIES_PATH, getUserCentricOwner(), page.getNextPageStart());
                response = getRequest(url);
                page = parse(response, BitbucketServerRepositories.class);
                repositories.addAll(page.getValues());
            }
            return repositories;
        } catch (IOException e) {
            throw new IOException("I/O error when accessing URL: " + url, e);
        }
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public List<BitbucketServerRepository> getRepositories() throws IOException, InterruptedException {
        return getRepositories(null);
    }

    @Override
    public boolean isPrivate() throws IOException {
        return getRepository().isPrivate();
    }


    private <T> T parse(String response, Class<T> clazz) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(response, clazz);
    }

    private String getRequest(String path) throws IOException {
        GetMethod httpget = new GetMethod(this.baseURL + path);
        HttpClient client = getHttpClient(getMethodHost(httpget));
        try {
            client.executeMethod(httpget);
            String response = new String(httpget.getResponseBody(), "UTF-8");
            if (httpget.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                throw new FileNotFoundException("URL: " + path);
            }
            if (httpget.getStatusCode() != HttpStatus.SC_OK) {
                throw new BitbucketRequestException(httpget.getStatusCode(), "HTTP request error. Status: " + httpget.getStatusCode() + ": " + httpget.getStatusText() + ".\n" + response);
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

    private HttpClient getHttpClient(String host) {
        HttpClient client = new HttpClient();

        client.getParams().setConnectionManagerTimeout(10 * 1000);
        client.getParams().setSoTimeout(60 * 1000);

        client.getState().setCredentials(AuthScope.ANY, credentials);
        client.getParams().setAuthenticationPreemptive(true);

        setClientProxyParams(host, client);
        return client;
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
            final InetSocketAddress proxyAddress = (InetSocketAddress)proxy.address();
            LOGGER.log(Level.FINE, "Jenkins proxy: {0}", proxy.address());
            client.getHostConfiguration().setProxy(proxyAddress.getHostString(), proxyAddress.getPort());
            String username = proxyConfig.getUserName();
            String password = proxyConfig.getPassword();
            if (username != null && !"".equals(username.trim())) {
                LOGGER.log(Level.FINE, "Using proxy authentication (user={0})", username);
                client.getState().setProxyCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password));
            }
        }
    }

    private int getRequestStatus(String path) throws IOException {
        GetMethod httpget = new GetMethod(this.baseURL + path);
        HttpClient client = getHttpClient(getMethodHost(httpget));
        try {
            client.executeMethod(httpget);
            return httpget.getStatusCode();
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

    private <T> String serialize(T o) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(o);
    }

    private String postRequest(String path, NameValuePair[] params) throws IOException {
        PostMethod httppost = new PostMethod(this.baseURL + path);
        httppost.setRequestEntity(new StringRequestEntity(nameValueToJson(params), "application/json", "UTF-8"));
        return postRequest(httppost);
    }

    private String postRequest(String path, String content) throws IOException {
        PostMethod httppost = new PostMethod(this.baseURL + path);
        httppost.setRequestEntity(new StringRequestEntity(content, "application/json", "UTF-8"));
        return postRequest(httppost);
    }

    private String nameValueToJson(NameValuePair[] params) {
        JSONObject o = new JSONObject();
        for (NameValuePair pair : params) {
            o.put(pair.getName(), pair.getValue());
        }
        return o.toString();
    }

    private String postRequest(PostMethod httppost) throws IOException {
        HttpClient client = getHttpClient(getMethodHost(httppost));
        client.getState().setCredentials(AuthScope.ANY, credentials);
        client.getParams().setAuthenticationPreemptive(true);
        try {
            client.executeMethod(httppost);
            if (httppost.getStatusCode() == HttpStatus.SC_NO_CONTENT) {
                // 204, no content
                return "";
            }
            String response = new String(httppost.getResponseBody(), "UTF-8");
            if (httppost.getStatusCode() != HttpStatus.SC_OK && httppost.getStatusCode() != HttpStatus.SC_CREATED) {
                throw new BitbucketRequestException(httppost.getStatusCode(), "HTTP request error. Status: " + httppost.getStatusCode() + ": " + httppost.getStatusText() + ".\n" + response);
            }
            return response;
        } finally {
            httppost.releaseConnection();
        }
    }

}
