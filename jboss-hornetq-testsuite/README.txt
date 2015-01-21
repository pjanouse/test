HornetQ test suite:

Change JMS client version if necessary (for example when running with different EAP version) by setting property, for example:
-Deap6.clients.version=6.4.0.ER1

How to make it work:

- Run PrepareServers.groovy script to prepare environment
    - groovy -DEAP.VERSION=6.3.0.ER10 PrepareServers.groovy (this will download distribution)
    - groovy -DEAP_ZIP_URL=file:///home/mnovak/tmp/jboss-eap-6.2.4-patched.zip
        -DNATIVES_URL=file:///home/mnovak/tmp/jboss-eap-native-6.2.0-RHEL6-x86_64.zip PrepareServers.groovy (bits are on local disk)
- Set environment properties JBOSS_HOME_1..4 in bash shell to previously created directories

Run test suite
 - mvn clean install -Peap6x-common

Run one test
 - mvn clean install -Dtest=JmsBridgeAttributesTestCase

 Important!!!
 Add new test to "eap6x-common" profile if it should be there!


