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

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApi;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketApiFactory;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketAuthenticator;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBranch;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCommit;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketHref;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketPullRequest;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryProtocol;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepositoryType;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRequestException;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketTeam;
import com.cloudbees.jenkins.plugins.bitbucket.client.BitbucketCloudApiClient;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.UserRoleInRepository;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.AbstractBitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.hooks.HasPullRequests;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.damnhandy.uri.template.UriTemplate;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.RestrictedSince;
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
import java.io.ObjectStreamException;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import jenkins.plugins.git.AbstractGitSCMSource.SCMRevisionImpl;
import jenkins.plugins.git.traits.GitBrowserSCMSourceTrait;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceCriteria.Probe;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.api.SCMSourceEvent;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.metadata.ContributorMetadataAction;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.api.metadata.PrimaryInstanceMetadataAction;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceRequest.IntermediateLambda;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.TagSCMHeadCategory;
import jenkins.scm.impl.UncategorizedSCMHeadCategory;
import jenkins.scm.impl.form.NamedArrayList;
import jenkins.scm.impl.trait.Discovery;
import jenkins.scm.impl.trait.Selection;
import jenkins.scm.impl.trait.WildcardSCMHeadFilterTrait;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;
import org.eclipse.jgit.lib.Constants;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

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
     * Credentials used to clone the repository/repositories.
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.2.0")
    private transient String checkoutCredentialsId;

    /**
     * Ant match expression that indicates what branches to include in the retrieve process.
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.2.0")
    private transient String includes;

    /**
     * Ant match expression that indicates what branches to exclude in the retrieve process.
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.2.0")
    private transient String excludes;

    /**
     * If true, a webhook will be auto-registered in the repository managed by this source.
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.2.0")
    private transient boolean autoRegisterHook;

    /**
     * Bitbucket Server URL.
     * A specific HTTP client is used if this field is not null.
     * This value (or serverUrl if this is null) is used in particular
     * to find the current endpoint configuration for this server.
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.2.0")
    private transient String bitbucketServerUrl;

    /**
     * The cache of the repository type.
     */
    @CheckForNull
    private transient BitbucketRepositoryType repositoryType;

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
     * The cache of the clone links.
     */
    @CheckForNull
    private transient List<BitbucketHref> cloneLinks = null;

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

    /**
     * Migrate legacy serialization formats.
     *
     * @return {@code this}
     * @throws ObjectStreamException if things go wrong.
     */
    @SuppressWarnings({"ConstantConditions", "deprecation"})
    @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
                        justification = "Only non-null after we set them here!")
    private Object readResolve() throws ObjectStreamException {
        if (serverUrl == null) {
            serverUrl = BitbucketEndpointConfiguration.get().readResolveServerUrl(bitbucketServerUrl);
        }
        if (serverUrl == null) {
            LOGGER.log(Level.WARNING, "BitbucketSCMSource::readResolve : serverUrl is still empty");
        }
        if (traits == null) {
            traits = new ArrayList<>();
            if (!"*".equals(includes) || !"".equals(excludes)) {
                traits.add(new WildcardSCMHeadFilterTrait(includes, excludes));
            }
            if (checkoutCredentialsId != null
                    && !DescriptorImpl.SAME.equals(checkoutCredentialsId)
                    && !checkoutCredentialsId.equals(credentialsId)) {
                traits.add(new SSHCheckoutTrait(checkoutCredentialsId));
            }
            traits.add(new WebhookRegistrationTrait(
                    autoRegisterHook ? WebhookRegistration.ITEM : WebhookRegistration.DISABLE)
            );
            traits.add(new BranchDiscoveryTrait(true, true));
            traits.add(new OriginPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD)));
            traits.add(new ForkPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD),
                    new ForkPullRequestDiscoveryTrait.TrustEveryone()));
            traits.add(new PublicRepoPullRequestFilterTrait());
        }
        return this;
    }

    @CheckForNull
    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(@CheckForNull String credentialsId) {
        this.credentialsId = Util.fixEmpty(credentialsId);
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
    public void setServerUrl(@CheckForNull String serverUrl) {
        this.serverUrl = BitbucketEndpointConfiguration.normalizeServerUrl(serverUrl);
    }

    @NonNull
    public String getEndpointJenkinsRootUrl() {
        return AbstractBitbucketEndpoint.getEndpointJenkinsRootUrl(serverUrl);
    }

    @NonNull
    public List<SCMSourceTrait> getTraits() {
        return Collections.unmodifiableList(traits);
    }

    @DataBoundSetter
    public void setTraits(@CheckForNull List<SCMSourceTrait> traits) {
        this.traits = new ArrayList<>(Util.fixNull(traits));
    }

    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.2.0")
    @DataBoundSetter
    public void setBitbucketServerUrl(String url) {
        url = BitbucketEndpointConfiguration.normalizeServerUrl(url);
        AbstractBitbucketEndpoint endpoint = BitbucketEndpointConfiguration.get().findEndpoint(url);
        if (endpoint != null) {
            // we have a match
            setServerUrl(endpoint.getServerUrl());
            return;
        }
        LOGGER.log(Level.WARNING, "Call to legacy setBitbucketServerUrl({0}) method is configuring a url missing "
                + "from the global configuration.", url);
        setServerUrl(url);
    }

    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.2.0")
    @CheckForNull
    public String getBitbucketServerUrl() {
        String serverUrl = getServerUrl();
        if (BitbucketEndpointConfiguration.get().findEndpoint(serverUrl) instanceof BitbucketCloudEndpoint) {
            return null;
        }
        return serverUrl;
    }

    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.2.0")
    @CheckForNull
    public String getCheckoutCredentialsId() {
        for (SCMSourceTrait t : traits) {
            if (t instanceof SSHCheckoutTrait) {
                return StringUtils.defaultString(((SSHCheckoutTrait) t).getCredentialsId(), DescriptorImpl.ANONYMOUS);
            }
        }
        return DescriptorImpl.SAME;
    }

    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.2.0")
    @DataBoundSetter
    public void setCheckoutCredentialsId(String checkoutCredentialsId) {
        traits.removeIf(trait -> trait instanceof SSHCheckoutTrait);
        if (checkoutCredentialsId != null && !DescriptorImpl.SAME.equals(checkoutCredentialsId)) {
            traits.add(new SSHCheckoutTrait(checkoutCredentialsId));
        }
    }

    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.2.0")
    @NonNull
    public String getIncludes() {
        for (SCMSourceTrait trait : traits) {
            if (trait instanceof WildcardSCMHeadFilterTrait) {
                return ((WildcardSCMHeadFilterTrait) trait).getIncludes();
            }
        }
        return "*";
    }

    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.2.0")
    @DataBoundSetter
    public void setIncludes(@NonNull String includes) {
        for (int i = 0; i < traits.size(); i++) {
            SCMSourceTrait trait = traits.get(i);
            if (trait instanceof WildcardSCMHeadFilterTrait) {
                WildcardSCMHeadFilterTrait existing = (WildcardSCMHeadFilterTrait) trait;
                if ("*".equals(includes) && "".equals(existing.getExcludes())) {
                    traits.remove(i);
                } else {
                    traits.set(i, new WildcardSCMHeadFilterTrait(includes, existing.getExcludes()));
                }
                return;
            }
        }
        if (!"*".equals(includes)) {
            traits.add(new WildcardSCMHeadFilterTrait(includes, ""));
        }
    }

    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.2.0")
    @NonNull
    public String getExcludes() {
        for (SCMSourceTrait trait : traits) {
            if (trait instanceof WildcardSCMHeadFilterTrait) {
                return ((WildcardSCMHeadFilterTrait) trait).getExcludes();
            }
        }
        return "";
    }

    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.2.0")
    @DataBoundSetter
    public void setExcludes(@NonNull String excludes) {
        for (int i = 0; i < traits.size(); i++) {
            SCMSourceTrait trait = traits.get(i);
            if (trait instanceof WildcardSCMHeadFilterTrait) {
                WildcardSCMHeadFilterTrait existing = (WildcardSCMHeadFilterTrait) trait;
                if ("*".equals(existing.getIncludes()) && "".equals(excludes)) {
                    traits.remove(i);
                } else {
                    traits.set(i, new WildcardSCMHeadFilterTrait(existing.getIncludes(), excludes));
                }
                return;
            }
        }
        if (!"".equals(excludes)) {
            traits.add(new WildcardSCMHeadFilterTrait("*", excludes));
        }
    }


    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.2.0")
    @DataBoundSetter
    public void setAutoRegisterHook(boolean autoRegisterHook) {
        traits.removeIf(trait -> trait instanceof WebhookRegistrationTrait);
        traits.add(new WebhookRegistrationTrait(
                autoRegisterHook ? WebhookRegistration.ITEM : WebhookRegistration.DISABLE
        ));
    }

    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.2.0")
    public boolean isAutoRegisterHook() {
        for (SCMSourceTrait t : traits) {
            if (t instanceof WebhookRegistrationTrait) {
                return ((WebhookRegistrationTrait) t).getMode() != WebhookRegistration.DISABLE;
            }
        }
        return true;
    }

    public BitbucketRepositoryType getRepositoryType() throws IOException, InterruptedException {
        if (repositoryType == null) {
            BitbucketRepository r = buildBitbucketClient().getRepository();
            repositoryType = BitbucketRepositoryType.fromString(r.getScm());
            Map<String, List<BitbucketHref>> links = r.getLinks();
            if (links != null && links.containsKey("clone")) {
                cloneLinks = links.get("clone");
            }
        }
        return repositoryType;
    }

    public BitbucketApi buildBitbucketClient() {
        return buildBitbucketClient(repoOwner, repository);
    }

    public BitbucketApi buildBitbucketClient(PullRequestSCMHead head) {
        return buildBitbucketClient(head.getRepoOwner(), head.getRepository());
    }

    public BitbucketApi buildBitbucketClient(String repoOwner, String repository) {
        return BitbucketApiFactory.newInstance(getServerUrl(), authenticator(), repoOwner, repository);
    }

    @Override
    public void afterSave() {
        try {
            getRepositoryType();
        } catch (InterruptedException | IOException e) {
            LOGGER.log(Level.FINE,
                    "Could not determine repository type of " + getRepoOwner() + "/" + getRepository() + " on "
                            + getServerUrl() + " for " + getOwner(), e);
        }
    }

    @Override
    protected void retrieve(@CheckForNull SCMSourceCriteria criteria, @NonNull SCMHeadObserver observer,
                            @CheckForNull SCMHeadEvent<?> event, @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        try (BitbucketSCMSourceRequest request = new BitbucketSCMSourceContext(criteria, observer)
                .withTraits(traits)
                .newRequest(this, listener)) {
            StandardCredentials scanCredentials = credentials();
            if (scanCredentials == null) {
                listener.getLogger().format("Connecting to %s with no credentials, anonymous access%n", getServerUrl());
            } else {
                listener.getLogger().format("Connecting to %s using %s%n", getServerUrl(),
                        CredentialsNameProvider.name(scanCredentials));
            }
            // this has the side effect of ensuring that repository type is always populated.
            final BitbucketRepositoryType repositoryType = getRepositoryType();
            listener.getLogger().format("Repository type: %s%n", WordUtils.capitalizeFully(repositoryType != null ? repositoryType.name() : "Unknown"));

            // populate the request with its data sources
            if (request.isFetchPRs()) {
                request.setPullRequests(new LazyIterable<BitbucketPullRequest>() {
                    @Override
                    protected Iterable<BitbucketPullRequest> create() {
                        try {
                            if (event instanceof HasPullRequests) {
                                HasPullRequests hasPrEvent = (HasPullRequests) event;
                                return getBitbucketPullRequestsFromEvent(hasPrEvent, listener);
                            }

                            return (Iterable<BitbucketPullRequest>) buildBitbucketClient().getPullRequests();
                        } catch (IOException | InterruptedException e) {
                            throw new BitbucketSCMSource.WrappedException(e);
                        }
                    }
                });
            }
            if (request.isFetchBranches()) {
                request.setBranches(new LazyIterable<BitbucketBranch>() {
                    @Override
                    protected Iterable<BitbucketBranch> create() {
                        try {
                            return (Iterable<BitbucketBranch>) buildBitbucketClient().getBranches();
                        } catch (IOException | InterruptedException e) {
                            throw new BitbucketSCMSource.WrappedException(e);
                        }
                    }
                });
            }
            if (request.isFetchTags()) {
                request.setTags(new LazyIterable<BitbucketBranch>() {
                    @Override
                    protected Iterable<BitbucketBranch> create() {
                        try {
                            return (Iterable<BitbucketBranch>) buildBitbucketClient().getTags();
                        } catch (IOException | InterruptedException e) {
                            throw new BitbucketSCMSource.WrappedException(e);
                        }
                    }
                });
            }

            // now server the request
            if (request.isFetchBranches() && !request.isComplete()) {
                // Search branches
                retrieveBranches(request);
            }
            if (request.isFetchPRs() && !request.isComplete()) {
                // Search pull requests
                retrievePullRequests(request);
            }
            if (request.isFetchTags() && !request.isComplete()) {
                // Search tags
                retrieveTags(request);
            }
        } catch (WrappedException e) {
            e.unwrap();
        }
    }

    private Iterable<BitbucketPullRequest> getBitbucketPullRequestsFromEvent(@NonNull HasPullRequests incomingPrEvent, @NonNull TaskListener listener) {
        BitbucketApi bitBucket = buildBitbucketClient();
        Collection<BitbucketPullRequest> initializedPRs = new HashSet<>();
        try {
            Iterable<BitbucketPullRequest> pullRequests =
                incomingPrEvent.getPullRequests(BitbucketSCMSource.this);
            for (BitbucketPullRequest pr : pullRequests) {
                // ensure that the PR is properly initialized via /changes API
                // see BitbucketServerAPIClient.setupPullRequest()
                initializedPRs.add(bitBucket.getPullRequestById(Integer.parseInt(pr.getId())));
                listener.getLogger().format("Initialized PR: %s%n", pr.getLink());
            }
        } catch (IOException | InterruptedException e) {
            throw new BitbucketSCMSource.WrappedException(e);
        }
        return initializedPRs;
    }

    private void retrievePullRequests(final BitbucketSCMSourceRequest request) throws IOException, InterruptedException {
        final String fullName = repoOwner + "/" + repository;

        class Skip extends IOException {
        }

        final BitbucketApi originBitbucket = buildBitbucketClient();
        if (request.isSkipPublicPRs() && !originBitbucket.isPrivate()) {
            request.listener().getLogger().printf("Skipping pull requests for %s (public repository)%n", fullName);
            return;
        }

        request.listener().getLogger().printf("Looking up %s for pull requests%n", fullName);
        final Set<String> livePRs = new HashSet<>();
        int count = 0;
        Map<Boolean, Set<ChangeRequestCheckoutStrategy>> strategies = request.getPRStrategies();
        for (final BitbucketPullRequest pull : request.getPullRequests()) {
            String originalBranchName = pull.getSource().getBranch().getName();
            request.listener().getLogger().printf(
                    "Checking PR-%s from %s and branch %s%n",
                    pull.getId(),
                    pull.getSource().getRepository().getFullName(),
                    originalBranchName
            );
            boolean fork = !fullName.equalsIgnoreCase(pull.getSource().getRepository().getFullName());
            String pullRepoOwner = pull.getSource().getRepository().getOwnerName();
            String pullRepository = pull.getSource().getRepository().getRepositoryName();
            final BitbucketApi pullBitbucket = fork && originBitbucket instanceof BitbucketCloudApiClient
                    ? BitbucketApiFactory.newInstance(
                    getServerUrl(),
                    authenticator(),
                    pullRepoOwner,
                    pullRepository
            )
                    : originBitbucket;
            count++;
            livePRs.add(pull.getId());
            getPullRequestTitleCache()
                    .put(pull.getId(), StringUtils.defaultString(pull.getTitle()));
            getPullRequestContributorCache().put(pull.getId(),
                    new ContributorMetadataAction(pull.getAuthorIdentifier(), pull.getAuthorLogin(), pull.getAuthorEmail()));
            try {
                // We store resolved hashes here so to avoid resolving the commits multiple times
                for (final ChangeRequestCheckoutStrategy strategy : strategies.get(fork)) {
                    String branchName = "PR-" + pull.getId();
                    if (strategies.get(fork).size() > 1) {
                        branchName = "PR-" + pull.getId() + "-" + strategy.name().toLowerCase(Locale.ENGLISH);
                    }
                    PullRequestSCMHead head;
                    if (originBitbucket instanceof BitbucketCloudApiClient) {
                        head = new PullRequestSCMHead( //
                                branchName, //
                                pullRepoOwner, //
                                pullRepository, //
                                originalBranchName, //
                                pull, //
                                originOf(pullRepoOwner, pullRepository), //
                                strategy);
                    } else {
                        head = new PullRequestSCMHead( //
                                branchName, //
                                repoOwner, //
                                repository, //
                                originalBranchName, //
                                pull, //
                                originOf(pullRepoOwner, pullRepository), //
                                strategy);
                    }
                    if (request.process(head, //
                        () -> {
                            // use branch instead of commit to postpone closure initialisation
                            return new BranchHeadCommit(pull.getSource().getBranch());
                        },  //
                            new BitbucketProbeFactory<>(pullBitbucket, request), //
                            new BitbucketRevisionFactory<BitbucketCommit>(pullBitbucket) {
                                @NonNull
                                @Override
                                public SCMRevision create(@NonNull SCMHead head, @Nullable BitbucketCommit sourceCommit)
                                        throws IOException, InterruptedException {
                                    try {
                                        // use branch instead of commit to postpone closure initialisation
                                        BranchHeadCommit targetCommit = new BranchHeadCommit(pull.getDestination().getBranch());
                                        return super.create(head, sourceCommit, targetCommit);
                                    } catch (BitbucketRequestException e) {
                                        if (originBitbucket instanceof BitbucketCloudApiClient) {
                                            if (e.getHttpCode() == 403) {
                                                request.listener().getLogger().printf( //
                                                        "Skipping %s because of %s%n", //
                                                        pull.getId(), //
                                                        HyperlinkNote.encodeTo("https://bitbucket.org/site/master" //
                                                                + "/issues/5814/reify-pull-requests-by-making-them-a-ref", //
                                                                "a permission issue accessing pull requests from forks"));
                                                throw new Skip();
                                            }
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
                            }, //
                            new CriteriaWitness(request))) {
                        request.listener().getLogger() //
                               .format("%n  %d pull requests were processed (query completed)%n", count);
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
            }
        }
        request.listener().getLogger().format("%n  %d pull requests were processed%n", count);
        getPullRequestTitleCache().keySet().retainAll(livePRs);
        getPullRequestContributorCache().keySet().retainAll(livePRs);
    }

    private void retrieveBranches(final BitbucketSCMSourceRequest request)
            throws IOException, InterruptedException {
        String fullName = repoOwner + "/" + repository;
        request.listener().getLogger().println("Looking up " + fullName + " for branches");

        final BitbucketApi bitbucket = buildBitbucketClient();
        Map<String, List<BitbucketHref>> links = bitbucket.getRepository().getLinks();
        if (links != null && links.containsKey("clone")) {
            cloneLinks = links.get("clone");
        }
        int count = 0;
        for (final BitbucketBranch branch : request.getBranches()) {
            request.listener().getLogger().println("Checking branch " + branch.getName() + " from " + fullName);
            count++;
            if (request.process(new BranchSCMHead(branch.getName()), //
                (IntermediateLambda<BitbucketCommit>) () -> new BranchHeadCommit(branch), //
                    new BitbucketProbeFactory<>(bitbucket, request), //
                    new BitbucketRevisionFactory<>(bitbucket), //
                    new CriteriaWitness(request))) {
                request.listener().getLogger().format("%n  %d branches were processed (query completed)%n", count);
                return;
            }
        }
        request.listener().getLogger().format("%n  %d branches were processed%n", count);
    }


    private void retrieveTags(final BitbucketSCMSourceRequest request) throws IOException, InterruptedException {
        String fullName = repoOwner + "/" + repository;
        request.listener().getLogger().println("Looking up " + fullName + " for tags");

        final BitbucketApi bitbucket = buildBitbucketClient();
        Map<String, List<BitbucketHref>> links = bitbucket.getRepository().getLinks();
        if (links != null && links.containsKey("clone")) {
            cloneLinks = links.get("clone");
        }
        int count = 0;
        for (final BitbucketBranch tag : request.getTags()) {
            request.listener().getLogger().println("Checking tag " + tag.getName() + " from " + fullName);
            count++;
            if (request.process(new BitbucketTagSCMHead(tag.getName(), tag.getDateMillis()), //
                tag::getRawNode, //
                    new BitbucketProbeFactory<>(bitbucket, request), //
                    new BitbucketRevisionFactory<>(bitbucket), //
                    new CriteriaWitness(request))) {
                request.listener().getLogger().format("%n  %d tags were processed (query completed)%n", count);
                return;
            }
        }
        request.listener().getLogger().format("%n  %d tags were processed%n", count);
    }

    @Override
    protected SCMRevision retrieve(SCMHead head, TaskListener listener) throws IOException, InterruptedException {
        final BitbucketApi bitbucket = buildBitbucketClient();
        try {
            List<? extends BitbucketBranch> branches = bitbucket.getBranches();
            if (head instanceof PullRequestSCMHead) {
                PullRequestSCMHead h = (PullRequestSCMHead) head;
                BitbucketCommit targetRevision = findCommit(h.getTarget().getName(), branches, listener);
                if (targetRevision == null) {
                    LOGGER.log(Level.WARNING, "No branch found in {0}/{1} with name [{2}]",
                        new Object[]{repoOwner, repository, h.getTarget().getName()});
                    return null;
                }
                BitbucketCommit sourceRevision;
                if (bitbucket instanceof BitbucketCloudApiClient) {
                    branches = head.getOrigin() == SCMHeadOrigin.DEFAULT
                        ? branches
                        : buildBitbucketClient(h).getBranches();
                    sourceRevision = findCommit(h.getBranchName(), branches, listener);
                } else {
                    try {
                        BitbucketPullRequest pr = bitbucket.getPullRequestById(Integer.parseInt(h.getId()));
                        sourceRevision = findPRCommit(pr, listener);
                    } catch (NumberFormatException nfe) {
                        LOGGER.log(Level.WARNING, "Cannot parse the PR id {0}", h.getId());
                        sourceRevision = null;
                    }
                }
                if (sourceRevision == null) {
                    LOGGER.log(Level.WARNING, "No revision found in {0}/{1} for PR-{2} [{3}]",
                        new Object[]{
                            h.getRepoOwner(),
                            h.getRepository(),
                            h.getId(),
                            h.getBranchName()
                        });
                    return null;
                }
                return new PullRequestSCMRevision<>(
                    h,
                    new BitbucketGitSCMRevision(h.getTarget(), targetRevision),
                    new BitbucketGitSCMRevision(h, sourceRevision)
                );
            } else if (head instanceof BitbucketTagSCMHead) {
                BitbucketTagSCMHead tagHead = (BitbucketTagSCMHead) head;
                List<? extends BitbucketBranch> tags = bitbucket.getTags();
                BitbucketCommit revision = findCommit(head.getName(), tags, listener);
                if (revision == null) {
                    LOGGER.log(Level.WARNING, "No tag found in {0}/{1} with name [{2}]", new Object[]{repoOwner, repository, head.getName()});
                    return null;
                }
                return new BitbucketTagSCMRevision(tagHead, revision);
            } else {
                BitbucketCommit revision = findCommit(head.getName(), branches, listener);
                if (revision == null) {
                    LOGGER.log(Level.WARNING, "No branch found in {0}/{1} with name [{2}]",
                        new Object[]{repoOwner, repository, head.getName()});
                    return null;
                }
                return new BitbucketGitSCMRevision(head, revision);
            }
        } catch (IOException e) {
            // here we only want to display the job name to have it in the log
            if (e instanceof BitbucketRequestException) {
                BitbucketRequestException bre = (BitbucketRequestException) e;
                SCMSourceOwner scmSourceOwner = getOwner();
                if (bre.getHttpCode() == 401 && scmSourceOwner != null) {
                    LOGGER.log(Level.WARNING, "BitbucketRequestException: Authz error. Status: 401 for Item '{0}' using credentialId '{1}'",
                        new Object[]{scmSourceOwner.getFullDisplayName(), getCredentialsId()});
                }
            }
            throw e;
        }
    }

    private BitbucketCommit findCommit(@NonNull String branchName, List<? extends BitbucketBranch> branches, TaskListener listener) {
        for (final BitbucketBranch b : branches) {
            if (branchName.equals(b.getName())) {
                String revision = b.getRawNode();
                if (revision == null) {
                    if (BitbucketCloudEndpoint.SERVER_URL.equals(getServerUrl())) {
                        listener.getLogger().format("Cannot resolve the hash of the revision in branch %s%n",
                                branchName);
                    } else {
                        listener.getLogger().format("Cannot resolve the hash of the revision in branch %s. "
                                        + "Perhaps you are using Bitbucket Server previous to 4.x%n",
                                branchName);
                    }
                    return null;
                }
                return new BranchHeadCommit(b);
            }
        }
        listener.getLogger().format("Cannot find the branch %s%n", branchName);
        return null;
    }

    private BitbucketCommit findPRCommit(BitbucketPullRequest pr, TaskListener listener) {
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

    @Override
    public SCM build(SCMHead head, SCMRevision revision) {
        BitbucketRepositoryType type;
        if (head instanceof PullRequestSCMHead) {
            type = ((PullRequestSCMHead) head).getRepositoryType();
        } else if (head instanceof BranchSCMHead) {
            type = ((BranchSCMHead) head).getRepositoryType();
        } else if (head instanceof BitbucketTagSCMHead) {
            type = ((BitbucketTagSCMHead) head).getRepositoryType();
        } else {
            throw new IllegalArgumentException("Either PullRequestSCMHead, BitbucketTagSCMHead or BranchSCMHead required as parameter");
        }
        if (type == null) {
            if (revision instanceof SCMRevisionImpl) {
                type = BitbucketRepositoryType.GIT;
            } else {
                try {
                    type = getRepositoryType();
                } catch (IOException | InterruptedException e) {
                    type = BitbucketRepositoryType.GIT;
                    LOGGER.log(Level.SEVERE,
                            "Could not determine repository type of " + getRepoOwner() + "/" + getRepository()
                                    + " on " + getServerUrl() + " for " + getOwner() + " assuming " + type, e);
                }
            }
        }
        assert type != null;

        if (cloneLinks == null) {
            BitbucketApi bitbucket = buildBitbucketClient();
            try {
                BitbucketRepository r = bitbucket.getRepository();
                Map<String, List<BitbucketHref>> links = r.getLinks();
                if (links != null && links.containsKey("clone")) {
                    cloneLinks = links.get("clone");
                }
            } catch (IOException | InterruptedException e) {
                LOGGER.log(Level.SEVERE,
                        "Could not determine clone links of " + getRepoOwner() + "/" + getRepository()
                                + " on " + getServerUrl() + " for " + getOwner() + " falling back to generated links",
                        e);
                cloneLinks = new ArrayList<>();
                cloneLinks.add(new BitbucketHref("ssh",
                        bitbucket.getRepositoryUri(BitbucketRepositoryProtocol.SSH, null,
                            getRepoOwner(), getRepository())
                ));
                cloneLinks.add(new BitbucketHref("https",
                        bitbucket.getRepositoryUri(BitbucketRepositoryProtocol.HTTP, null,
                            getRepoOwner(), getRepository())
                ));
            }
        }
        switch (type) {
            case GIT:
            default:
                return new BitbucketGitSCMBuilder(this, head, revision, getCredentialsId())
                        .withCloneLinks(cloneLinks)
                        .withTraits(traits)
                        .build();

        }
    }

    @NonNull
    @Override
    public SCMRevision getTrustedRevision(@NonNull SCMRevision revision, @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        if (revision instanceof PullRequestSCMRevision) {
            PullRequestSCMHead head = (PullRequestSCMHead) revision.getHead();

            try (BitbucketSCMSourceRequest request = new BitbucketSCMSourceContext(null, SCMHeadObserver.none())
                    .withTraits(traits)
                    .newRequest(this, listener)) {
                if (request.isTrusted(head)) {
                    return revision;
                }
            } catch (WrappedException wrapped) {
                wrapped.unwrap();
            }
            PullRequestSCMRevision<?> rev = (PullRequestSCMRevision) revision;
            listener.getLogger().format("Loading trusted files from base branch %s at %s rather than %s%n",
                    head.getTarget().getName(), rev.getTarget(), rev.getPull());
            return rev.getTarget();
        }
        return revision;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @CheckForNull
    /* package */ StandardCredentials credentials() {
        return BitbucketCredentials.lookupCredentials(
                getServerUrl(),
                getOwner(),
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
    protected List<Action> retrieveActions(@CheckForNull SCMSourceEvent event,
                                           @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        // TODO when we have support for trusted events, use the details from event if event was from trusted source
        List<Action> result = new ArrayList<>();
        final BitbucketApi bitbucket = buildBitbucketClient();
        BitbucketRepository r = bitbucket.getRepository();
        Map<String, List<BitbucketHref>> links = r.getLinks();
        if (links != null && links.containsKey("clone")) {
            cloneLinks = links.get("clone");
        }
        result.add(new BitbucketRepoMetadataAction(r));
        String defaultBranch = bitbucket.getDefaultBranch();
        if (StringUtils.isNotBlank(defaultBranch)) {
            result.add(new BitbucketDefaultBranch(repoOwner, repository, defaultBranch));
        }
        UriTemplate template;
        if (BitbucketCloudEndpoint.SERVER_URL.equals(getServerUrl())) {
            template = UriTemplate.fromTemplate(getServerUrl() + CLOUD_REPO_TEMPLATE);
        } else {
            template = UriTemplate.fromTemplate(getServerUrl() + SERVER_REPO_TEMPLATE);
        }
        template.set("owner", repoOwner).set("repo", repository);
        String url = template.expand();
        result.add(new BitbucketLink("icon-bitbucket-repo", url));
        result.add(new ObjectMetadataAction(r.getRepositoryName(), null, url));
        return result;
    }

    @NonNull
    @Override
    protected List<Action> retrieveActions(@NonNull SCMHead head,
                                           @CheckForNull SCMHeadEvent event,
                                           @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        // TODO when we have support for trusted events, use the details from event if event was from trusted source
        List<Action> result = new ArrayList<>();
        UriTemplate template;
        String title = null;
        if (BitbucketCloudEndpoint.SERVER_URL.equals(getServerUrl())) {
            template = UriTemplate.fromTemplate(getServerUrl() + CLOUD_REPO_TEMPLATE + "/{branchOrPR}/{prIdOrHead}")
                    .set("owner", repoOwner)
                    .set("repo", repository);
            if (head instanceof PullRequestSCMHead) {
                PullRequestSCMHead pr = (PullRequestSCMHead) head;
                template.set("branchOrPR", "pull-requests").set("prIdOrHead", pr.getId());
            } else {
                template.set("branchOrPR", "branch").set("prIdOrHead", head.getName());
            }
        } else {
            if (head instanceof PullRequestSCMHead) {
                PullRequestSCMHead pr = (PullRequestSCMHead) head;
                template = UriTemplate
                        .fromTemplate(getServerUrl() + SERVER_REPO_TEMPLATE + "/pull-requests/{id}/overview")
                        .set("owner", repoOwner)
                        .set("repo", repository)
                        .set("id", pr.getId());
            } else {
                template = UriTemplate
                        .fromTemplate(getServerUrl() + SERVER_REPO_TEMPLATE + "/compare/commits{?sourceBranch}")
                        .set("owner", repoOwner)
                        .set("repo", repository)
                        .set("sourceBranch", Constants.R_HEADS + head.getName());
            }
        }
        if (head instanceof PullRequestSCMHead) {
            PullRequestSCMHead pr = (PullRequestSCMHead) head;
            title = getPullRequestTitleCache().get(pr.getId());
            ContributorMetadataAction contributor = getPullRequestContributorCache().get(pr.getId());
            if (contributor != null) {
                result.add(contributor);
            }
        }
        String url = template.expand();
        result.add(new BitbucketLink("icon-bitbucket-branch", url));
        result.add(new ObjectMetadataAction(title, null, url));
        SCMSourceOwner owner = getOwner();
        if (owner instanceof Actionable) {
            for (BitbucketDefaultBranch p : ((Actionable) owner).getActions(BitbucketDefaultBranch.class)) {
                if (StringUtils.equals(getRepoOwner(), p.getRepoOwner())
                        && StringUtils.equals(repository, p.getRepository())
                        && StringUtils.equals(p.getDefaultBranch(), head.getName())) {
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
            if (this.repoOwner.equalsIgnoreCase(repoOwner)) {
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

    @Symbol("bitbucket")
    @Extension
    public static class DescriptorImpl extends SCMSourceDescriptor {

        public static final String ANONYMOUS = "ANONYMOUS";
        public static final String SAME = "SAME";

        @Override
        public String getDisplayName() {
            return "Bitbucket";
        }

        @SuppressWarnings("unused") // used By stapler
        public FormValidation doCheckCredentialsId(@CheckForNull @AncestorInPath SCMSourceOwner context,
                                                   @QueryParameter String value,
                                                   @QueryParameter String serverUrl) {
            return BitbucketCredentials.checkCredentialsId(context, value, serverUrl);
        }

        @SuppressWarnings("unused") // used By stapler
        public static FormValidation doCheckServerUrl(@AncestorInPath SCMSourceOwner context, @QueryParameter String value) {
            AccessControlled contextToCheck = context == null ? Jenkins.get() : context;
            contextToCheck.checkPermission(Item.CONFIGURE);
            if (BitbucketEndpointConfiguration.get().findEndpoint(value) == null) {
                return FormValidation.error("Unregistered Server: " + value);
            }
            return FormValidation.ok();
        }

        @SuppressWarnings("unused") // used By stapler
        public boolean isServerUrlSelectable() {
            return BitbucketEndpointConfiguration.get().isEndpointSelectable();
        }

        @SuppressWarnings("unused") // used By stapler
        public ListBoxModel doFillServerUrlItems(@AncestorInPath SCMSourceOwner context) {
            AccessControlled contextToCheck = context == null ? Jenkins.get() : context;
            if (!contextToCheck.hasPermission(Item.CONFIGURE)) {
                return new ListBoxModel();
            }
            return BitbucketEndpointConfiguration.get().getEndpointItems();
        }

        @SuppressWarnings("unused") // used By stapler
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath SCMSourceOwner context, @QueryParameter String serverUrl) {
            return BitbucketCredentials.fillCredentialsIdItems(context, serverUrl);
        }

        @SuppressWarnings("unused") // used By stapler
        @RequirePOST
        public ListBoxModel doFillRepositoryItems(@AncestorInPath SCMSourceOwner context,
                                                  @QueryParameter String serverUrl,
                                                  @QueryParameter String credentialsId,
                                                  @QueryParameter String repoOwner)
                throws IOException, InterruptedException {
            repoOwner = Util.fixEmptyAndTrim(repoOwner);
            if (repoOwner == null) {
                return new ListBoxModel();
            }
            if (context == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER) ||
                context != null && !context.hasPermission(Item.EXTENDED_READ)) {
                return new ListBoxModel(); // not supposed to be seeing this form
            }
            if (context != null && !context.hasPermission(CredentialsProvider.USE_ITEM)) {
                return new ListBoxModel(); // not permitted to try connecting with these credentials
            }

            String serverUrlFallback = BitbucketCloudEndpoint.SERVER_URL;
            // if at least one bitbucket server is configured use it instead of bitbucket cloud
            if(BitbucketEndpointConfiguration.get().getEndpointItems().size() > 0){
               serverUrlFallback =  BitbucketEndpointConfiguration.get().getEndpointItems().get(0).value;
            }

            serverUrl = StringUtils.defaultIfBlank(serverUrl, serverUrlFallback);
            ListBoxModel result = new ListBoxModel();
            StandardCredentials credentials = BitbucketCredentials.lookupCredentials(
                    serverUrl,
                    context,
                    credentialsId,
                    StandardCredentials.class
            );

            BitbucketAuthenticator authenticator = AuthenticationTokens.convert(BitbucketAuthenticator.authenticationContext(serverUrl), credentials);

            try {
                BitbucketApi bitbucket = BitbucketApiFactory.newInstance(serverUrl, authenticator, repoOwner, null);
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
            } catch (FormFillFailure | OutOfMemoryError e) {
                throw e;
            } catch (IOException e) {
                if (e instanceof BitbucketRequestException) {
                    if (((BitbucketRequestException) e).getHttpCode() == 401) {
                        throw FormFillFailure.error(credentials == null
                                ? Messages.BitbucketSCMSource_UnauthorizedAnonymous(repoOwner)
                                : Messages.BitbucketSCMSource_UnauthorizedOwner(repoOwner)).withSelectionCleared();
                    }
                } else if (e.getCause() instanceof BitbucketRequestException) {
                    if (((BitbucketRequestException) e.getCause()).getHttpCode() == 401) {
                        throw FormFillFailure.error(credentials == null
                                ? Messages.BitbucketSCMSource_UnauthorizedAnonymous(repoOwner)
                                : Messages.BitbucketSCMSource_UnauthorizedOwner(repoOwner)).withSelectionCleared();
                    }
                }
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
                throw FormFillFailure.error(e.getMessage());
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
                throw FormFillFailure.error(e.getMessage());
            }
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

        public List<SCMSourceTrait> getTraitsDefaults() {
            return Arrays.asList(
                    new BranchDiscoveryTrait(true, false),
                    new OriginPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE)),
                    new ForkPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE),
                            new ForkPullRequestDiscoveryTrait.TrustTeamForks())
            );
        }
    }

    private static class CriteriaWitness implements SCMSourceRequest.Witness {
        private final BitbucketSCMSourceRequest request;

        public CriteriaWitness(BitbucketSCMSourceRequest request) {
            this.request = request;
        }

        @Override
        public void record(@NonNull SCMHead scmHead, SCMRevision revision, boolean isMatch) {
            if (revision == null) {
                request.listener().getLogger().println("    Skipped");
            } else {
                if (isMatch) {
                    request.listener().getLogger().println("    Met criteria");
                } else {
                    request.listener().getLogger().println("    Does not meet criteria");
                    return;
                }

            }
        }
    }

    private static class BitbucketProbeFactory<I> implements SCMSourceRequest.ProbeLambda<SCMHead, I> {
        private final BitbucketApi bitbucket;
        private final BitbucketSCMSourceRequest request;

        public BitbucketProbeFactory(BitbucketApi bitbucket, BitbucketSCMSourceRequest request) {
            this.bitbucket = bitbucket;
            this.request = request;
        }

        @NonNull
        @Override
        public Probe create(@NonNull final SCMHead head, @CheckForNull final I revisionInfo) throws IOException, InterruptedException {
            final String hash = (revisionInfo instanceof BitbucketCommit) //
                    ? ((BitbucketCommit) revisionInfo).getHash() //
                    : (String) revisionInfo;

            return new SCMSourceCriteria.Probe() {
                private static final long serialVersionUID = 1L;

                @Override
                public String name() {
                    return head.getName();
                }

                @Override
                public long lastModified() {
                    try {
                        BitbucketCommit commit = null;
                        if (hash != null) {
                            commit = (revisionInfo instanceof BitbucketCommit) //
                                    ? (BitbucketCommit) revisionInfo //
                                    : bitbucket.resolveCommit(hash);
                        }

                        if (commit == null) {
                            request.listener().getLogger().format("Can not resolve commit by hash [%s] on repository %s/%s%n", //
                                    hash, bitbucket.getOwner(), bitbucket.getRepositoryName());
                            return 0;
                        }
                        return commit.getDateMillis();
                    } catch (InterruptedException | IOException e) {
                        request.listener().getLogger().format("Can not resolve commit by hash [%s] on repository %s/%s%n", //
                                hash, bitbucket.getOwner(), bitbucket.getRepositoryName());
                        return 0;
                    }
                }

                @Override
                public boolean exists(@NonNull String path) throws IOException {
                    if (hash == null) {
                        request.listener().getLogger() //
                                .format("Can not resolve path for hash [%s] on repository %s/%s%n", //
                                        hash, bitbucket.getOwner(), bitbucket.getRepositoryName());
                        return false;
                    }

                    try {
                        return bitbucket.checkPathExists(hash, path);
                    } catch (InterruptedException e) {
                        throw new IOException("Interrupted", e);
                    }
                }
            };
        }
    }

    private class BitbucketRevisionFactory<I> implements SCMSourceRequest.LazyRevisionLambda<SCMHead, SCMRevision, I> {
        private final BitbucketApi client;

        public BitbucketRevisionFactory(BitbucketApi client) {
            this.client = client;
        }

        @NonNull
        @Override
        public SCMRevision create(@NonNull SCMHead head, @Nullable I input) throws IOException, InterruptedException {
            return create(head, input, null);
        }

        @NonNull
        public SCMRevision create(@NonNull SCMHead head,
                                  @Nullable I sourceInput,
                                  @Nullable I targetInput) throws IOException, InterruptedException {
            BitbucketCommit sourceCommit = asCommit(sourceInput);
            BitbucketCommit targetCommit = asCommit(targetInput);

            SCMRevision revision;
            if (head instanceof PullRequestSCMHead) {
                PullRequestSCMHead prHead = (PullRequestSCMHead) head;
                SCMHead targetHead = prHead.getTarget();

                return new PullRequestSCMRevision<>( //
                        prHead, //
                        new BitbucketGitSCMRevision(targetHead, targetCommit), //
                        new BitbucketGitSCMRevision(prHead, sourceCommit));
            } else {
                revision = new BitbucketGitSCMRevision(head, sourceCommit);
            }
            return revision;
        }

        private BitbucketCommit asCommit(I input) throws IOException, InterruptedException {
            if (input instanceof String) {
                return client.resolveCommit((String) input);
            } else if (input instanceof BitbucketCommit) {
                return (BitbucketCommit) input;
            }
            return null;
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
            return new StdDateFormat().format(new Date(branch.getDateMillis()));
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

    private static class WrappedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public WrappedException(Throwable cause) {
            super(cause);
        }

        public void unwrap() throws IOException, InterruptedException {
            Throwable cause = getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof InterruptedException) {
                throw (InterruptedException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw this;
        }

    }
}
