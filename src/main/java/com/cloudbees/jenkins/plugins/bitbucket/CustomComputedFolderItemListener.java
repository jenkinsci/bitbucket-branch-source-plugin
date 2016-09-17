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
package com.cloudbees.jenkins.plugins.bitbucket;

import static java.util.Arrays.asList;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import com.cloudbees.jenkins.plugins.bitbucket.CustomComputedFolderItemListener.Sniffer.OrgMatch;
import com.cloudbees.jenkins.plugins.bitbucket.CustomComputedFolderItemListener.Sniffer.RepoMatch;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketTeam;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.inject.Inject;

import hudson.BulkChange;
import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.AllView;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ListView;
import hudson.model.listeners.ItemListener;
import hudson.views.StatusColumn;
import hudson.views.WeatherColumn;
import jenkins.branch.Branch;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.OrganizationFolder;
import jenkins.scm.api.SCMNavigator;

// TODO: this code should be mostly extracted to scm-api, and then the dependency to branch-api can be removed.
@Extension
public class CustomComputedFolderItemListener extends ItemListener {

    @Inject
    private Applier applier;

    @Override
    public void onUpdated(Item item) {
        maybeApply(item);
    }

    @Override
    public void onCreated(Item item) {
        maybeApply(item);
    }

    private void maybeApply(Item item) {
        OrgMatch f = Sniffer.matchOrg(item);
        if (f != null && f.folder.getDisplayNameOrNull() == null) {
            applier.applyOrg(f);
        }
        RepoMatch r = Sniffer.matchRepo(item);
        if (r != null) {
            applier.applyRepo(r);
        }
    }

    @Extension
    @Restricted(NoExternalUse.class)
    public static class Applier {

        public void applyOrg(OrgMatch f) {
            if (UPDATING.get().add(f.folder)) {
                BulkChange bc = new BulkChange(f.folder);
                try {
                    StandardUsernamePasswordCredentials credentials = f.scm.getBitbucketConnector().lookupCredentials(f.folder,
                            f.scm.getCredentialsId(), StandardUsernamePasswordCredentials.class);
                    BitbucketTeam team = f.scm.getBitbucketConnector().create(f.scm.getRepoOwner(), credentials).getTeam();
                    if (team != null) {
                        try {
                            f.folder.setDisplayName(team.getDisplayName());
                            if (f.folder.getView("Repositories") == null && f.folder.getView("All") instanceof AllView) {
                                // need to set the default view
                                ListView lv = new ListView("Repositories");
                                lv.getColumns().replaceBy(asList(
                                    new StatusColumn(),
                                    new WeatherColumn(),
                                    new CustomNameJobColumn(Messages.class, Messages._ListViewColumn_Repository())
                                ));
                                lv.setIncludeRegex(".*");   // show all
                                f.folder.addView(lv);
                                f.folder.deleteView(f.folder.getView("All"));
                                f.folder.setPrimaryView(lv);
                            }
                            bc.commit();
                        } catch (IOException e) {
                            LOGGER.log(Level.INFO, "Can not set the Team/Project display name automatically. Skipping.");
                            LOGGER.log(Level.FINE, "StackTrace:", e);
                        }
                    }
                } finally {
                    bc.abort();
                    UPDATING.get().remove(f.folder);
                }
            }
        }

        public void applyRepo(RepoMatch r) {
            if (UPDATING.get().add(r.repo)) {
                BulkChange bc = new BulkChange(r.repo);
                try {
                    if (r.repo.getView("Branches") == null && r.repo.getView("All") instanceof AllView) {
                        // create initial views
                        ListView bv = new ListView("Branches");
                        bv.getJobFilters().add(new BranchJobFilter());

                        ListView pv = new ListView("Pull Requests");
                        pv.getJobFilters().add(new PullRequestJobFilter());

                        try {
                            r.repo.addView(bv);
                            r.repo.addView(pv);
                            r.repo.deleteView(r.repo.getView("All"));
                            r.repo.setPrimaryView(bv);
                            bc.commit();
                        } catch (IOException e) {
                            LOGGER.log(Level.INFO, "Can not set the repo/PR views. Skipping.");
                            LOGGER.log(Level.FINE, "StackTrace:", e);
                        }
                    }
                } finally {
                    bc.abort();
                    UPDATING.get().remove(r.repo);
                }
            }
        }

        /**
         * Keeps track of what we are updating to avoid recursion, because {@link AbstractItem#save()}
         * triggers {@link ItemListener}.
         */
        private final ThreadLocal<Set<Item>> UPDATING = new ThreadLocal<Set<Item>>() {
            @Override
            protected Set<Item> initialValue() {
                return new HashSet<>();
            }
        };
    }

    public static class Sniffer {

        static class OrgMatch {
            final OrganizationFolder folder;
            final BitbucketSCMNavigator scm;

            public OrgMatch(OrganizationFolder folder, BitbucketSCMNavigator scm) {
                this.folder = folder;
                this.scm = scm;
            }
        }

        public static OrgMatch matchOrg(Object item) {
            if (item instanceof OrganizationFolder) {
                OrganizationFolder of = (OrganizationFolder) item;
                List<SCMNavigator> navigators = of.getNavigators();
                if (/* could be called from constructor */navigators != null && navigators.size() > 0) {
                    SCMNavigator n = navigators.get(0);
                    if (n instanceof BitbucketSCMNavigator) {
                        return new OrgMatch(of, (BitbucketSCMNavigator) n);
                    }
                }
            }
            return null;
        }

        static class RepoMatch extends OrgMatch {
            final MultiBranchProject repo;

            public RepoMatch(OrgMatch x, MultiBranchProject repo) {
                super(x.folder, x.scm);
                this.repo = repo;
            }
        }

        public static RepoMatch matchRepo(Object item) {
            if (item instanceof MultiBranchProject) {
                MultiBranchProject repo = (MultiBranchProject) item;
                OrgMatch org = matchOrg(repo.getParent());
                if (org != null)
                    return new RepoMatch(org, repo);
            }
            return null;
        }

        static class BranchMatch extends RepoMatch {
            final Job branch;

            public BranchMatch(RepoMatch x, Job branch) {
                super(x,x.repo);
                this.branch = branch;
            }

            public Branch getScmBranch() {
                return repo.getProjectFactory().getBranch(branch);
            }
        }

        public static BranchMatch matchBranch(Item item) {
            if (item instanceof Job) {
                Job branch = (Job) item;
                RepoMatch x = matchRepo(item.getParent());
                if (x!=null) {
                    return new BranchMatch(x, branch);
                }
            }
            return null;
        }

    }

    private static final Logger LOGGER = Logger.getLogger(CustomComputedFolderItemListener.class.getName());

}
