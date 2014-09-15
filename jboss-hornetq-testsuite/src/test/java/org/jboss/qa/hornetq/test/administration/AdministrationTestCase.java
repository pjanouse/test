package org.jboss.qa.hornetq.test.administration;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

/**
 * Test administration console - cli
 *
 * @author mnovak@rehat.com
 */
@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class AdministrationTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(AdministrationTestCase.class);

    String queueNamePrefix = "testQueue";
    String topicNamePrefix = "testTopic";
    String queueJndiNamePrefix = "jms/queue/testQueue";
    String topicJndiNamePrefix = "jms/topic/testTopic";
    String jndiContextPrefix = "java:jboss/exported/";

    @After
    public void stopAllServers() {

        stopServer(CONTAINER1);

        deleteFolder(new File(JOURNAL_DIRECTORY_A));

    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    public void testConfiguration() throws IOException {
        configure(CONTAINER1, getHostname(CONTAINER1), JOURNAL_DIRECTORY_A);
    }

    /**
     * Test all possible things. Failed operation simply throw RuntimeException
     *
     * @param containerName    Name of the container - defined in arquillian.xml
     * @param bindingAddress   says on which ip container will be bound
     * @param journalDirectory path to journal directory
     */
    public void configure(String containerName, String bindingAddress, String journalDirectory) throws IOException {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        int udpGroupPort = 9875;
        int broadcastBindingPort = 56880;
        String serverName = "default";

        controller.start(containerName);


        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);
        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);


        jmsAdminOperations.setClustered(true);

        jmsAdminOperations.setSharedStore(true);

        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);

        jmsAdminOperations.setJournalType("NIO");
        jmsAdminOperations.setPersistenceEnabled(true);

        jmsAdminOperations.setSecurityEnabled(false);

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

        jmsAdminOperations.createInVmAcceptor("my-acceptor", 32, null);
        jmsAdminOperations.createRemoteAcceptor("remote-acceptor", "messaging", null);

        jmsAdminOperations.removeBroadcastGroup(broadCastGroupName);
        jmsAdminOperations.setBroadCastGroup(broadCastGroupName, bindingAddress, broadcastBindingPort, MCAST_ADDRESS, udpGroupPort, 2000, connectorName, "");

        jmsAdminOperations.removeDiscoveryGroup(discoveryGroupName);
        jmsAdminOperations.setDiscoveryGroup(discoveryGroupName, bindingAddress, MCAST_ADDRESS, udpGroupPort, 10000);

        jmsAdminOperations.setHaForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setBlockOnAckForConnectionFactory(connectionFactoryName, true);
        jmsAdminOperations.setRetryIntervalForConnectionFactory(connectionFactoryName, 1000L);
        jmsAdminOperations.setRetryIntervalMultiplierForConnectionFactory(connectionFactoryName, 1.0);
        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, -1);

        jmsAdminOperations.addRemoteSocketBinding("messaging-bridge", getHostname(CONTAINER1), getHornetqPort(CONTAINER1));
        jmsAdminOperations.createRemoteConnector("bridge-connector", "messaging-bridge", null);

        jmsAdminOperations.removeClusteringGroup(clusterGroupName);
        jmsAdminOperations.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true, connectorName);

        // set security permissions for roles admin,users - user is already there
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "create-non-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "delete-non-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "manage", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "guest", "send", true);

        jmsAdminOperations.addRoleToSecuritySettings("#", "admin");
        jmsAdminOperations.addRoleToSecuritySettings("#", "users");

        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "delete-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "admin", "send", true);

        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "consume", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "create-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "delete-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "delete-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "manage", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "send", true);

        // TODO it's hard to write admin operation for security so this hack
        // copy application-users.properties
        // copy application-roles.properties
        File applicationUsersModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
        File applicationUsersOriginal = new File(System.getProperty("JBOSS_HOME_1") + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-users.properties");
        copyFile(applicationUsersModified, applicationUsersOriginal);

        File applicationRolesModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");
        File applicationRolesOriginal = new File(System.getProperty("JBOSS_HOME_1") + File.separator + "standalone" + File.separator
                + "configuration" + File.separator + "application-roles.properties");
        copyFile(applicationRolesModified, applicationRolesOriginal);

        for (int queueNumber = 0; queueNumber < 3; queueNumber++) {
            jmsAdminOperations.createQueue(serverName, queueNamePrefix + queueNumber, jndiContextPrefix + queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < 3; topicNumber++) {
            jmsAdminOperations.createTopic(serverName, topicNamePrefix + topicNumber, jndiContextPrefix + topicJndiNamePrefix + topicNumber);
        }

        controller.stop(containerName);

    }

}