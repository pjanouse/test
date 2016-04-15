#!/bin/bash
for i in {1..50}
do
        echo "Run test for the $i time."        

        mvn clean test -Dtest=$1   -DfailIfNoTests=false  -Deap=7x -Deap7.org.jboss.qa.hornetq.apps.clients.version=7.0.0.CR1-with-all-fixes-2 | tee log

        export GREP=`grep "Failures: 0, Errors: 0" log`
        echo $GREP
        if [  x$GREP==x ] 
        then
                echo "Breaking loop - there is failure"
                exit 1
        fi
done
exit 0
