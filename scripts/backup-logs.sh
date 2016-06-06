#!/bin/bash

FILES=$(mktemp)

find $WORKSPACE -name "target" >> $FILES
find $WORKSPACE -name "test-suite.log" >> $FILES


cd $WORKSPACE
cat $FILES | while read LINE; do realpath --relative-to="$WORKSPACE" $LINE; done | xargs zip -r $WORKSPACE/logs.zip

rm -f $FILES
