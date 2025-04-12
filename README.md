# Bitbucket Branch Source Plugin

[![Build](https://ci.jenkins.io/job/Plugins/job/bitbucket-branch-source-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/bitbucket-branch-source-plugin/job/master/)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/bitbucket-branch-source-plugin.svg?label=release)](https://github.com/jenkinsci/bitbucket-branch-source-plugin/releases/latest)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/cloudbees-bitbucket-branch-source?color=blue)](https://plugins.jenkins.io/cloudbees-bitbucket-branch-source)
[![Contributors](https://img.shields.io/github/contributors/jenkinsci/bitbucket-branch-source-plugin.svg)](https://github.com/jenkinsci/bitbucket-branch-source-plugin/contributors)
[![Join the chat](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/jenkinsci/bitbucket-branch-source-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## User Guide

[Browse the user guide here](docs/USER_GUIDE.adoc)

## Issue tracking (where file new issues?)

At the time of writing, the Jenkins organisation let to the maintainer the choice of which issue tracker to use. Available are Github or [JIRA](https://issues.jenkins.io) issue tracker.
For reason discussed in the Jenkins Developers Google Group the official issue tracking for this plugin is [JIRA](https://issues.jenkins.io/browse/JENKINS-74965?jql=project%20%3D%20JENKINS%20AND%20component%20%3D%20bitbucket-branch-source-plugin%20ORDER%20BY%20created%20DESC).

What means:
* new issues must be open at JIRA
* the Github tracker will be froozen, that means no new issues will be accepted
* depending on the progress I expect that by the date 01/06/2025 the Github issue tracker will be disabled at all

What will happen to the issues opened in Github?
* actual issues will be processed
* maybe resolved
* otherwise after 6 months, it will be manually migrated by me to JIRA if it doesn't already exist

## Notes

### General notes

Unlike GitHub, in Bitbucket, [team admins do not have access to forks](https://bitbucket.org/site/master/issues/4828/team-admins-dont-have-read-access-to-forks).
This means that when you have a private repository, or a private fork of a public repository, the team admin will not be able to see the PRs within the fork.

### Developers and DevOps notes

Classes under the packages `com.cloudbees.jenkins.plugins.bitbucket.api` is intended to be public api and can be used to extend functionality in other plugins. Changes in the method signature will be marked with @deprecated providing an alternative new signature or class to use. After a reasonable time (about a year) the method could be removed at all. If some methods are not intended to be used then are marked with `@Restricted(NoExternalUse.class)`.

Classes in other packages are not intended to be used outside of this plugin. Signature can be changed in any moment, backward compatibility are no guaranteed.

When implementing a pipeline (scripted or declarative) we encourage the use of symbols instead of using the name (or fully qualified name) of the class. Symbols are safer against possible reorganization of the plugin code (classic examples: renaming the class or moving it to different packages).

Compliant example:

```
multibranch:
  branchSource:
    bitbucket:
      repoOwner: 'organization'
      repository: 'repository'
      credentialsId: 'bitbucket-credentials'
      traits:
        - bitbucketBranchDiscovery:
            strategyId: 1
        - bitbucketSshCheckout:
            credentialsId: 'bitbucket-ssh-credentials'
```

Noncompliant code example:

```
multibranch:
  branchSource:
    bitbucket:
      repoOwner: 'organization'
      repository: 'repository'
      credentialsId: 'bitbucket-credentials'
      traits:
        - $class: 'BranchDiscoveryTrait'
            strategyId: 1
        - $class: 'com.cloudbees.jenkins.plugins.bitbucket.SSHCheckoutTrait':
            credentialsId: 'bitbucket-ssh-credentials'
```

### Release notes (for maintainers)

To perform a release of this plugin the minimum requirements are:
 * Maven 3.9.9
 * JDK 17
 * git 2.39.x

In a shell or Windows terminal run

`mvn -B -ntp release:prepare release:perform "-Pquick-build" "-P-block-MRP"`

## How-to run and test with Bitbucket Server locally

### Install in local PC

1. [Install the Atlassian SDK on Linux or Mac](https://developer.atlassian.com/server/framework/atlassian-sdk/install-the-atlassian-sdk-on-a-linux-or-mac-system/)
2. Install git
3. To run latest server: `atlas-run-standalone --product bitbucket`

Support to run Server under Windows has been dismissed since version 7.14+

### Run inside docker

1. run `docker pull nolddor/atlassian-sdk:17-jdk`
2. run `docker run -it -p 7990:7990 -p 7999:7999 -v %USER%\.m2:/root/.m2 nolddor/atlassian-sdk:17-jdk`
3. Inside the container:
   - install git with `apk add git`
   - install git support for http with `apk add git-daemon`
   - run `/opt/atlassian-plugin-sdk/bin/atlas-run-standalone --product bitbucket --version 9.5.2`
