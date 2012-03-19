package org.jboss.qa.hornetq.test.security;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.qa.hornetq.apps.clients.*;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSAdminOperations;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigAfterTest;
import org.jboss.qa.tools.byteman.annotation.BMRule;
import org.jboss.qa.tools.byteman.annotation.BMRules;
import org.jboss.qa.tools.byteman.rule.RuleInstaller;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
/**
 *
 * Test security permissions to queues and topic
 * 
 * Uses its own application-roles.properties, application-roles.properties
 * 
 * It creates its own address-settings in standalone-full-ha.xml, enables security.
 * 
 * There are 3 users and 3 roles:
 * user -> role (username/password)
 * admin - admin (admin/adminadmin)
 * user - user (user/useruser)
 * guest - guest (unauthenticated)
 * 
 * There is 1 queue/topic
 * name of queue/topic -> roles -> permission for the role
 * testQueue0/testTopic0    -> guest -> send,consume
 *                          -> admin -> all permissions
 *                          -> user -> send, consume, create/delete
 * 
 * 
 * @author mnovak@rehat.com
 */
@RunWith(Arquillian.class)
public class PermissionSecurityTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(PermissionSecurityTestCase.class);
    private static final int NUMBER_OF_QUEUES = 1;
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 10;
    private static final int NUMBER_OF_PRODUCERS_PER_QUEUE = 1;
    private static final int NUMBER_OF_RECEIVERS_PER_QUEUE = 1;
    
    String queueNamePrefix = "testQueue";
    String topicNamePrefix = "testTopic";
    String queueJndiNamePrefix = "jms/queue/testQueue";
    String topicJndiNamePrefix = "jms/topic/testTopic";
    String jndiContextPrefix = "java:jboss/exported/";

    /**
     * This test will start one server. And try to send/receive messages or create/delete queue from it.
     */
    @Test
    @RunAsClient
    public void testSecurity() throws Exception {
        
        prepareServer();

        controller.start(CONTAINER1);
        //TODO UPRAV KLIENTY ABY posilali credentials
        QueueClientsTransAck clients = new QueueClientsTransAck(CONTAINER1_IP, PORT_JNDI, queueJndiNamePrefix, NUMBER_OF_QUEUES, NUMBER_OF_PRODUCERS_PER_QUEUE, NUMBER_OF_RECEIVERS_PER_QUEUE, NUMBER_OF_MESSAGES_PER_PRODUCER);
        
        clients.startClients();

        while (!clients.isFinished()) {
            Thread.sleep(1000);
        }

        Assert.assertTrue(clients.evaluateResults());

        controller.stop(CONTAINER1);

    }


    @After
    public void stopAllServers() {

        controller.stop(CONTAINER1);

        deleteFolder(new File(JOURNAL_DIRECTORY_A));

    }

    public void prepareServer() throws Exception {

        prepareLiveServer(CONTAINER1, CONTAINER1_IP, JOURNAL_DIRECTORY_A);
        
        controller.start(CONTAINER1);
        deployDestinations(CONTAINER1_IP, 9999);
        controller.stop(CONTAINER1);

    }
    
    /**
     * Prepares live server for dedicated topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     * @param bindingAddress says on which ip container will be binded
     * @param journalDirectory path to journal directory
     */
    private void prepareLiveServer(String containerName, String bindingAddress, String journalDirectory) throws IOException {

        controller.start(containerName);

        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(bindingAddress, 9999);
        jmsAdminOperations.setInetAddress("public", bindingAddress);
        jmsAdminOperations.setInetAddress("unsecure", bindingAddress);
        jmsAdminOperations.setInetAddress("management", bindingAddress);

        jmsAdminOperations.setBindingsDirectory(journalDirectory);
        jmsAdminOperations.setPagingDirectory(journalDirectory);
        jmsAdminOperations.setJournalDirectory(journalDirectory);
        jmsAdminOperations.setLargeMessagesDirectory(journalDirectory);

        jmsAdminOperations.setJournalType("NIO");
        jmsAdminOperations.setPersistenceEnabled(true);
        
        jmsAdminOperations.setSecurityEnabled(true);

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);
        
        // set security persmissions for roles admin,users - guest is already there
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
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "create-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "create-non-durable-queue", true);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "delete-durable-queue", false);
        jmsAdminOperations.setPermissionToRoleToSecuritySettings("#", "users", "delete-non-durable-queue", false);
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
        
        controller.stop(containerName);

    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param hostname ip address where to bind to managemant interface
     * @param port port of management interface - it should be 9999
     */
    private void deployDestinations(String hostname, int port) {
        deployDestinations(hostname, port, "default");
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param hostname ip address where to bind to managemant interface
     * @param port port of management interface - it should be 9999
     * @param serverName server name of the hornetq server
     *
     */
    private void deployDestinations(String hostname, int port, String serverName) {

        JMSAdminOperations jmsAdminOperations = new JMSAdminOperations(hostname, port);

        for (int queueNumber = 0; queueNumber < NUMBER_OF_QUEUES; queueNumber++) {
            jmsAdminOperations.createQueue(serverName, queueNamePrefix + queueNumber, jndiContextPrefix + queueJndiNamePrefix + queueNumber, true);
        }

        for (int topicNumber = 0; topicNumber < NUMBER_OF_QUEUES; topicNumber++) {
            jmsAdminOperations.createTopic(serverName, topicNamePrefix + topicNumber, jndiContextPrefix + topicJndiNamePrefix + topicNumber);
        }
    }
    
    /**
     * Copies file from one place to another.
     * 
     * @param sourceFile source file
     * @param destFile destination file - file will be rewritten
     * @throws IOException 
     */
    public void copyFile(File sourceFile, File destFile) throws IOException {
        if (!destFile.exists()) {
            destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;

        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
        }
    }

}