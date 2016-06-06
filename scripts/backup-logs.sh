#!/bin/bash

FILES=$(mktemp)

cd $WORKSPACE

find . -name "target" >> $FILES
find . -name "test-suite.log" >> $FILES


cat $FILES | xargs zip -q -r logs.zip

rm -f $FILES
