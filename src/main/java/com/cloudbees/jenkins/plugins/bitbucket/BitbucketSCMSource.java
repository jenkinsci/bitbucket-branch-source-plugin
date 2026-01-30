/*
 * The MIT License
 *
 * Copyright (c) 2016-2017, CloudBees, Inc., Nikolas Falco
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
package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSourceRequest.BitbucketRevisionFactory;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBranch;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketMirroredRepository;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketMirroredRepositoryDescriptor;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketProject;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRequestException;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketTeam;
import com.cloudbees.jenkins.plugins.bitbucket.api.HasPullRequests;
import com.cloudbees.jenkins.plugins.bitbucket.api.PullRequestBranchType;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpointProvider;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.UserRoleInRepository;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.impl.avatars.BitbucketRepoAvatarMetadataAction;
import com.cloudbees.jenkins.plugins.bitbucket.impl.endpoint.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.extension.BitbucketEnvVarExtension;
import com.cloudbees.jenkins.plugins.bitbucket.impl.extension.GitClientAuthenticatorExtension;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketApiUtils;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketApiUtils.BitbucketSupplier;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.DateUtils;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.MirrorListSupplier;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.URLUtils;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.BitbucketServerAPIClient;
import com.cloudbees.jenkins.plugins.bitbucket.server.client.repository.BitbucketServerRepository;
import com.cloudbees.jenkins.plugins.bitbucket.trait.BranchDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.ForkPullRequestDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.OriginPullRequestDiscoveryTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.SSHCheckoutTrait;
import com.cloudbees.jenkins.plugins.bitbucket.trait.ShowBitbucketAvatarTrait;
import com.cloudbees.jenkins.plugins.bitbucket.util.BitbucketCredentialsUtils;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.damnhandy.uri.template.UriTemplate;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.console.HyperlinkNote;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.security.AccessControlled;
import hudson.util.FormFillFailure;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import jenkins.plugins.git.GitTagSCMHead;
import jenkins.plugins.git.traits.GitBrowserSCMSourceTrait;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.api.SCMSourceEvent;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.metadata.ContributorMetadataAction;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.api.metadata.PrimaryInstanceMetadataAction;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMSourceRequest.IntermediateLambda;
import jenkins.scm.api.trait.SCMSourceRequest.ProbeLambda;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.api.trait.SCMTrait;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.TagSCMHeadCategory;
import jenkins.scm.impl.UncategorizedSCMHeadCategory;
import jenkins.scm.impl.form.NamedArrayList;
import jenkins.scm.impl.trait.Discovery;
import jenkins.scm.impl.trait.Selection;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.eclipse.jgit.lib.Constants;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.accmod.restrictions.ProtectedExternally;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import static com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketApiUtils.getFromBitbucket;

/**
 * SCM source implementation for Bitbucket.
 *
 * It provides a way to discover/retrieve branches and pull requests through the Bitbucket REST API
 * which is much faster than the plain Git SCM source implementation.
 */
public class BitbucketSCMSource extends SCMSource {
    private static final Logger LOGGER = Logger.getLogger(BitbucketSCMSource.class.getName());
    private static final String CLOUD_REPO_TEMPLATE = "{/owner,repo}";
    private static final String SERVER_REPO_TEMPLATE = "/projects{/owner}/repos{/repo}";

    /** How long to delay events received from Bitbucket in order to allow the API caches to sync. */
    private static /*mostly final*/ int eventDelaySeconds =
        Math.min(
            300,
            Math.max(
                0, Integer.getInteger(BitbucketSCMSource.class.getName() + ".eventDelaySeconds", 5)));

    /**
     * Bitbucket URL.
     */
    @NonNull
    private String serverUrl = BitbucketCloudEndpoint.SERVER_URL;

    /**
     * Credentials used to access the Bitbucket REST API.
     */
    @CheckForNull
    private String credentialsId;

    /**
     * Bitbucket mirror id
     */
    @CheckForNull
    private String mirrorId;

    /**
     * Repository owner.
     * Used to build the repository URL.
     */
    @NonNull
    private final String repoOwner;

    /**
     * Repository name.
     * Used to build the repository URL.
     */
    @NonNull
    private final String repository;

    /**
     * The behaviours to apply to this source.
     */
    @NonNull
    private List<SCMSourceTrait> traits;

    /**
     * The cache of pull request titles for each open PR.
     */
    @CheckForNull
    private transient /*effectively final*/ Map<String, String> pullRequestTitleCache;
    /**
     * The cache of pull request contributors for each open PR.
     */
    @CheckForNull
    private transient /*effectively final*/ Map<String, ContributorMetadataAction> pullRequestContributorCache;
    /**
     * The cache of the primary clone links.
     */
    @CheckForNull
    private transient List<BitbucketHref> primaryCloneLinks = null;
    /**
     * The cache of the mirror clone links.
     */
    @CheckForNull
    private transient List<BitbucketHref> mirrorCloneLinks = null;

    /**
     * Constructor.
     *
     * @param repoOwner  the repository owner.
     * @param repository the repository name.
     * @since 2.2.0
     */
    @DataBoundConstructor
    public BitbucketSCMSource(@NonNull String repoOwner, @NonNull String repository) {
        this.serverUrl = BitbucketCloudEndpoint.SERVER_URL;
        this.repoOwner = repoOwner;
        this.repository = repository;
        this.traits = new ArrayList<>();
    }

