HornetQ test suite:

Change JMS client version if necessary (for example when running with different EAP version) by setting property, for example:
-Deap6.org.jboss.qa.hornetq.apps.clients.version=6.4.0.ER1

How to make it work with EAP7:

from eap-tests-hornetq dir run following commands

cd scripts/

git checkout refactoring_modules

groovy -DEAP_VERSION=7.0.0.ER4  PrepareServers7.groovy

export WORKSPACE=$PWD

export JBOSS_HOME_1=$WORKSPACE/server1/jboss-eap

export JBOSS_HOME_2=$WORKSPACE/server2/jboss-eap

export JBOSS_HOME_3=$WORKSPACE/server3/jboss-eap

export JBOSS_HOME_4=$WORKSPACE/server4/jboss-eap

cd ../jboss-hornetq-testsuite/

mvn clean test  -DfailIfNoTests=false  -Deap=7x  



How to make it work with EAP6:

from eap-tests-hornetq dir run following commands

cd scripts/

git checkout refactoring_modules

groovy -DEAP_VERSION=6.4.0  PrepareServers.groovy

export WORKSPACE=$PWD

export JBOSS_HOME_1=$WORKSPACE/server1/jboss-eap

export JBOSS_HOME_2=$WORKSPACE/server2/jboss-eap

export JBOSS_HOME_3=$WORKSPACE/server3/jboss-eap

export JBOSS_HOME_4=$WORKSPACE/server4/jboss-eap

cd ../jboss-hornetq-testsuite/

mvn clean test  -DfailIfNoTests=false 
