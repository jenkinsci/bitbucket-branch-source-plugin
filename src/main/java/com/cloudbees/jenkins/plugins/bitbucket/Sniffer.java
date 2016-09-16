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

import java.util.List;

import jenkins.branch.OrganizationFolder;
import jenkins.scm.api.SCMNavigator;

public class Sniffer {

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

}
