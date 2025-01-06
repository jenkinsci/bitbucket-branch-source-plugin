package com.cloudbees.jenkins.plugins.bitbucket.impl.extension.BitbucketEnvVarExtension

def l = namespace(lib.JenkinsTagLib)

// Base on javadoc in EnvironmentContributor Jenkins provides other extension points (such as SCM) to contribute environment variables to builds, and for those plugins, Jenkins also looks for /buildEnv.groovy and aggregates them.
['BITBUCKET_REPOSITORY', 'BITBUCKET_OWNER', 'BITBUCKET_PROJECT_KEY', 'BITBUCKET_SERVER_URL'].each {name ->
    l.buildEnvVar(name: name) {
        raw(_("${name}.blurb"))
    }
}