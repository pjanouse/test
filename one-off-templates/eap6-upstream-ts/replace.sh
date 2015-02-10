#!/bin/bash
#
# Replace compiled classes in upstream testsuite with classes from EAP
#
# Usage:
#  ./replace.sh [eap root] [hornetq repo root]
#

EAP_HOME=$1
REPO_ROOT=$2

rm -rf $REPO_ROOT/hornetq-core-client/target/classes/*
rm -rf $REPO_ROOT/hornetq-core-client/src/*
unzip -q -d $REPO_ROOT/hornetq-core-client/target/classes \
    $EAP_HOME/modules/system/layers/base/org/hornetq/main/hornetq-core-client*.jar

rm -rf $REPO_ROOT/hornetq-server/target/classes/*
rm -rf $REPO_ROOT/hornetq-server/src/*
unzip -q -d $REPO_ROOT/hornetq-server/target/classes \
    $EAP_HOME/modules/system/layers/base/org/hornetq/main/hornetq-server*.jar

rm -rf $REPO_ROOT/hornetq-commons/target/classes/*
rm -rf $REPO_ROOT/hornetq-commons/src/*
unzip -q -d $REPO_ROOT/hornetq-commons/target/classes \
    $EAP_HOME/modules/system/layers/base/org/hornetq/main/hornetq-commons*.jar

rm -rf $REPO_ROOT/hornetq-journal/target/classes/*
rm -rf $REPO_ROOT/hornetq-journal/src/*
unzip -q -d $REPO_ROOT/hornetq-journal/target/classes \
    $EAP_HOME/modules/system/layers/base/org/hornetq/main/hornetq-journal*.jar

rm -rf $REPO_ROOT/hornetq-jms-client/target/classes/*
rm -rf $REPO_ROOT/hornetq-jms-client/src/*
unzip -q -d $REPO_ROOT/hornetq-jms-client/target/classes \
    $EAP_HOME/modules/system/layers/base/org/hornetq/main/hornetq-jms-client*.jar

rm -rf $REPO_ROOT/hornetq-jms-server/target/classes/*
rm -rf $REPO_ROOT/hornetq-jms-server/src/*
unzip -q -d $REPO_ROOT/hornetq-jms-server/target/classes \
    $EAP_HOME/modules/system/layers/base/org/hornetq/main/hornetq-jms-server*.jar

rm -rf $REPO_ROOT/hornetq-ra/target/classes/*
rm -rf $REPO_ROOT/hornetq-ra/src/*
unzip -q -d $REPO_ROOT/hornetq-ra/target/classes \
    $EAP_HOME/modules/system/layers/base/org/hornetq/ra/main/hornetq-ra*.jar

