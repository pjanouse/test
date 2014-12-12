#!/bin/bash


MVN_REPO=${1-.}


function eap_artifact_version() {
    package=${1%%:*}
    artifact=${1##*:}

    jar=$(find $MVN_REPO/${package//./\/}/$artifact -name "$artifact-*redhat-*.jar" | sort | tail -n 1)
    version=$(echo $jar | sed -e "s!.*/$artifact-\(.*[\.-]redhat-.*\).jar!\1!")

    echo "    versions << '$package:$artifact:$version'"
}


function noneap_artifact_version() {
    package=${1%%:*}
    artifact=${1##*:}

    jar=$(find $MVN_REPO/${package//./\/}/$artifact -name "$artifact-*redhat-*.jar" | sort | tail -n 1)
    version=$(echo $jar | sed -e "s!.*/$artifact-\(.*\)[\.-]redhat-.*.jar!\1!")

    echo "    versions << '$package:$artifact:$version'"
}


eap_artifact_version 'io.netty:netty'
#eap_artifact_version 'log4j:log4j'
eap_artifact_version 'org.jboss:jboss-common-core'
eap_artifact_version 'org.jboss:jbossxb'
eap_artifact_version 'org.jboss.logging:jboss-logging'
eap_artifact_version 'org.jboss.logmanager:jboss-logmanager'
eap_artifact_version 'org.jboss.resteasy:resteasy-atom-provider'
eap_artifact_version 'org.jboss.resteasy:resteasy-jackson-provider'
eap_artifact_version 'org.jboss.resteasy:resteasy-jaxb-provider'
eap_artifact_version 'org.jboss.spec.javax.jms:jboss-jms-api_1.1_spec'
eap_artifact_version 'org.jboss.spec.javax.transaction:jboss-transaction-api_1.1_spec'
eap_artifact_version 'org.jgroups:jgroups'

echo

noneap_artifact_version 'com.sun.xml.bind:jaxb-impl'
noneap_artifact_version 'org.codehaus.jackson:jackson-core-asl'
noneap_artifact_version 'org.codehaus.jackson:jackson-jaxrs'
noneap_artifact_version 'org.codehaus.jackson:jackson-mapper-asl'
noneap_artifact_version 'org.codehaus.jackson:jackson-xc'
noneap_artifact_version 'xerces:xercesImpl'
