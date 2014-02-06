HornetQ test suite:

How to make it work:

1. Download and unzip EAP 6.x distribution, unzip and copy to 4 directories
2. Set environment properties JBOSS_HOME_1..4 in bash shell
2.1 Set environment properties MYTESTIP_1..4 in bash shell
2.3 Copy standalone-full-ha.xml to $JBOSS_HOME_1..4/standalone/configuration/standalone-full-ha.xml from https://svn.devel.redhat.com/repos/jboss-qa/load-testing/etc/eap-60/hornetq/configuration-files/

Run test suite
 - mvn clean install  -Darquillian.xml=arquillian-4-nodes.xml -Peap6x-common

Run one test
 - mvn clean install  -Darquillian.xml=arquillian-4-nodes.xml -Peap6x -Dtest=JmsBridgeAttributesTestCase

 Important!!!
 Add new test to "eap6x-common" profile if it should be there!


