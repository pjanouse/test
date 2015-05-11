package org.jboss.qa.hornetq.test.security;

import org.apache.commons.io.FileUtils;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.tools.CheckServerAvailableUtils;
import org.jboss.shrinkwrap.api.Archive;
import org.junit.After;
import org.junit.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

/**
 * Test security permissions to queues and topic
 * <p/>
 * Uses its own application-roles.properties, application-roles.properties
 * <p/>
 * It creates its own address-settings in standalone-full-ha.xml, enables
 * security.
 * <p/>
 * There are 3 users and 3 roles: admin -> role (username/password) admin -
 * admin (admin/adminadmin) admin - admin (admin/useruser) user - user
 * (unauthenticated)
 * <p/>
 * There is 1 queue/topic name of queue/topic -> roles -> permission for the
 * role testQueue0 -> user -> send,consume -> admin -> all permissions -> admin
 * -> send, consume, create/delete durable queue
 *
 * @author mnovak@rehat.com
 */
@RunWith(Arquillian.class)
@Category(FunctionalTests.class)
public class PermissionSecurityTestCase extends HornetQTestCase {

    private static final String USER_NAME = "user";
    private static final String USER_PASSWORD = "useruser";

    private static final String ADMIN_NAME = "admin";
    private static final String ADMIN_PASSWORD = "adminadmin";


    private final Archive mdbOnQueueToQueue = createDeploymentMdbOnQueue1Temp();

    private static final Logger logger = Logger.getLogger(PermissionSecurityTestCase.class);


    String queueNamePrefix = "testQueue";
    String queueJndiNamePrefix = "jms/queue/testQueue";

    // InQueue and OutQueue for mdb
    static String inQueueNameForMdb = "InQueue";
    static String inQueueJndiNameForMdb = "jms/queue/" + inQueueNameForMdb;
    static String outQueueNameForMdb = "OutQueue";
    static String outQueueJndiNameForMdb = "jms/queue/" + outQueueNameForMdb;

    String jndiContextPrefix = "java:jboss/exported/";

