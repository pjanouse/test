#!/bin/bash

LOGS=$(mktemp)
REPORTS=$(mktemp)

cd $WORKSPACE

find . -name "target" >> $LOGS
find . -name "test-suite.log" >> $LOGS

# zip log directories of servers 
# this is good in case that build timeouted and we want to see the log directories of all servers
find -name log | grep jboss-eap >> $LOGS
find -name "*thread-dump*" | grep jboss-eap >> $LOGS

find . -name "*.xml" | grep "surefire-reports" >> $REPORTS

cat $LOGS
cat $LOGS | xargs zip -r logs.zip

cat $REPORTS
cat $REPORTS | xargs zip -r reports.zip

rm -f $LOGS
rm -f $REPORTS
