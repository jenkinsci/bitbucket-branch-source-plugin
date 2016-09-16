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

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cloudbees.jenkins.plugins.bitbucket.Sniffer.OrgMatch;
import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketTeam;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;

// TODO: this code should be mostly extracted to scm-api, and then the branch-api dependency can be removed.
@Extension
public class DisplayNameItemListener extends ItemListener {

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
            StandardUsernamePasswordCredentials credentials = f.scm.getBitbucketConnector().lookupCredentials(f.folder,
                    f.scm.getCredentialsId(), StandardUsernamePasswordCredentials.class);
            BitbucketTeam team = f.scm.getBitbucketConnector().create(f.scm.getRepoOwner(), credentials).getTeam();
            if (team != null) {
                try {
                    f.folder.setDisplayName(team.getDisplayName());
                    f.folder.save();
                } catch (IOException e) {
                    LOGGER.log(Level.INFO, "Can not set the Team/Project display name automatically. Skipping.");
                    LOGGER.log(Level.FINE, "StackTrace:", e);
                }
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(DisplayNameItemListener.class.getName());

}
