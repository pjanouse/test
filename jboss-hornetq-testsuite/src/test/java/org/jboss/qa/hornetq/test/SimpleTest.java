package org.jboss.qa.hornetq.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.clients.SecurityClient;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.HornetQAdminOperationsEAP6;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.JMSProvider;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigAfterTest;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/**
 *
 *
 *
 * @author mnovak@rehat.com
 */
//@RestoreConfigAfterTest
@RunWith(Arquillian.class)
public class SimpleTest extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(SimpleTest.class);
    String queueNamePrefix = "testQueue";
    String queueJndiNamePrefix = "jms/queue/testQueue";
    String jndiContextPrefix = "java:jboss/exported/";
    static boolean topologyCreated = false;

    /**
     * This test will start one server. And try to send/receive messages or
     * create/delete queue from it.
     */
    @Test
    @RunAsClient
//    @RestoreConfigAfterTest
    public void test() throws Exception {

//        prepareServer();
        logger.error("mnovak: " + System.getProperty("JMS_PROVIDER_CLASS"));
        controller.start(CONTAINER1);
        
        JMSOperations jmsAdminOperations = new JMSProvider().getInstance(CONTAINER1);
        jmsAdminOperations.addAddressSettings(CONTAINER1, queueNamePrefix, PORT_JNDI, PORT_JNDI, PORT_JNDI, PORT_JNDI);
        jmsAdminOperations.createQueue("testQueue", "queue/testQueue");
        jmsAdminOperations.createQueue("testTopic", "queue/testTopic");
        jmsAdminOperations.setPersistenceEnabled(false);
        Thread.sleep(5000);
        logger.info("I'm going to kill it");
        killServer(CONTAINER1);
        logger.info("KILLED");

        //controller.kill(CONTAINER1);

//        jmsAdminOperations.removeQueue("testQueue");
//        jmsAdminOperations.removeTopic("testTopic");
        
        logger.info("mnovak: server was started and queue deployed");
//        Thread.sleep(100000);

        stopServer(CONTAINER1);
    }

    @After
    public void stopAllServers() {

        stopServer(CONTAINER1);

    }

//    public void prepareServer() throws Exception {
//
//        if (!topologyCreated) {
//
//            prepareLiveServer(CONTAINER1, CONTAINER1_IP, JOURNAL_DIRECTORY_A);
//
//            controller.start(CONTAINER1);
//            
//            deployDestinations(CONTAINER1_IP, 9999);
//            
//            stopServer(CONTAINER1);
//            
//            topologyCreated = true;
//        }
//    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     * @param bindingAddress says on which ip container will be binded
     * @param journalDirectory path to journal directory
     */
    private void prepareLiveServer(String containerName, String bindingAddress, String journalDirectory) throws IOException {

        controller.start(containerName);
        
        JMSOperations jmsAdminOperations = JMSProvider.getInstance(containerName);
        
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

        // set security persmissions for roles admin,users - user is already there
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

        controller.stop(containerName);

    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param containerName container name
     */
    private void deployDestinations(String containerName) {
        deployDestinations(containerName, "default");
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param containerName container name
     * @param serverName server name of the hornetq server
     *
     */
    private void deployDestinations(String containerName, String serverName) {

        JMSOperations jmsAdminOperations = JMSProvider.getInstance(containerName);

        for (
                int queueNumber = 0; queueNumber < 3; queueNumber++) {
            jmsAdminOperations.createQueue(serverName, queueNamePrefix + queueNumber, jndiContextPrefix + queueJndiNamePrefix + queueNumber, true);
            
//            jmsAdminOperations.createQueue(serverName, queueNamePrefix + queueNumber, jndiContextPrefix + queueJndiNamePrefix + queueNumber, false);
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