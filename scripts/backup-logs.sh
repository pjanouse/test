#!/bin/bash

LOGS=$(mktemp)
REPORTS=$(mktemp)

cd $WORKSPACE

find . -name "target" >> $LOGS
find . -name "test-suite.log" >> $LOGS

find . -name "*.xml" | grep "surefire-reports" >> $REPORTS

cat $LOGS | xargs zip -q -r logs.zip
cat $REPORTS | xargs zip -q -r reports.zip

rm -f $LOGS
rm -f $REPORTS