    /**
     * Legacy Constructor.
     *
     * @param id         the id.
     * @param repoOwner  the repository owner.
     * @param repository the repository name.
     * @deprecated use {@link #BitbucketSCMSource(String, String)} and {@link #setId(String)}
     */
    @Deprecated
    public BitbucketSCMSource(@CheckForNull String id, @NonNull String repoOwner, @NonNull String repository) {
        this(repoOwner, repository);
        setId(id);
        traits.add(new BranchDiscoveryTrait(true, true));
        traits.add(new OriginPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE)));
        traits.add(new ForkPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE),
                new ForkPullRequestDiscoveryTrait.TrustTeamForks()));
    }

    @CheckForNull
    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(@CheckForNull String credentialsId) {
        this.credentialsId = Util.fixEmpty(credentialsId);
    }

    public String getMirrorId() {
        return mirrorId;
    }

    @DataBoundSetter
    public void setMirrorId(String mirrorId) {
        this.mirrorId = Util.fixEmpty(mirrorId);
    }

    @NonNull
    public String getRepoOwner() {
        return repoOwner;
    }

    @NonNull
    public String getRepository() {
        return repository;
    }

    @NonNull
    public String getServerUrl() {
        return serverUrl;
    }

    @DataBoundSetter
    public void setServerUrl(@CheckForNull String serverURL) {
        if (serverURL == null) {
            this.serverUrl = BitbucketEndpointConfiguration.get().getDefaultEndpoint().getServerURL();
        } else {
            this.serverUrl = Util.fixNull(URLUtils.normalizeURL(serverURL));
        }
    }

    @Override
    @NonNull
    public List<SCMSourceTrait> getTraits() {
        return Collections.unmodifiableList(traits);
    }

    @Override
    @DataBoundSetter
    public void setTraits(@CheckForNull List<SCMSourceTrait> traits) {
        this.traits = new ArrayList<>(Util.fixNull(traits));
    }

    public BitbucketApi buildBitbucketClient() {
        return buildBitbucketClient(repoOwner, repository);
    }

    public BitbucketApi buildBitbucketClient(PullRequestSCMHead head) {
        return buildBitbucketClient(head.getRepoOwner(), head.getRepository());
    }

    public BitbucketApi buildBitbucketClient(String repoOwner, String repository) {
        return BitbucketApiFactory.newInstance(getServerUrl(), authenticator(), repoOwner, null, repository);
    }

    @Override
    public void afterSave() {
        try (BitbucketApi client = buildBitbucketClient()) {
            gatherPrimaryCloneLinks(client);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,
                    "Could not determine clone links of " + getRepoOwner() + "/" + getRepository() +
                    " on " + getServerUrl() + " for " + getOwner() + " falling back to generated links", e);
        }
    }

    private void gatherPrimaryCloneLinks(@NonNull BitbucketApi apiClient) throws IOException {
        BitbucketRepository r = apiClient.getRepository();
        Map<String, List<BitbucketHref>> links = r.getLinks();
        if (links != null && links.containsKey("clone")) {
            setPrimaryCloneLinks(links.get("clone"));
        }
    }

    @Override
    protected void retrieve(@CheckForNull SCMSourceCriteria criteria, @NonNull SCMHeadObserver observer,
                            @CheckForNull SCMHeadEvent<?> event, @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        Logger.getLogger("JENKINS-76351").warning("Received event " + event + " to source " + repository);
        try (BitbucketSCMSourceRequest request = new BitbucketSCMSourceContext(criteria, observer)
                .withTraits(traits)
                .newRequest(this, listener)) {
            StandardCredentials scanCredentials = credentials();
            if (scanCredentials == null) {
                listener.getLogger().format("Connecting to %s with no credentials, anonymous access%n", getServerUrl());
            } else {
                Optional.ofNullable(getOwner()).ifPresent(item -> CredentialsProvider.track(item, scanCredentials));
                listener.getLogger().format("Connecting to %s using %s%n", getServerUrl(),
                        CredentialsNameProvider.name(scanCredentials));
            }
            gatherPrimaryCloneLinks(buildBitbucketClient());

            // populate the request with its data sources
            if (request.isFetchPRs() && event instanceof HasPullRequests hasPrEvent) {
                request.setPullRequests(getBitbucketPullRequestsFromEvent(hasPrEvent, listener));
            }
            // now server the request
            if (request.isFetchPRs() && !request.isComplete()) {
                // Search pull requests
                retrievePullRequests(request);
            }
            if (request.isFetchBranches() && !request.isComplete()) {
                // Search branches
                retrieveBranches(request);
            }
            if (request.isFetchTags() && !request.isComplete()) {
                // Search tags
                retrieveTags(request);
            }
        }
    }

    private Iterable<BitbucketPullRequest> getBitbucketPullRequestsFromEvent(@NonNull HasPullRequests incomingPrEvent,
                                                                             @NonNull TaskListener listener) throws IOException, InterruptedException {
        Collection<BitbucketPullRequest> initializedPRs = new HashSet<>();
        try (BitbucketApi bitBucket = buildBitbucketClient()) {
            Iterable<BitbucketPullRequest> pullRequests = incomingPrEvent.getPullRequests(BitbucketSCMSource.this);
            for (BitbucketPullRequest pr : pullRequests) {
                // ensure that the PR is properly initialised via /changes API
                // see BitbucketServerAPIClient.setupPullRequest()
                initializedPRs.add(bitBucket.getPullRequestById(Integer.parseInt(pr.getId())));
                listener.getLogger().format("Initialized PR: %s%n", pr.getLink());
            }
        }
        return initializedPRs;
    }

    private void retrievePullRequests(final BitbucketSCMSourceRequest request) throws IOException, InterruptedException {
        final String fullName = repoOwner + "/" + repository;

        @SuppressWarnings("serial")
        class Skip extends IOException {
        }

        try (final BitbucketApi originClient = buildBitbucketClient()) {
            if (request.isSkipPublicPRs() && !originClient.isPrivate()) {
                request.listener().getLogger().printf("Skipping pull requests for %s (public repository)%n", fullName);
                return;
            }
        }

        request.listener().getLogger().printf("Looking up %s for pull requests%n", fullName);
        final Set<String> livePRs = new HashSet<>();
        int count = 0;
        Map<Boolean, Set<ChangeRequestCheckoutStrategy>> strategies = request.getPRStrategies();
        for (final BitbucketPullRequest pull : request.getPullRequests()) {
            String originalBranchName = pull.getSource().getBranch().getName();
            request.listener().getLogger().printf(
                    "Checking PR-%s from %s and %s %s%n",
                    pull.getId(),
                    pull.getSource().getRepository().getFullName(),
                    pull.getSource().getBranchType() == PullRequestBranchType.TAG ? "tag" : "branch",
                    originalBranchName
            );
            boolean fork = !Strings.CI.equals(fullName, pull.getSource().getRepository().getFullName());
            String pullRepoOwner = pull.getSource().getRepository().getOwnerName();
            String pullRepository = pull.getSource().getRepository().getRepositoryName();
            final BitbucketApi forkClient = fork && BitbucketApiUtils.isCloud(getServerUrl())
                    ? BitbucketApiFactory.newInstance(
                        getServerUrl(),
                        authenticator(),
                        pullRepoOwner,
                        null,
                        pullRepository)
                    : null;
            count++;
            livePRs.add(pull.getId());
            getPullRequestTitleCache().put(pull.getId(), StringUtils.defaultString(pull.getTitle()));
            getPullRequestContributorCache().put(pull.getId(),
                    new ContributorMetadataAction(pull.getAuthorIdentifier(), pull.getAuthorLogin(), pull.getAuthorEmail()));
            try {
                // We store resolved hashes here so to avoid resolving the commits multiple times
                for (final ChangeRequestCheckoutStrategy strategy : strategies.get(fork)) {
                    String branchName = "PR-" + pull.getId();
                    if (strategies.get(fork).size() > 1) {
                        branchName = "PR-" + pull.getId() + "-" + strategy.name().toLowerCase(Locale.ENGLISH);
                    }
                    PullRequestSCMHead head = new PullRequestSCMHead( //
                        branchName, //
                        pullRepoOwner, //
                        pullRepository, //
                        originalBranchName, //
                        pull, //
                        originOf(pullRepoOwner, pullRepository), //
                        strategy
                    );

                    // use branch instead of commit to postpone closure initialisation
                    IntermediateLambda<BitbucketCommit> intermediateFactory = () -> new BranchHeadCommit(pull.getSource().getBranch());
                    ProbeLambda<SCMHead, BitbucketCommit> probeFactory = forkClient != null
                            ? request.buildProbeLamda(forkClient)
                            : request.defaultProbeLamda();
                    BitbucketRevisionFactory<BitbucketCommit> revisionFactory = new BitbucketRevisionFactory<BitbucketCommit>(null) {
                        @Override
                        public SCMRevision create(SCMHead head, BitbucketCommit sourceCommit) throws IOException, InterruptedException {
                            try {
                                // use branch instead of commit to postpone closure initialisation
                                BranchHeadCommit targetCommit = new BranchHeadCommit(pull.getDestination().getBranch());
                                return super.create(head, sourceCommit, targetCommit);
                            } catch (BitbucketRequestException e) {
                                if (BitbucketApiUtils.isCloud(getServerUrl()) && e.getHttpCode() == 403) {
                                    request.listener().getLogger().printf( //
                                            "Skipping %s because of %s%n", //
                                            pull.getId(), //
                                            HyperlinkNote.encodeTo("https://bitbucket.org/site/master" //
                                                    + "/issues/5814/reify-pull-requests-by-making-them-a-ref", //
                                                    "a permission issue accessing pull requests from forks"));
                                    throw new Skip();
                                }
                                // https://bitbucket.org/site/master/issues/5814/reify-pull-requests-by-making-them-a-ref
                                e.printStackTrace(request.listener().getLogger());
                                if (e.getHttpCode() == 403) {
                                    // the credentials do not have permission, so we should not observe the
                                    // PR ever the PR is dead to us, so this is the one case where we can
                                    // squash the exception.
                                    throw new Skip();
                                }
                                throw e;
                            }
                        }
                    };
                    if (request.process(head, intermediateFactory, probeFactory, revisionFactory, request.defaultWitness())) {
                        request.listener().getLogger().format("%n  %d pull requests were processed (query completed)%n", count);
                        return;
                    }
                }
            } catch (Skip e) {
                request.listener().getLogger().println(
                        "Do not have permission to view PR from " + pull.getSource().getRepository()
                                .getFullName()
                                + " and branch "
                                + originalBranchName);
                continue;
            } finally {
                if (forkClient != null) {
                    forkClient.close();
                }
            }
        }
        request.listener().getLogger().format("%n  %d pull requests were processed%n", count);
        getPullRequestTitleCache().keySet().retainAll(livePRs);
        getPullRequestContributorCache().keySet().retainAll(livePRs);
    }

    private void retrieveBranches(final BitbucketSCMSourceRequest request) throws IOException, InterruptedException {
        String fullName = repoOwner + "/" + repository;
        request.listener().getLogger().println("Looking up " + fullName + " for branches");

        int count = 0;
        for (final BitbucketBranch branch : request.getBranches()) {
            request.listener().getLogger().println("Checking branch " + branch.getName() + " from " + fullName);
            count++;
            BranchSCMHead head = new BranchSCMHead(branch.getName());
            if (request.process(head, (IntermediateLambda<BitbucketCommit>) () -> new BranchHeadCommit(branch))) {
                request.listener().getLogger().format("%n  %d branches were processed (query completed)%n", count);
                return;
            }
        }
        request.listener().getLogger().format("%n  %d branches were processed%n", count);
    }


    private void retrieveTags(final BitbucketSCMSourceRequest request) throws IOException, InterruptedException {
        String fullName = repoOwner + "/" + repository;
        request.listener().getLogger().println("Looking up " + fullName + " for tags");

        int count = 0;
        for (final BitbucketBranch tag : request.getTags()) {
            request.listener().getLogger().println("Checking tag " + tag.getName() + " from " + fullName);
            count++;
            BitbucketTagSCMHead head = new BitbucketTagSCMHead(tag.getName(), tag.getDateMillis());
            if (request.process(head, tag::getRawNode)) {
                request.listener().getLogger().format("%n  %d tags were processed (query completed)%n", count);
                return;
            }
        }
        request.listener().getLogger().format("%n  %d tags were processed%n", count);
    }

    @Override
    protected SCMRevision retrieve(SCMHead head, TaskListener listener) throws IOException, InterruptedException {
        try (BitbucketApi client = buildBitbucketClient()) {
            if (head instanceof PullRequestSCMHead prHead) {
                BitbucketCommit sourceRevision;
                BitbucketCommit targetRevision;

                if (BitbucketApiUtils.isCloud(client)) {
                    // Bitbucket Cloud /pullrequests/{id} API endpoint only returns short commit IDs of the source
                    // and target branch. We therefore retrieve the branches directly
                    BitbucketBranch targetBranch = client.getBranch(prHead.getTarget().getName());
                    if(targetBranch == null) {
                        listener.getLogger().format("No branch found in %s/%s with name [%s]",
                            repoOwner, repository, prHead.getTarget().getName());
                        return null;
                    }

                    targetRevision = findCommit(targetBranch, listener);
                    if (targetRevision == null) {
                        listener.getLogger().format("No branch found in %s/%s with name [%s]",
                            repoOwner, repository, prHead.getTarget().getName());
                        return null;
                    }

                    // Retrieve the source branch commit
                    BitbucketBranch branch;
                    if (head.getOrigin() == SCMHeadOrigin.DEFAULT) {
                        branch = client.getBranch(prHead.getBranchName());
                    } else {
                        // In case of a forked branch, retrieve the branch as that owner
                        try (BitbucketApi forkClient = buildBitbucketClient(prHead)) {
                            branch = forkClient.getBranch(prHead.getBranchName());
                        }
                    }

                    if(branch == null) {
                        listener.getLogger().format("No branch found in %s/%s with name [%s]",
                            repoOwner, repository, head.getName());
                        return null;
                    }

                    sourceRevision = findCommit(branch, listener);

                } else {
                    BitbucketPullRequest pr;
                    try {
                        pr = client.getPullRequestById(Integer.parseInt(prHead.getId()));
                    } catch (NumberFormatException nfe) {
                        LOGGER.log(Level.WARNING, "Cannot parse the PR id {0}", prHead.getId());
                        return null;
                    }

                    targetRevision = findPRDestinationCommit(pr, listener);

                    if (targetRevision == null) {
                        listener.getLogger().format("No branch found in %s/%s with name [%s]",
                            repoOwner, repository, prHead.getTarget().getName());
                        return null;
                    }

                    sourceRevision = findPRSourceCommit(pr, listener);
                }

                if (sourceRevision == null) {
                    listener.getLogger().format("No revision found in %s/%s for PR-%s [%s]",
                        prHead.getRepoOwner(),
                        prHead.getRepository(),
                        prHead.getId(),
                        prHead.getBranchName());
                    return null;
                }

                return new PullRequestSCMRevision(
                    prHead,
                    new BitbucketGitSCMRevision(prHead.getTarget(), targetRevision),
                    new BitbucketGitSCMRevision(prHead, sourceRevision)
                );
            } else if (head instanceof BitbucketTagSCMHead tagHead) {
                BitbucketBranch tag = client.getTag(tagHead.getName());
                if(tag == null) {
                    listener.getLogger().format( "No tag found in %s/%s with name [%s]",
                        repoOwner, repository, head.getName());
                    return null;
                }
                BitbucketCommit revision = findCommit(tag, listener);
                if (revision == null) {
                    listener.getLogger().format( "No revision found in %s/%s with name [%s]",
                        repoOwner, repository, head.getName());
                    return null;
                }
                return new BitbucketTagSCMRevision(tagHead, revision);
            } else {
                BitbucketBranch branch = client.getBranch(head.getName());
                if(branch == null) {
                    listener.getLogger().format("No branch found in %s/%s with name [%s]",
                        repoOwner, repository, head.getName());
                    return null;
                }
                BitbucketCommit revision = findCommit(branch, listener);
                if (revision == null) {
                    listener.getLogger().format("No revision found in %s/%s with name [%s]",
                        repoOwner, repository, head.getName());
                    return null;
                }
                return new BitbucketGitSCMRevision(head, revision);
            }
        } catch (IOException e) {
            // here we only want to display the job name to have it in the log
            BitbucketRequestException bre = BitbucketApiUtils.unwrap(e);
            if (bre != null) {
                SCMSourceOwner scmSourceOwner = getOwner();
                if (bre.getHttpCode() == 401 && scmSourceOwner != null) {
                    LOGGER.log(Level.WARNING, "BitbucketRequestException: Authz error. Status: 401 for Item '{0}' using credentialId '{1}'",
                        new Object[]{scmSourceOwner.getFullDisplayName(), getCredentialsId()});
                }
            }
            throw e;
        }
    }

    private BitbucketCommit findCommit(@NonNull BitbucketBranch branch, TaskListener listener) {
        String revision = branch.getRawNode();
        if (revision == null) {
            if (BitbucketCloudEndpoint.SERVER_URL.equals(getServerUrl())) {
                listener.getLogger().format("Cannot resolve the hash of the revision in branch %s%n",
                    branch.getName());
            } else {
                listener.getLogger().format("Cannot resolve the hash of the revision in branch %s. "
                        + "Perhaps you are using Bitbucket Server previous to 4.x%n",
                    branch.getName());
            }
            return null;
        }
        return new BranchHeadCommit(branch);
    }

    private BitbucketCommit findPRSourceCommit(BitbucketPullRequest pr, TaskListener listener) {
        // if I use getCommit() the branch closure is trigger immediately
        BitbucketBranch branch = pr.getSource().getBranch();
        String hash = branch.getRawNode();
        if (hash == null) {
            if (BitbucketCloudEndpoint.SERVER_URL.equals(getServerUrl())) {
                listener.getLogger().format("Cannot resolve the hash of the revision in PR-%s%n",
                    pr.getId());
            } else {
                listener.getLogger().format("Cannot resolve the hash of the revision in PR-%s. "
                        + "Perhaps you are using Bitbucket Server previous to 4.x%n",
                    pr.getId());
            }
            return null;
        }
        return new BranchHeadCommit(branch);
    }

    private BitbucketCommit findPRDestinationCommit(BitbucketPullRequest pr, TaskListener listener) {
        // if I use getCommit() the branch closure is trigger immediately
        BitbucketBranch branch = pr.getDestination().getBranch();
        String hash = branch.getRawNode();
        if (hash == null) {
            if (BitbucketCloudEndpoint.SERVER_URL.equals(getServerUrl())) {
                listener.getLogger().format("Cannot resolve the hash of the revision in PR-%s%n",
                    pr.getId());
            } else {
                listener.getLogger().format("Cannot resolve the hash of the revision in PR-%s. "
                        + "Perhaps you are using Bitbucket Server previous to 4.x%n",
                    pr.getId());
            }
            return null;
        }
        return new BranchHeadCommit(branch);
    }

    @Override
    public SCM build(@NonNull SCMHead head, @CheckForNull SCMRevision revision) {
        initCloneLinks();

        SSHCheckoutTrait sshTrait = SCMTrait.find(traits, SSHCheckoutTrait.class);
        String checkoutCredentialsId = sshTrait != null ? sshTrait.getCredentialsId() : credentialsId;

        BitbucketGitSCMBuilder scmBuilder = new BitbucketGitSCMBuilder(this, head, revision, checkoutCredentialsId)
                .withExtension(new BitbucketEnvVarExtension(getRepoOwner(), getRepository(), getProjectKey(), getServerUrl()))
                .withCloneLinks(primaryCloneLinks, mirrorCloneLinks)
                .withTraits(traits);

        // checkoutURL must be calculated after set withCloneLinks and credentials
        String checkoutURL = scmBuilder.remote();
        String scmOwner = Optional.ofNullable(getOwner())
                .map(SCMSourceOwner::getFullName)
                .orElse(null);
        return scmBuilder
                .withExtension(new GitClientAuthenticatorExtension(checkoutURL, serverUrl, scmOwner, sshTrait != null ? null : checkoutCredentialsId))
                .build();
    }

    @CheckForNull
    @Restricted(ProtectedExternally.class)
    protected String getProjectKey() {
        String projectKey = null;
        try {
            BitbucketProject project = buildBitbucketClient().getRepository().getProject();
            if (project != null) {
                projectKey = project.getKey();
            }
        } catch (IOException e) {
            LOGGER.severe("Failure getting the project key of repository " + getRepository() + " : " + e.getMessage());
        }
        return projectKey;
    }

    private void setPrimaryCloneLinks(List<BitbucketHref> links) {
        links.forEach(link -> {
            if (StringUtils.startsWithIgnoreCase(link.getName(), "http")) {
                // Remove the username from URL because it will be set into the GIT_URL variable
                // credentials used to git clone or push/pull operation could be different than this (for example SSH)
                // and will run into a failure
                // Restore the behaviour before mirror link feature.
                link.setHref(URLUtils.removeAuthority(link.getHref()));
            }
        });
        primaryCloneLinks = links;
    }

    @NonNull
    @Override
    public SCMRevision getTrustedRevision(@NonNull SCMRevision revision, @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        if (revision instanceof PullRequestSCMRevision prRevision) {
            PullRequestSCMHead head = (PullRequestSCMHead) revision.getHead();

            try (BitbucketSCMSourceRequest request = new BitbucketSCMSourceContext(null, SCMHeadObserver.none())
                    .withTraits(traits)
                    .newRequest(this, listener)) {
                if (request.isTrusted(head)) {
                    return revision;
                }
            }
            listener.getLogger().format("Loading trusted files from base branch %s at %s rather than %s%n",
                    head.getTarget().getName(), prRevision.getTarget(), prRevision.getPull());
            return prRevision.getTarget();
        }
        return revision;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @CheckForNull
    /* package */ StandardCredentials credentials() {
        return BitbucketCredentialsUtils.lookupCredentials(
                getOwner(),
                getServerUrl(),
                getCredentialsId(),
                StandardCredentials.class
        );
    }

    @CheckForNull
    /* package */ BitbucketAuthenticator authenticator() {
        return AuthenticationTokens.convert(BitbucketAuthenticator.authenticationContext(getServerUrl()), credentials());
    }

    @NonNull
    @Override
    protected List<Action> retrieveActions(@SuppressWarnings("rawtypes") @CheckForNull SCMSourceEvent event,
                                           @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        // TODO when we have support for trusted events, use the details from event if event was from trusted source
        List<Action> result = new ArrayList<>();
        try (BitbucketApi client = buildBitbucketClient()) {
            gatherPrimaryCloneLinks(client);
            BitbucketRepository repo = client.getRepository();
            result.add(new BitbucketRepoAvatarMetadataAction(showAvatar() ? repo : null));
            String defaultBranch = client.getDefaultBranch();
            if (StringUtils.isNotBlank(defaultBranch)) {
                result.add(new BitbucketDefaultBranch(repoOwner, repository, defaultBranch));
            }
            UriTemplate template;
            if (BitbucketApiUtils.isCloud(getServerUrl())) {
                template = UriTemplate.fromTemplate(getServerUrl() + CLOUD_REPO_TEMPLATE);
            } else {
                template = UriTemplate.fromTemplate(getServerUrl() + SERVER_REPO_TEMPLATE);
            }
            String url = template
                .set("owner", repoOwner)
                .set("repo", repository)
                .expand();
            result.add(new BitbucketLink("icon-bitbucket-repo", url));
            result.add(new ObjectMetadataAction(repo.getRepositoryName(), null, url));
        }
        return result;
    }

    private boolean showAvatar() {
        return SCMTrait.find(traits, ShowBitbucketAvatarTrait.class) != null;
    }

    @NonNull
    @Override
    protected List<Action> retrieveActions(@NonNull SCMHead head,
                                           @SuppressWarnings("rawtypes") @CheckForNull SCMHeadEvent event,
                                           @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        // TODO when we have support for trusted events, use the details from event if event was from trusted source
        List<Action> result = new ArrayList<>();
        UriTemplate template;
        String title = null;
        if (BitbucketApiUtils.isCloud(getServerUrl())) {
            String resourceName;
            String resourceId;
            if (head instanceof PullRequestSCMHead prHead) {
                resourceName = "pull-requests";
                resourceId = prHead.getId();
            } else if (head instanceof GitTagSCMHead) {
                resourceName = "commits";
                resourceId = head.getName();
            } else {
                resourceName = "branch";
                resourceId = head.getName();
            }
            template = UriTemplate.fromTemplate(getServerUrl() + CLOUD_REPO_TEMPLATE + "/{resourceName}/{resourceId}")
                    .set("owner", repoOwner)
                    .set("repo", repository)
                    .set("resourceName", resourceName)
                    .set("resourceId", resourceId);
        } else {
            if (head instanceof PullRequestSCMHead prHead) {
                template = UriTemplate
                        .fromTemplate(getServerUrl() + SERVER_REPO_TEMPLATE + "/pull-requests/{id}/overview")
                        .set("owner", repoOwner)
                        .set("repo", repository)
                        .set("id", prHead.getId());
            } else {
                template = UriTemplate
                        .fromTemplate(getServerUrl() + SERVER_REPO_TEMPLATE + "/compare/commits{?sourceBranch}")
                        .set("owner", repoOwner)
                        .set("repo", repository)
                        .set("sourceBranch", Constants.R_HEADS + head.getName());
            }
        }
        if (head instanceof PullRequestSCMHead prHead) {
            title = getPullRequestTitleCache().get(prHead.getId());
            ContributorMetadataAction contributor = getPullRequestContributorCache().get(prHead.getId());
            if (contributor != null) {
                result.add(contributor);
            }
        }
        String url = template.expand();
        result.add(new BitbucketLink("icon-bitbucket-branch", url));
        result.add(new ObjectMetadataAction(title, null, url));
        SCMSourceOwner owner = getOwner();
        if (owner instanceof Actionable actionable) {
            for (BitbucketDefaultBranch p : actionable.getActions(BitbucketDefaultBranch.class)) {
                if (Strings.CI.equals(getRepoOwner(), p.getRepoOwner())
                        && Objects.equals(repository, p.getRepository())
                        && Objects.equals(p.getDefaultBranch(), head.getName())) {
                    result.add(new PrimaryInstanceMetadataAction());
                    break;
                }
            }
        }
        return result;
    }

    @NonNull
    private synchronized Map<String, String> getPullRequestTitleCache() {
        if (pullRequestTitleCache == null) {
            pullRequestTitleCache = new ConcurrentHashMap<>();
        }
        return pullRequestTitleCache;
    }

    @NonNull
    private synchronized Map<String, ContributorMetadataAction> getPullRequestContributorCache() {
        if (pullRequestContributorCache == null) {
            pullRequestContributorCache = new ConcurrentHashMap<>();
        }
        return pullRequestContributorCache;
    }

    @NonNull
    public SCMHeadOrigin originOf(@NonNull String repoOwner, @NonNull String repository) {
        if (this.repository.equalsIgnoreCase(repository)) {
            if (Strings.CI.equals(this.repoOwner, repoOwner)) {
                return SCMHeadOrigin.DEFAULT;
            }
            return new SCMHeadOrigin.Fork(repoOwner);
        }
        return new SCMHeadOrigin.Fork(repoOwner + "/" + repository);
    }


    /**
     * Returns how long to delay events received from Bitbucket in order to allow the API caches to sync.
     *
     * @return how long to delay events received from Bitbucket in order to allow the API caches to sync.
     */
    public static int getEventDelaySeconds() {
        return eventDelaySeconds;
    }

    /**
     * Sets how long to delay events received from Bitbucket in order to allow the API caches to sync.
     *
     * @param eventDelaySeconds number of seconds to delay, will be restricted into a value within the
     *     range {@code [0,300]} inclusive
     */
    @Restricted(NoExternalUse.class) // to allow configuration from system groovy console
    public static void setEventDelaySeconds(int eventDelaySeconds) {
        BitbucketSCMSource.eventDelaySeconds = Math.min(300, Math.max(0, eventDelaySeconds));
    }

    private void initCloneLinks() {
        if (primaryCloneLinks == null) {
            BitbucketApi bitbucket = buildBitbucketClient();
            initPrimaryCloneLinks(bitbucket);
            if (mirrorId != null && mirrorCloneLinks == null) {
                initMirrorCloneLinks((BitbucketServerAPIClient) bitbucket, mirrorId);
            }
        }
        if (mirrorId != null && mirrorCloneLinks == null) {
            BitbucketApi bitbucket = buildBitbucketClient();
            initMirrorCloneLinks((BitbucketServerAPIClient) bitbucket, mirrorId);
        }
    }

    private void initMirrorCloneLinks(BitbucketServerAPIClient bitbucket, String mirrorIdLocal) {
        try {
            // Mirrors are supported only by Bitbucket Server
            BitbucketServerRepository r = (BitbucketServerRepository) bitbucket.getRepository();
            List<BitbucketMirroredRepositoryDescriptor> mirrors = bitbucket.getMirrors(r.getId());
            BitbucketMirroredRepositoryDescriptor mirroredRepositoryDescriptor = mirrors.stream()
                .filter(it -> mirrorIdLocal.equals(it.getMirrorServer().getId()))
                .findFirst()
                .orElseThrow(() ->
                    new IllegalStateException("Could not find mirror descriptor for mirror id " + mirrorIdLocal)
                );
            if (!mirroredRepositoryDescriptor.getMirrorServer().isEnabled()) {
                throw new IllegalStateException("Mirror is disabled for mirror id " + mirrorIdLocal);
            }
            Map<String, List<BitbucketHref>> mirrorDescriptorLinks = mirroredRepositoryDescriptor.getLinks();
            if (mirrorDescriptorLinks == null) {
                throw new IllegalStateException("There is no repository descriptor links for mirror id " + mirrorIdLocal);
            }
            List<BitbucketHref> self = mirrorDescriptorLinks.get("self");
            if (self == null || self.isEmpty()) {
                throw new IllegalStateException("There is no self-link for mirror id " + mirrorIdLocal);
            }
            String selfLink = self.get(0).getHref();
            BitbucketMirroredRepository mirroredRepository = bitbucket.getMirroredRepository(selfLink);
            if (!mirroredRepository.isAvailable()) {
                throw new IllegalStateException("Mirrored repository is not available for mirror id " + mirrorIdLocal);
            }
            Map<String, List<BitbucketHref>> mirroredRepositoryLinks = mirroredRepository.getLinks();
            if (mirroredRepositoryLinks == null) {
                throw new IllegalStateException("There is no mirrored repository links for mirror id " + mirrorIdLocal);
            }
            List<BitbucketHref> mirroredRepositoryCloneLinks = mirroredRepositoryLinks.get("clone");
            if (mirroredRepositoryCloneLinks == null) {
                throw new IllegalStateException("There is no mirrored repository clone links for mirror id " + mirrorIdLocal);
            }
            mirrorCloneLinks = mirroredRepositoryCloneLinks;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                "Could not determine mirror clone links of " + getRepoOwner() + "/" + getRepository()
                    + " on " + getServerUrl() + " for " + getOwner() + " falling back to primary server",
                e);
        }
    }

    private void initPrimaryCloneLinks(BitbucketApi bitbucket) {
        try {
            BitbucketRepository r = bitbucket.getRepository();
            List<BitbucketHref> cloneLinks = r.getCloneLinks();
            if (cloneLinks.isEmpty()) {
                throw new IllegalStateException("There is no clone links");
            }
            setPrimaryCloneLinks(cloneLinks);
        } catch (IOException e) {
            throw new IllegalStateException(
                "Could not determine clone links of " + getRepoOwner() + "/" + getRepository()
                    + " on " + getServerUrl() + " for " + getOwner() + " falling back to generated links",
                e);
        }
    }

    @Deprecated(since = "936.0.0", forRemoval = true)
    public boolean isCloud() {
        return BitbucketApiUtils.isCloud(serverUrl);
    }

    @Symbol("bitbucket")
    @Extension
    public static class DescriptorImpl extends SCMSourceDescriptor {

        public static final String ANONYMOUS = "ANONYMOUS";
        public static final String SAME = "SAME";

        @Override
        public String getDisplayName() {
            return "Bitbucket";
        }

        public FormValidation doCheckCredentialsId(@CheckForNull @AncestorInPath SCMSourceOwner context,
                                                   @QueryParameter String value,
                                                   @QueryParameter(fixEmpty = true, value = "serverUrl") String serverURL) {
            return BitbucketCredentialsUtils.checkCredentialsId(context, value, serverURL);
        }

        public static FormValidation doCheckServerUrl(@AncestorInPath SCMSourceOwner context, @QueryParameter String value) {
            if (context == null && !Jenkins.get().hasPermission(Jenkins.MANAGE)
                || context != null && !context.hasPermission(Item.EXTENDED_READ)) {
                return FormValidation.error(
                    "Unauthorized to validate Server URL"); // not supposed to be seeing this form
            }
            if (!BitbucketEndpointProvider.lookupEndpoint(value).isPresent()) {
                return FormValidation.error("Unregistered Server: " + value);
            }
            return FormValidation.ok();
        }

        public boolean isServerUrlSelectable() {
            return !BitbucketEndpointProvider.all().isEmpty();
        }

        public ListBoxModel doFillServerUrlItems(@AncestorInPath SCMSourceOwner context) {
            AccessControlled contextToCheck = context == null ? Jenkins.get() : context;
            if (!contextToCheck.hasPermission(Item.CONFIGURE)) {
                return new ListBoxModel();
            }
            return BitbucketEndpointProvider.listEndpoints();
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath SCMSourceOwner context, @QueryParameter String serverUrl) {
            return BitbucketCredentialsUtils.listCredentials(context, serverUrl, null);
        }

        @RequirePOST
        public ListBoxModel doFillRepositoryItems(@AncestorInPath SCMSourceOwner context,
                                                  @QueryParameter String serverUrl,
                                                  @QueryParameter String credentialsId,
                                                  @QueryParameter String repoOwner) throws IOException {
            BitbucketSupplier<ListBoxModel> listBoxModelSupplier = bitbucket -> {
                ListBoxModel result = new ListBoxModel();
                BitbucketTeam team = bitbucket.getTeam();
                List<? extends BitbucketRepository> repositories =
                    bitbucket.getRepositories(team != null ? null : UserRoleInRepository.CONTRIBUTOR);
                if (repositories.isEmpty()) {
                    throw FormFillFailure.error(Messages.BitbucketSCMSource_NoMatchingOwner(repoOwner)).withSelectionCleared();
                }
                for (BitbucketRepository repo : repositories) {
                    result.add(repo.getRepositoryName());
                }
                return result;
            };
            return getFromBitbucket(context, serverUrl, credentialsId, repoOwner, null, listBoxModelSupplier);
        }

        public ListBoxModel doFillMirrorIdItems(@AncestorInPath SCMSourceOwner context,
                                                @QueryParameter String serverUrl,
                                                @QueryParameter String credentialsId,
                                                @QueryParameter String repoOwner,
                                                @QueryParameter String repository)
            throws FormFillFailure {

            return getFromBitbucket(context, serverUrl, credentialsId, repoOwner, repository, MirrorListSupplier.INSTANCE);
        }

        @NonNull
        @Override
        protected SCMHeadCategory[] createCategories() {
            return new SCMHeadCategory[]{
                    new UncategorizedSCMHeadCategory(Messages._BitbucketSCMSource_UncategorizedSCMHeadCategory_DisplayName()),
                    new ChangeRequestSCMHeadCategory(Messages._BitbucketSCMSource_ChangeRequestSCMHeadCategory_DisplayName()),
                    new TagSCMHeadCategory(Messages._BitbucketSCMSource_TagSCMHeadCategory_DisplayName())
                    // TODO add support for feature branch identification
            };
        }

        @SuppressWarnings("unchecked")
        public List<NamedArrayList<? extends SCMSourceTraitDescriptor>> getTraitsDescriptorLists() {
            List<SCMSourceTraitDescriptor> all = new ArrayList<>();
            // all that are applicable to our context
            all.addAll(SCMSourceTrait._for(this, BitbucketSCMSourceContext.class, null));
            // all that are applicable to our builders
            all.addAll(SCMSourceTrait._for(this, null, BitbucketGitSCMBuilder.class));
            Set<SCMSourceTraitDescriptor> dedup = new HashSet<>();
            for (Iterator<SCMSourceTraitDescriptor> iterator = all.iterator(); iterator.hasNext(); ) {
                SCMSourceTraitDescriptor d = iterator.next();
                if (dedup.contains(d)
                        || d instanceof GitBrowserSCMSourceTrait.DescriptorImpl) {
                    // remove any we have seen already and ban the browser configuration as it will always be bitbucket
                    iterator.remove();
                } else {
                    dedup.add(d);
                }
            }
            List<NamedArrayList<? extends SCMSourceTraitDescriptor>> result = new ArrayList<>();
            NamedArrayList.select(all, "Within repository", NamedArrayList
                            .anyOf(NamedArrayList.withAnnotation(Discovery.class),
                                   NamedArrayList.withAnnotation(Selection.class)),
                    true, result);
            int insertionPoint = result.size();
            NamedArrayList.select(all, "Git", it -> GitSCM.class.isAssignableFrom(it.getScmClass()), true, result);
            NamedArrayList.select(all, "General", null, true, result, insertionPoint);
            return result;
        }

        @Override
        public List<SCMSourceTrait> getTraitsDefaults() {
            return Arrays.asList(
                    new BranchDiscoveryTrait(true, false),
                    new OriginPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE)),
                    new ForkPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE),
                            new ForkPullRequestDiscoveryTrait.TrustTeamForks())
            );
        }
    }

    private static class BranchHeadCommit implements BitbucketCommit {

        private final BitbucketBranch branch;

        public BranchHeadCommit(@NonNull final BitbucketBranch branch) {
            this.branch = branch;
        }

        @Override
        public String getAuthor() {
            return branch.getAuthor();
        }

        @Override
        public String getMessage() {
            return branch.getMessage();
        }

        @Override
        public String getDate() {
            return DateUtils.formatToISO(new Date(branch.getDateMillis()));
        }

        @Override
        public String getHash() {
            return branch.getRawNode();
        }

        @Override
        public long getDateMillis() {
            return branch.getDateMillis();
        }
    }

}
