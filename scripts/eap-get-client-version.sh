#!/bin/bash

EAP_VERSION=${1:-6.4.0.ER1}

CANDIDATE_BASE_URL=http://download.devel.redhat.com/devel/candidates/JBEAP/JBEAP-$EAP_VERSION
RELEASED_BASE_URL=http://download.devel.redhat.com/released/JBEAP-6/$EAP_VERSION
FILE=jboss-eap-${EAP_VERSION}-supported-GAVs.txt


function get_client_version() {
    curl --silent $1 | grep '^org.jboss.as:jboss-as-build:' | cut -d: -f3
}


if curl --output /dev/null --silent --fail $CANDIDATE_BASE_URL/$FILE; then
    get_client_version $CANDIDATE_BASE_URL/$FILE
else
    get_client_version $RELEASED_BASE_URL/$FILE
fi

