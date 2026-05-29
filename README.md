

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
   - run `/opt/atlassian-plugin-sdk/bin/atlas-run-standalone --product bitbucket --version 9.6.5 --data-version 9.6.5 "-Dfeature.public.access=true"`
