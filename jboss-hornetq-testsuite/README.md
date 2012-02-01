HornetQ test suite:

How to make it work:

1. Download and unzip AS7 distribution
2. Set "jbossHome" in src/test/resources/arquillian.xml for all containers - each container should have its own AS/EAP distribution folder
2.1 copy "src/test/resources/org/jboss/hornetq/configurations/AS71CR1b/standalone-ha.xml" to each server
Now let's get all dependecies:
3. run "mvn clean install" in arquillian-extension-kill project
4. download, unzip and copy maven repo from http://download.devel.redhat.com/devel/candidates/JBEAP/JBEAP-6.0.0-DR12/ to ~/.m2/repository/ 
    -- only hornetq client jars, jms-api is needed from there - should be made better
5. now cross fingers and try "mvn clean install -DJBOSS_HOME_1=/home/mnovak/tmp/hornetq_eap6_dev/internal/eap-tests-hornetq/server0/jboss-as-7.1.0.CR1b -DJBOSS_HOME_2=/home/mnovak/tmp/hornetq_eap6_dev/internal/eap-tests-hornetq/server1/jboss-as-7.1.0.CR1b"

Test packages:

org.jboss.hornetq.apps.clients - some JMS clients
org.jboss.hornetq.apps.servlets - refactored MRG servlet
org.jboss.hornetq.rule,org.jboss.hornetq.annotation - support for byteman 
org.jboss.hornetq.test.cluster - cluster tests
org.jboss.hornetq.test.failover - HA tests
org.jboss.hornetq.test.faultinjection - fault injection tests

Configuration files for AS7 will be stored in - "src/test/resources/org/jboss/hornetq/configurations/as-7.1.CR1b directory"