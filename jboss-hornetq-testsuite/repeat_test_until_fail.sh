#!/bin/bash
for i in {1..5}
do
        echo "Run test for the $i time."        

        mvn clean test -Dtest=$1   -DfailIfNoTests=false  -Deap=7x | tee log

        export GREP=`grep "Failures: 0, Errors: 0" log`
        echo $GREP
        if [  x$GREP==x ] 
        then
                echo "Breaking loop - there is failure"
                break              #Abandon the loop.
        fi
done

