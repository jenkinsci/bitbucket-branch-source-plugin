# Bitbucket Branch Source Plugin

[![Build](https://ci.jenkins.io/job/Plugins/job/bitbucket-branch-source-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/bitbucket-branch-source-plugin/job/master/)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/bitbucket-branch-source-plugin.svg?label=release)](https://github.com/jenkinsci/bitbucket-branch-source-plugin/releases/latest)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/cloudbees-bitbucket-branch-source?color=blue)](https://plugins.jenkins.io/cloudbees-bitbucket-branch-source)
[![Language grade: Java](https://img.shields.io/lgtm/grade/java/g/jenkinsci/bitbucket-branch-source-plugin.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/jenkinsci/bitbucket-branch-source-plugin/context:java)
[![Contributors](https://img.shields.io/github/contributors/jenkinsci/bitbucket-branch-source-plugin.svg)](https://github.com/jenkinsci/bitbucket-branch-source-plugin/contributors)
[![Join the chat](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/jenkinsci/bitbucket-branch-source-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## User Guide

[Browse the user guide here](docs/USER_GUIDE.adoc)

## Notes

* Unlike GitHub, in Bitbucket, [team admins do not have access to forks](https://bitbucket.org/site/master/issues/4828/team-admins-dont-have-read-access-to-forks).
This means that when you have a private repository, or a private fork of a public repository, the team admin will not be able to see the PRs within the fork.

## How-to run and test with Bitbucket Server locally

* [Install the Atlassian SDK on Linux or Mac](https://developer.atlassian.com/server/framework/atlassian-sdk/install-the-atlassian-sdk-on-a-linux-or-mac-system/) or [on Windows](https://developer.atlassian.com/server/framework/atlassian-sdk/install-the-atlassian-sdk-on-a-windows-system/)
* To run 5.2.0 server: `atlas-run-standalone -u 6.3.0 --product bitbucket --version 5.2.0 --data-version 5.2.0`