    /**
     * This test will start one server. And try to send/receive messages or
     * create/delete queue from it.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testSecurityWithGuest() throws Exception {

        prepareServer();

        container(1).start();

        SecurityClient guest = null;
        try {

            guest = new SecurityClient(container(1), queueJndiNamePrefix + "0", 10, null, null);
            guest.initializeClient();

            try {
                guest.sendAndReceive();
            } catch (Exception ex) {
                Assert.fail("This should not fail. Exception: " + ex.getMessage());
            }

            try {
                guest.createDurableQueue(queueNamePrefix + "0");
                Assert.fail("This should fail. User guest should not have permission to create queue.");
            } catch (Exception ex) {
                // ignore
            }

            try {
                guest.deleteDurableQueue(queueNamePrefix + "0");
                Assert.fail("This should fail. User guest should not have permission to delete queue.");
            } catch (Exception ex) {
                // ignore
            }

            try {
                guest.createNonDurableQueue("jms.queue." + queueNamePrefix + "nondurable");

                Assert.fail("This should fail. User guest should not have permission to create non-durable queue.");

            } catch (Exception ex) {
                // ignore
            }

            try {
                guest.deleteNonDurableQueue(queueNamePrefix + "nondurable");
                Assert.fail("This should fail. User guest should not have permission to delete non-durable queue.");
            } catch (Exception ex) {
                // ignore
            }

        } finally {
            if (guest != null) {
                guest.close();
            }
        }
        container(1).stop();
    }

    /**
     * This test will start one server. And try to send/receive messages or
     * create/delete queue from it.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testSecurityWithUser() throws Exception {

        prepareServer();

        container(1).start();

        SecurityClient user = null;

        try {
            user = new SecurityClient(container(1), queueJndiNamePrefix + "1", 10, USER_NAME, USER_PASSWORD);
            user.initializeClient();

            try {
                user.sendAndReceive();
            } catch (Exception ex) {
                Assert.fail("This should not fail. Exception: " + ex.getMessage());
            }

            try {
                user.createDurableQueue(queueNamePrefix + "1");
                Assert.fail("This should fail. User 'user' should not have permission to create queue.");
            } catch (Exception ex) {
                // ignore
            }

            try {
                user.deleteDurableQueue(queueNamePrefix + "1");
                Assert.fail("This should fail. User 'user' should not have permission to delete queue.");
            } catch (Exception ex) {
                // ignore
            }

            try {
                user.createNonDurableQueue("jms.queue." + queueNamePrefix + "nondurable");
            } catch (Exception ex) {
                Assert.fail("This should pass. User guest should have permission to create non-durable queue.");
            }

            try {
                user.deleteNonDurableQueue(queueNamePrefix + "nondurable");

            } catch (Exception ex) {
                ex.printStackTrace();
                Assert.fail("This should pass. User 'user' should have permission to delete non-durable queue.");
            }
        } finally {
            if (user != null) {
                user.close();
            }
        }

        container(1).stop();

    }

    /**
     * This test will start one server. And try to send/receive messages or
     * create/delete queue from it.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testSecurityWithAdmin() throws Exception {

        prepareServer();

        container(1).start();

        SecurityClient admin = null;

        try {
            // try user admin
            admin = new SecurityClient(container(1), queueJndiNamePrefix + "2", 10, "admin", "adminadmin");
            admin.initializeClient();

            try {
                admin.sendAndReceive();
            } catch (Exception ex) {
                Assert.fail("This should not fail:" + ex.getMessage());
            }
            try {
                admin.createDurableQueue(queueNamePrefix + "2");

            } catch (Exception ex) {
                Assert.fail("This should not fail:" + ex.getMessage());
            }

            try {
                admin.deleteDurableQueue(queueNamePrefix + "2");
            } catch (Exception ex) {
                Assert.fail("This should not fail:" + ex.getMessage());
                ex.printStackTrace();
            }

            try {
                admin.createNonDurableQueue("jms.queue." + queueNamePrefix + "nondurable");
            } catch (Exception ex) {
                Assert.fail("This should not fail:" + ex.getMessage());
            }

            try {
                admin.deleteNonDurableQueue(queueNamePrefix + "nondurable");
            } catch (Exception ex) {
                Assert.fail("This should not fail.");
            }

        } finally {
            if (admin != null) {
                admin.close();
            }
        }

        container(1).stop();

    }

    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    //TODO This test will fail on EAP 6.4.0.DR13 and older
    public void inVmSecurityTestCase() throws Exception {

        prepareServer();
        container(1).start();
        container(1).deploy(mdbOnQueueToQueue);
        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        HashMap<String, String> opts = new HashMap<String, String>();
        opts.put("password-stacking", "useFirstPass");
        jmsAdminOperations.rewriteLoginModule("Remoting", opts);
        jmsAdminOperations.rewriteLoginModule("RealmDirect", opts);
        jmsAdminOperations.overrideInVMSecurity(false);

        container(1).restart();
        SecurityClient producer = new SecurityClient(container(1), inQueueJndiNameForMdb, 10, "user", "useruser");
        producer.initializeClient();
        producer.send();
        producer.join();

        Thread.sleep(2000);

        jmsAdminOperations = container(1).getJmsOperations();
        long count = jmsAdminOperations.getCountOfMessagesOnQueue(outQueueNameForMdb);
        Assert.assertEquals("Mdb shouldn't be able to send any message to outQueue", 0, count);
        container(1).stop();
    }

    @After
    public void stopServerIfAlive() {
        if (CheckServerAvailableUtils.checkThatServerIsReallyUp(container(1).getHostname(), container(1).getPort())) {
            container(1).stop();
        }
    }

    public void prepareServer() throws Exception {
        prepareServer(container(1));
    }

    /**
     *
     * @param container        test container
     */
    private void prepareServer(Container container) throws IOException {
        container.start();
        JMSOperations jmsAdminOperations = container.getJmsOperations();

        jmsAdminOperations.setJournalType("NIO");
        jmsAdminOperations.setPersistenceEnabled(true);

        jmsAdminOperations.setSecurityEnabled(true);

        jmsAdminOperations.removeClusteringGroup("my-cluster");

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

        // set authentication for null users
        jmsAdminOperations.setAuthenticationForNullUsers(true);

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

        for (
                int queueNumber = 0; queueNumber < 3; queueNumber++) {
            jmsAdminOperations.createQueue(queueNamePrefix + queueNumber, jndiContextPrefix + queueJndiNamePrefix + queueNumber, true);
        }
        jmsAdminOperations.createQueue(inQueueNameForMdb, inQueueJndiNameForMdb, true);
        jmsAdminOperations.createQueue(outQueueNameForMdb, outQueueJndiNameForMdb, true);

        jmsAdminOperations.close();

        UsersSettings.forDefaultEapServer()
                .create();
        UsersSettings.forDefaultEapServer()
                .withUser(USER_NAME, USER_PASSWORD, USER_NAME)
                .create();
        UsersSettings.forDefaultEapServer()
                .withUser(ADMIN_NAME, ADMIN_PASSWORD, ADMIN_NAME)
                .create();

//        // TODO it's hard to write admin operation for security so this hack
//
//        // copy application-users.properties
//        // copy application-roles.properties
//        File applicationUsersModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-users.properties");
//        File applicationUsersOriginal = new File(System.getProperty("JBOSS_HOME_1") + File.separator + "standalone" + File.separator
//                + "configuration" + File.separator + "application-users.properties");
//        FileUtils.copyFile(applicationUsersModified, applicationUsersOriginal);
//
//        File applicationRolesModified = new File("src/test/resources/org/jboss/qa/hornetq/test/security/application-roles.properties");
//        File applicationRolesOriginal = new File(System.getProperty("JBOSS_HOME_1") + File.separator + "standalone" + File.separator
//                + "configuration" + File.separator + "application-roles.properties");
//        FileUtils.copyFile(applicationRolesModified, applicationRolesOriginal);
        container.stop();
    }

    public JavaArchive createDeploymentMdbOnQueue1Temp() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "localMdbFromQueue.jar");
        mdbJar.addClass(LocalMdbFromQueue.class);
        logger.info(mdbJar.toString(true));
//        File target = new File("/tmp/mdbOnQueue1.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

}