#!/bin/bash

LOGS=$(mktemp)
REPORTS=$(mktemp)

cd $WORKSPACE

find . -name "target" >> $LOGS
find . -name "test-suite.log" >> $LOGS

find . -name "*.xml" | grep "surefire-reports" >> $REPORTS

cat $LOGS
cat $LOGS | xargs zip -q -r logs.zip

# zip log directories of servers 
# this is good in case that build timeouted and we want to see the log directories of all servers
find -name log | grep jboss-eap | xargs zip -r logs.zip

cat $REPORTS
cat $REPORTS | xargs zip -q -r reports.zip

rm -f $LOGS
rm -f $REPORTS
