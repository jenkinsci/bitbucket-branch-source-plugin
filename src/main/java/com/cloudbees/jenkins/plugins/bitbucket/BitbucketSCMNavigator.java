/*
 * The MIT License
 *
 * Copyright (c) 2016-2017, CloudBees, Inc.
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
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketCloudWorkspace;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketTeam;
import com.cloudbees.jenkins.plugins.bitbucket.client.repository.UserRoleInRepository;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.AbstractBitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketCloudEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketEndpointConfiguration;
import com.cloudbees.jenkins.plugins.bitbucket.endpoints.BitbucketServerEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.impl.avatars.BitbucketTeamAvatarMetadataAction;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketCredentials;
import com.cloudbees.jenkins.plugins.bitbucket.impl.util.MirrorListSupplier;
import com.cloudbees.jenkins.plugins.bitbucket.server.BitbucketServerWebhookImplementation;
import com.cloudbees.jenkins.plugins.bitbucket.trait.ShowBitbucketAvatarTrait;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.console.HyperlinkNote;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.security.AccessControlled;
import hudson.util.FormFillFailure;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import jenkins.plugins.git.traits.GitBrowserSCMSourceTrait;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMNavigatorDescriptor;
import jenkins.scm.api.SCMNavigatorEvent;
import jenkins.scm.api.SCMNavigatorOwner;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCategory;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMNavigatorRequest;
import jenkins.scm.api.trait.SCMNavigatorTrait;
import jenkins.scm.api.trait.SCMNavigatorTraitDescriptor;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMTrait;
import jenkins.scm.api.trait.SCMTraitDescriptor;
import jenkins.scm.impl.UncategorizedSCMSourceCategory;
import jenkins.scm.impl.form.NamedArrayList;
import jenkins.scm.impl.trait.Discovery;
import jenkins.scm.impl.trait.Selection;
import org.apache.commons.lang.StringUtils;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import static com.cloudbees.jenkins.plugins.bitbucket.impl.util.BitbucketApiUtils.getFromBitbucket;

public class BitbucketSCMNavigator extends SCMNavigator {

    @NonNull
    private String serverUrl;
    @CheckForNull
    private String credentialsId;
    @CheckForNull
    private String mirrorId;
    @NonNull
    private final String repoOwner;
    @CheckForNull
    private String projectKey;
    @NonNull
    private List<SCMTrait<? extends SCMTrait<?>>> traits;

    @DataBoundConstructor
    public BitbucketSCMNavigator(String repoOwner) {
        this.serverUrl = BitbucketCloudEndpoint.SERVER_URL;
        this.repoOwner = repoOwner;
        this.traits = new ArrayList<>();
        this.credentialsId = null; // highlighting the default is anonymous unless you configure explicitly
    }

    @Deprecated // retained for binary compatibility
    public BitbucketSCMNavigator(String repoOwner, String credentialsId, String checkoutCredentialsId) {
        this.serverUrl = BitbucketCloudEndpoint.SERVER_URL;
        this.repoOwner = repoOwner;
        this.traits = new ArrayList<>();
        this.credentialsId = Util.fixEmpty(credentialsId);
        // code invoking legacy constructor will want the legacy discovery model
        this.traits.add(new BranchDiscoveryTrait(true, true));
        this.traits.add(new OriginPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD)));
        this.traits.add(new ForkPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD),
                new ForkPullRequestDiscoveryTrait.TrustEveryone()));
        this.traits.add(new PublicRepoPullRequestFilterTrait());
        if (checkoutCredentialsId != null
                && !BitbucketSCMSource.DescriptorImpl.SAME.equals(checkoutCredentialsId)) {
            this.traits.add(new SSHCheckoutTrait(checkoutCredentialsId));
        }
    }

    @CheckForNull
    public String getCredentialsId() {
        return credentialsId;
    }

    @CheckForNull
    public String getMirrorId() {
        return mirrorId;
    }

    public String getRepoOwner() {
        return repoOwner;
    }

    public String getProjectKey() {
        return projectKey;
    }

    @DataBoundSetter
    public void setProjectKey(@CheckForNull String projectKey) {
        this.projectKey = Util.fixEmpty(projectKey);
    }

    @Override
    @NonNull
    public List<SCMTrait<? extends SCMTrait<?>>> getTraits() {
        return Collections.unmodifiableList(traits);
    }

    @DataBoundSetter
    public void setCredentialsId(@CheckForNull String credentialsId) {
        this.credentialsId = Util.fixEmpty(credentialsId);
    }

    @DataBoundSetter
    public void setMirrorId(String mirrorId) {
        this.mirrorId = Util.fixEmpty(mirrorId);
    }

    /**
     * Sets the behavioural traits that are applied to this navigator and any {@link BitbucketSCMSource} instances it
     * discovers. The new traits will take effect on the next navigation through any of the
     * {@link #visitSources(SCMSourceObserver)} overloads or {@link #visitSource(String, SCMSourceObserver)}.
     *
     * @param traits the new behavioural traits.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @DataBoundSetter
    public void setTraits(@CheckForNull SCMTrait[] traits) {
        // the reduced generics in the method signature are a workaround for JENKINS-26535
        this.traits = new ArrayList<>();
        if (traits != null) {
            for (SCMTrait trait : traits) {
                this.traits.add(trait);
            }
        }
    }

    /**
     * Sets the behavioural traits that are applied to this navigator and any {@link BitbucketSCMSource} instances it
     * discovers. The new traits will take effect on the next navigation through any of the
     * {@link #visitSources(SCMSourceObserver)} overloads or {@link #visitSource(String, SCMSourceObserver)}.
     *
     * @param traits the new behavioural traits.
     */
    @Override
    public void setTraits(@CheckForNull List<SCMTrait<? extends SCMTrait<?>>> traits) {
        this.traits = traits != null ? new ArrayList<>(traits) : new ArrayList<SCMTrait<? extends SCMTrait<?>>>();
    }

    public String getServerUrl() {
        return serverUrl;
    }

    @DataBoundSetter
    public void setServerUrl(@CheckForNull String serverUrl) {
        serverUrl = BitbucketEndpointConfiguration.normalizeServerUrl(serverUrl);
        if (serverUrl != null && !StringUtils.equals(this.serverUrl, serverUrl)) {
            this.serverUrl = serverUrl;
            resetId();
        }
    }

    @NonNull
    public String getEndpointJenkinsRootUrl() {
        return AbstractBitbucketEndpoint.getEndpointJenkinsRootUrl(serverUrl);
    }

    @NonNull
    @Override
    protected String id() {
        return serverUrl + "::" + repoOwner;
    }

    @Override
    public void visitSources(SCMSourceObserver observer) throws IOException, InterruptedException {
        TaskListener listener = observer.getListener();

        if (StringUtils.isBlank(repoOwner)) {
            listener.getLogger().format("Must specify a repository owner%n");
            return;
        }
        StandardCredentials credentials = BitbucketCredentials.lookupCredentials(
                serverUrl,
                observer.getContext(),
                credentialsId,
                StandardCredentials.class
        );

        if (credentials == null) {
            listener.getLogger().format("Connecting to %s with no credentials, anonymous access%n", serverUrl);
        } else {
            listener.getLogger()
                    .format("Connecting to %s using %s%n", serverUrl, CredentialsNameProvider.name(credentials));
        }
        try (final BitbucketSCMNavigatorRequest request = new BitbucketSCMNavigatorContext()
                .withTraits(traits)
                .newRequest(this, observer)) {
            SourceFactory sourceFactory = new SourceFactory(request);
            WitnessImpl witness = new WitnessImpl(request, listener);

            BitbucketAuthenticator authenticator = AuthenticationTokens.convert(BitbucketAuthenticator.authenticationContext(serverUrl), credentials);

            BitbucketApi bitbucket = BitbucketApiFactory.newInstance(serverUrl, authenticator, repoOwner, projectKey, null);
            BitbucketTeam team = bitbucket.getTeam();
            if (team != null) {
                // Navigate repositories of the team
                listener.getLogger().format("Looking up repositories of team %s%n", repoOwner);
                request.withRepositories(bitbucket.getRepositories());
            } else {
                // Navigate the repositories of the repoOwner as a user
                listener.getLogger().format("Looking up repositories of user %s%n", repoOwner);
                request.withRepositories(bitbucket.getRepositories(UserRoleInRepository.ADMIN));
            }
            for (BitbucketRepository repo : request.repositories()) {
                if (request.process(repo.getRepositoryName(), sourceFactory, null, witness)) {
                    listener.getLogger().format(
                            "%d repositories were processed (query completed)%n", witness.getCount()
                    );
                }
            }
            listener.getLogger().format("%d repositories were processed%n", witness.getCount());
        }
    }

    @NonNull
    @Override
    public List<Action> retrieveActions(@NonNull SCMNavigatorOwner owner,
                                        @CheckForNull SCMNavigatorEvent event,
                                        @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        // TODO when we have support for trusted events, use the details from event if event was from trusted source
        listener.getLogger().printf("Looking up team details of %s...%n", getRepoOwner());
        List<Action> result = new ArrayList<>();
        StandardCredentials credentials = BitbucketCredentials.lookupCredentials(
                serverUrl,
                owner,
                credentialsId,
                StandardCredentials.class
        );

        if (credentials == null) {
            listener.getLogger().format("Connecting to %s with no credentials, anonymous access%n",
                    serverUrl);
        } else {
            listener.getLogger().format("Connecting to %s using %s%n",
                    serverUrl,
                    CredentialsNameProvider.name(credentials));
        }

        BitbucketAuthenticator authenticator = AuthenticationTokens.convert(BitbucketAuthenticator.authenticationContext(serverUrl), credentials);

        try (BitbucketApi client = BitbucketApiFactory.newInstance(serverUrl, authenticator, repoOwner, projectKey, null)) {
            BitbucketTeam team = client.getTeam();
            String avatarURL = null;
            String teamURL;
            String teamDisplayName;
            if (team != null) {
                if (showAvatar()) {
                    avatarURL = team.getAvatar();
                }
                teamURL = team.getLink("html");
                teamDisplayName = StringUtils.defaultIfBlank(team.getDisplayName(), team.getName());
                if (StringUtils.isNotBlank(teamURL)) {
                    if (team instanceof BitbucketCloudWorkspace wks) {
                        teamURL = serverUrl + "/" + wks.getSlug();
                    } else {
                        teamURL = serverUrl + "/projects/" + team.getName();
                    }
                }
                listener.getLogger().printf("Team: %s%n", HyperlinkNote.encodeTo(teamURL, teamDisplayName));
            } else {
                teamURL = serverUrl + "/" + repoOwner;
                teamDisplayName = repoOwner;
                listener.getLogger().println("Could not resolve team details");
            }
            result.add(new ObjectMetadataAction(teamDisplayName, null, teamURL));
            result.add(new BitbucketTeamAvatarMetadataAction(avatarURL, serverUrl, owner.getFullName(), credentialsId));
            result.add(new BitbucketLink("icon-bitbucket-logo", teamURL));
            return result;
        }
    }

    private boolean showAvatar() {
        return traits.stream().anyMatch(ShowBitbucketAvatarTrait.class::isInstance);
    }

    @Symbol("bitbucket")
    @Extension
    public static class DescriptorImpl extends SCMNavigatorDescriptor {

        public static final String ANONYMOUS = BitbucketSCMSource.DescriptorImpl.ANONYMOUS;
        public static final String SAME = BitbucketSCMSource.DescriptorImpl.SAME;

        @Override
        public String getDisplayName() {
            return Messages.BitbucketSCMNavigator_DisplayName();
        }

        @Override
        public String getDescription() {
            return Messages.BitbucketSCMNavigator_Description();
        }

        @Override
        public String getIconFilePathPattern() {
            return "plugin/cloudbees-bitbucket-branch-source/images/bitbucket-scmnavigator.svg";
        }

        @Override
        public String getIconClassName() {
            return "icon-bitbucket-scm-navigator";
        }

        @Override
        public SCMNavigator newInstance(String name) {
            BitbucketSCMNavigator instance = new BitbucketSCMNavigator(StringUtils.defaultString(name));
            instance.setTraits(getTraitsDefaults());
            return instance;
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

        @RequirePOST
        @SuppressWarnings("unused") // used By stapler
        public static FormValidation doCheckCredentialsId(@AncestorInPath SCMSourceOwner context,
                                                          @QueryParameter(fixEmpty = true, value = "serverUrl") String serverURL,
                                                          @QueryParameter String value) {
            return BitbucketCredentials.checkCredentialsId(context, value, serverURL);
        }

        @RequirePOST
        @SuppressWarnings("unused") // used By stapler
        public static FormValidation doCheckMirrorId(@QueryParameter String value,
                                                     @QueryParameter(fixEmpty = true, value = "serverUrl") String serverURL) {
            if (!value.isEmpty()) {
                BitbucketServerWebhookImplementation webhookImplementation =
                    BitbucketServerEndpoint.findWebhookImplementation(serverURL);
                if (webhookImplementation == BitbucketServerWebhookImplementation.PLUGIN) {
                    return FormValidation.error("Mirror can only be used with native webhooks");
                }
            }
            return FormValidation.ok();
        }

        @SuppressWarnings("unused") // used By stapler
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath SCMSourceOwner context,
                                                     @QueryParameter(fixEmpty = true, value = "serverUrl") String serverURL) {
            return BitbucketCredentials.fillCredentialsIdItems(context, serverURL);
        }

        @SuppressWarnings("unused") // used By stapler
        public ListBoxModel doFillMirrorIdItems(@AncestorInPath SCMSourceOwner context,
                                                @QueryParameter(fixEmpty = true, value = "serverUrl") String serverUrl,
                                                @QueryParameter String credentialsId,
                                                @QueryParameter String repoOwner)
            throws FormFillFailure {
            return getFromBitbucket(context, serverUrl, credentialsId, repoOwner, null, MirrorListSupplier.INSTANCE);
        }

        public List<NamedArrayList<? extends SCMTraitDescriptor<?>>> getTraitsDescriptorLists() {
            BitbucketSCMSource.DescriptorImpl sourceDescriptor =
                    Jenkins.get().getDescriptorByType(BitbucketSCMSource.DescriptorImpl.class);
            List<SCMTraitDescriptor<?>> all = new ArrayList<>();
            all.addAll(
                    SCMNavigatorTrait._for(this, BitbucketSCMNavigatorContext.class, BitbucketSCMSourceBuilder.class));
            all.addAll(SCMSourceTrait._for(sourceDescriptor, BitbucketSCMSourceContext.class, null));
            all.addAll(SCMSourceTrait._for(sourceDescriptor, null, BitbucketGitSCMBuilder.class));
            Set<SCMTraitDescriptor<?>> dedup = new HashSet<>();
            for (Iterator<SCMTraitDescriptor<?>> iterator = all.iterator(); iterator.hasNext(); ) {
                SCMTraitDescriptor<?> d = iterator.next();
                if (dedup.contains(d)
                        || d instanceof GitBrowserSCMSourceTrait.DescriptorImpl) {
                    // remove any we have seen already and ban the browser configuration as it will always be bitbucket
                    iterator.remove();
                } else {
                    dedup.add(d);
                }
            }
            List<NamedArrayList<? extends SCMTraitDescriptor<?>>> result = new ArrayList<>();
            NamedArrayList.select(all, "Repositories", SCMNavigatorTraitDescriptor.class::isInstance, true, result);
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
        @NonNull
        public List<SCMTrait<? extends SCMTrait<?>>> getTraitsDefaults() {
            return Arrays.asList(
                    new BranchDiscoveryTrait(true, false),
                    new OriginPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE)),
                    new ForkPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE),
                            new ForkPullRequestDiscoveryTrait.TrustTeamForks())
            );
        }

        @NonNull
        @Override
        protected SCMSourceCategory[] createCategories() {
            return new SCMSourceCategory[]{
                    new UncategorizedSCMSourceCategory(
                            Messages._BitbucketSCMNavigator_UncategorizedSCMSourceCategory_DisplayName())
            };
        }

        static {
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-scm-navigator icon-sm",
                            "plugin/cloudbees-bitbucket-branch-source/images/bitbucket-scmnavigator.svg",
                            Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-scm-navigator icon-md",
                            "plugin/cloudbees-bitbucket-branch-source/images/bitbucket-scmnavigator.svg",
                            Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-scm-navigator icon-lg",
                            "plugin/cloudbees-bitbucket-branch-source/images/bitbucket-scmnavigator.svg",
                            Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-scm-navigator icon-xlg",
                            "plugin/cloudbees-bitbucket-branch-source/images/bitbucket-scmnavigator.svg",
                            Icon.ICON_XLARGE_STYLE));

            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-logo icon-sm",
                            "plugin/cloudbees-bitbucket-branch-source/images/bitbucket-logo.svg",
                            Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-logo icon-md",
                            "plugin/cloudbees-bitbucket-branch-source/images/bitbucket-logo.svg",
                            Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-logo icon-lg",
                            "plugin/cloudbees-bitbucket-branch-source/images/bitbucket-logo.svg",
                            Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-logo icon-xlg",
                            "plugin/cloudbees-bitbucket-branch-source/images/bitbucket-logo.svg",
                            Icon.ICON_XLARGE_STYLE));

            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-repo icon-sm",
                            "plugin/cloudbees-bitbucket-branch-source/images/bitbucket-repository.svg",
                            Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-repo icon-md",
                            "plugin/cloudbees-bitbucket-branch-source/images/bitbucket-repository.svg",
                            Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-repo icon-lg",
                            "plugin/cloudbees-bitbucket-branch-source/images/bitbucket-repository.svg",
                            Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-repo icon-xlg",
                            "plugin/cloudbees-bitbucket-branch-source/images/bitbucket-repository.svg",
                            Icon.ICON_XLARGE_STYLE));

            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-repo-git icon-sm",
                            "plugin/cloudbees-bitbucket-branch-source/images/bitbucket-repository-git.svg",
                            Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-repo-git icon-md",
                            "plugin/cloudbees-bitbucket-branch-source/images/bitbucket-repository-git.svg",
                            Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-repo-git icon-lg",
                            "plugin/cloudbees-bitbucket-branch-source/images/bitbucket-repository-git.svg",
                            Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-repo-git icon-xlg",
                            "plugin/cloudbees-bitbucket-branch-source/images/bitbucket-repository-git.svg",
                            Icon.ICON_XLARGE_STYLE));

            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-branch icon-sm",
                            "plugin/cloudbees-bitbucket-branch-source/images/bitbucket-branch.svg",
                            Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-branch icon-md",
                            "plugin/cloudbees-bitbucket-branch-source/images/bitbucket-branch.svg",
                            Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-branch icon-lg",
                            "plugin/cloudbees-bitbucket-branch-source/images/bitbucket-branch.svg",
                            Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-bitbucket-branch icon-xlg",
                            "plugin/cloudbees-bitbucket-branch-source/images/bitbucket-branch.svg",
                            Icon.ICON_XLARGE_STYLE));
        }
    }

    private static class WitnessImpl implements SCMNavigatorRequest.Witness {
        private int count;

        private final BitbucketSCMNavigatorRequest request;
        private final TaskListener listener;

        public WitnessImpl(BitbucketSCMNavigatorRequest request, TaskListener listener) {
            this.request = request;
            this.listener = listener;
        }

        @Override
        public void record(@NonNull String name, boolean isMatch) {
            BitbucketRepository repository = this.request.getBitbucketRepository(name);

            if (isMatch) {
                listener.getLogger().format("Proposing %s%n", repository.getFullName());
                count++;
            } else {
                listener.getLogger().format("Ignoring %s%n", repository.getFullName());
            }
        }

        public int getCount() {
            return count;
        }
    }

    private class SourceFactory implements SCMNavigatorRequest.SourceLambda {
        private final BitbucketSCMNavigatorRequest request;

        public SourceFactory(BitbucketSCMNavigatorRequest request) {
            this.request = request;
        }

        @NonNull
        @Override
        public SCMSource create(@NonNull String projectName) throws IOException, InterruptedException {
            return new BitbucketSCMSourceBuilder(
                    getId() + "::" + projectName,
                    serverUrl,
                    credentialsId,
                    repoOwner,
                    projectName,
                    mirrorId)
                    .withRequest(request)
                    .build();
        }
    }
}
