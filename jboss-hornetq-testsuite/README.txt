HornetQ test suite:

How to make it work:

1. Download and unzip AS7 distribution
2. Set JBOSS_HOME_1 and JBOSS_HOME_2 in bash shell
2.1 copy "src/test/resources/org/jboss/hornetq/configurations/AS71CR1b/standalone-ha.xml" to each server (or what version of AS you have)
Now let's get all dependecies:
3. run "mvn clean install" in arquillian-extension-kill project
4. now cross fingers and try "mvn clean install -DJBOSS_HOME_1=/home/mnovak/tmp/hornetq_eap6_dev/internal/eap-tests-hornetq/server0/jboss-as-7.1.0.CR1b -DJBOSS_HOME_2=/home/mnovak/tmp/hornetq_eap6_dev/internal/eap-tests-hornetq/server1/jboss-as-7.1.0.CR1b"

Test packages:

org.jboss.hornetq.apps.clients - some JMS clients
org.jboss.hornetq.apps.servlets - refactored MRG servlet
org.jboss.hornetq.rule,org.jboss.hornetq.annotation - support for byteman 
org.jboss.hornetq.test.cluster - cluster tests
org.jboss.hornetq.test.failover - HA tests
org.jboss.hornetq.test.faultinjection - fault injection tests

Configuration files for AS7 will be stored dependent on AS7/EAP6 version in - "src/test/resources/org/jboss/hornetq/configurations/AS... directory"
