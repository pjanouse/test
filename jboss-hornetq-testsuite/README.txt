HornetQ test suite:

How to make it work:

- Run PrepareServers.groovy script to prepare environment
    - groovy PrepareServers.groovy -DEAP.VERSION=6.3.0.ER10
- Set environment properties JBOSS_HOME_1..4 in bash shell to previously created directories

Run test suite
 - mvn clean install  -Darquillian.xml=arquillian-4-nodes.xml -Peap6x-common

Run one test
 - mvn clean install  -Darquillian.xml=arquillian-4-nodes.xml -Peap6x -Dtest=JmsBridgeAttributesTestCase

 Important!!!
 Add new test to "eap6x-common" profile if it should be there!


