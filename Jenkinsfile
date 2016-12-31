#!groovy

/* Only keep the 10 most recent builds. */
properties([[$class: 'BuildDiscarderProperty',
                strategy: [$class: 'LogRotator', numToKeepStr: '10']]])

node ("master") {
  stage 'Checkout'
  checkout scm

  stage 'Build'

  /* Call the maven build. */
  mvn "clean install -B -V -U -e -Dsurefire.useFile=false -Dmaven.test.failure.ignore=true"

  /* Save Results. */
  stage 'Results'

  /* Archive the test results */
  step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])

  /* Archive the build artifacts */
  step([$class: 'ArtifactArchiver', artifacts: 'target/*.hpi,target/*.jpi'])
}

/* Run maven from tool "mvn" */
void mvn(def args) {
  /* Get jdk tool. */
  String jdktool = '/usr' //tool name: "jdk7", type: 'hudson.model.JDK'

  /* Get the maven tool. */
  def mvnHome = '/usr'

  /* Set JAVA_HOME, and special PATH variables. */
  List javaEnv = [
    "PATH+JDK=${jdktool}/bin", "JAVA_HOME=${jdktool}",
  ]

  /* Call maven tool with java envVars. */
  withEnv(javaEnv) {
    timeout(time: 45, unit: 'MINUTES') {
      if (isUnix()) {
        sh "${mvnHome}/bin/mvn ${args}"
      } else {
        bat "${mvnHome}\\bin\\mvn ${args}"
      }
    }
  }
}
