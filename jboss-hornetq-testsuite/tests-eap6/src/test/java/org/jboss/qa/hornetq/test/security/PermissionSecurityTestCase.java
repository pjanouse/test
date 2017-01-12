package org.jboss.qa.hornetq.test.security;

import category.Functional;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

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
@Category(Functional.class)
public class PermissionSecurityTestCase extends HornetQTestCase {

    private final Archive mdbOnQueueToQueue = createDeploymentMdbOnQueue1Temp();

    private static final Logger logger = Logger.getLogger(PermissionSecurityTestCase.class);


    /**
     * This test will start one server. And try to send/receive messages or
     * create/delete queue from it.
     */
    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.ENABLE_SECURITY, value = "true"),
            @Param(name = PrepareParams.SECURITY_GUEST_CONSUME, value = "true"),
            @Param(name = PrepareParams.SECURITY_GUEST_CREATE_DURABLE_QUEUE, value = "false"),
            @Param(name = PrepareParams.SECURITY_GUEST_CREATE_NON_DURABLE_QUEUE, value = "false"),
            @Param(name = PrepareParams.SECURITY_GUEST_DELETE_DURABLE_QUEUE, value = "false"),
            @Param(name = PrepareParams.SECURITY_GUEST_DELETE_NON_DURABLE_QUEUE, value = "false"),
            @Param(name = PrepareParams.SECURITY_GUEST_MANAGE, value = "false"),
            @Param(name = PrepareParams.SECURITY_GUEST_SEND, value = "true")
    })
    public void testSecurityWithGuest() throws Exception {

        container(1).start();

        SecurityClient guest = null;
        try {

            guest = new SecurityClient(container(1), PrepareConstants.QUEUE_JNDI, 10, null, null);
            guest.initializeClient();

            try {
                guest.sendAndReceive();
            } catch (Exception ex) {
                Assert.fail("This should not fail. Exception: " + ex.getMessage());
            }

            try {
                guest.createDurableQueue(PrepareConstants.QUEUE_NAME);
                Assert.fail("This should fail. User guest should not have permission to create queue.");
            } catch (Exception ex) {
                // ignore
            }

            try {
                guest.deleteDurableQueue(PrepareConstants.QUEUE_NAME);
                Assert.fail("This should fail. User guest should not have permission to delete queue.");
            } catch (Exception ex) {
                // ignore
            }

            try {
                guest.createNonDurableQueue("jms.queue." + PrepareConstants.QUEUE_NAME_PREFIX + "nondurable");

                Assert.fail("This should fail. User guest should not have permission to create non-durable queue.");

            } catch (Exception ex) {
                // ignore
            }

            try {
                guest.deleteNonDurableQueue(PrepareConstants.QUEUE_NAME_PREFIX + "nondurable");
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
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.ENABLE_SECURITY, value = "true"),
            @Param(name = PrepareParams.SECURITY_USERS_CONSUME, value = "true"),
            @Param(name = PrepareParams.SECURITY_USERS_CREATE_DURABLE_QUEUE, value = "false"),
            @Param(name = PrepareParams.SECURITY_USERS_CREATE_NON_DURABLE_QUEUE, value = "true"),
            @Param(name = PrepareParams.SECURITY_USERS_DELETE_DURABLE_QUEUE, value = "false"),
            @Param(name = PrepareParams.SECURITY_USERS_DELETE_NON_DURABLE_QUEUE, value = "true"),
            @Param(name = PrepareParams.SECURITY_USERS_MANAGE, value = "true"),
            @Param(name = PrepareParams.SECURITY_USERS_SEND, value = "true")
    })
    public void testSecurityWithUser() throws Exception {

        container(1).start();

        SecurityClient user = null;

        try {
            user = new SecurityClient(container(1), PrepareConstants.QUEUE_JNDI, 10, PrepareConstants.USER_NAME, PrepareConstants.USER_PASS);
            user.initializeClient();

            try {
                user.sendAndReceive();
            } catch (Exception ex) {
                Assert.fail("This should not fail. Exception: " + ex.getMessage());
            }

            try {
                user.createDurableQueue(PrepareConstants.QUEUE_NAME);
                Assert.fail("This should fail. User 'user' should not have permission to create queue.");
            } catch (Exception ex) {
                // ignore
            }

            try {
                user.deleteDurableQueue(PrepareConstants.QUEUE_NAME);
                Assert.fail("This should fail. User 'user' should not have permission to delete queue.");
            } catch (Exception ex) {
                // ignore
            }

            try {
                user.createNonDurableQueue("jms.queue." + PrepareConstants.QUEUE_NAME_PREFIX + "nondurable");
            } catch (Exception ex) {
                Assert.fail("This should pass. User guest should have permission to create non-durable queue.");
            }

            try {
                user.deleteNonDurableQueue(PrepareConstants.QUEUE_NAME_PREFIX + "nondurable");

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
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.ENABLE_SECURITY, value = "true"),
            @Param(name = PrepareParams.SECURITY_ADMIN_CONSUME, value = "true"),
            @Param(name = PrepareParams.SECURITY_ADMIN_CREATE_DURABLE_QUEUE, value = "true"),
            @Param(name = PrepareParams.SECURITY_ADMIN_CREATE_NON_DURABLE_QUEUE, value = "true"),
            @Param(name = PrepareParams.SECURITY_ADMIN_DELETE_DURABLE_QUEUE, value = "true"),
            @Param(name = PrepareParams.SECURITY_ADMIN_DELETE_NON_DURABLE_QUEUE, value = "true"),
            @Param(name = PrepareParams.SECURITY_ADMIN_MANAGE, value = "true"),
            @Param(name = PrepareParams.SECURITY_ADMIN_SEND, value = "true")
    })
    public void testSecurityWithAdmin() throws Exception {

        container(1).start();

        SecurityClient admin = null;

        try {
            // try user admin
            admin = new SecurityClient(container(1), PrepareConstants.QUEUE_JNDI, 10, PrepareConstants.ADMIN_NAME, PrepareConstants.ADMIN_PASS);
            admin.initializeClient();

            try {
                admin.sendAndReceive();
            } catch (Exception ex) {
                Assert.fail("This should not fail:" + ex.getMessage());
            }
            try {
                admin.createDurableQueue(PrepareConstants.QUEUE_NAME);

            } catch (Exception ex) {
                Assert.fail("This should not fail:" + ex.getMessage());
            }

            try {
                admin.deleteDurableQueue(PrepareConstants.QUEUE_NAME);
            } catch (Exception ex) {
                Assert.fail("This should not fail:" + ex.getMessage());
                ex.printStackTrace();
            }

            try {
                admin.createNonDurableQueue("jms.queue." + PrepareConstants.QUEUE_NAME_PREFIX + "nondurable");
            } catch (Exception ex) {
                Assert.fail("This should not fail:" + ex.getMessage());
            }

            try {
                admin.deleteNonDurableQueue(PrepareConstants.QUEUE_NAME_PREFIX + "nondurable");
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
    @Prepare(value = "OneNode", params = {
            @Param(name = PrepareParams.ENABLE_SECURITY, value = "true"),
            @Param(name = PrepareParams.SECURITY_USERS_CONSUME, value = "true"),
            @Param(name = PrepareParams.SECURITY_USERS_CREATE_DURABLE_QUEUE, value = "false"),
            @Param(name = PrepareParams.SECURITY_USERS_CREATE_NON_DURABLE_QUEUE, value = "true"),
            @Param(name = PrepareParams.SECURITY_USERS_DELETE_DURABLE_QUEUE, value = "false"),
            @Param(name = PrepareParams.SECURITY_USERS_DELETE_NON_DURABLE_QUEUE, value = "true"),
            @Param(name = PrepareParams.SECURITY_USERS_MANAGE, value = "true"),
            @Param(name = PrepareParams.SECURITY_USERS_SEND, value = "true")
    })
    public void inVmSecurityTestCase() throws Exception {

        container(1).start();
        container(1).deploy(mdbOnQueueToQueue);
        JMSOperations jmsAdminOperations = container(1).getJmsOperations();
        HashMap<String, String> opts = new HashMap<String, String>();
        opts.put("password-stacking", "useFirstPass");
        jmsAdminOperations.rewriteLoginModule("Remoting", opts);
        jmsAdminOperations.rewriteLoginModule("RealmDirect", opts);
        jmsAdminOperations.overrideInVMSecurity(false);

        container(1).restart();
        SecurityClient producer = new SecurityClient(container(1), PrepareConstants.IN_QUEUE_JNDI, 10, PrepareConstants.USER_NAME, PrepareConstants.USER_PASS);
        producer.initializeClient();
        producer.send();
        producer.join();

        Thread.sleep(2000);

        jmsAdminOperations = container(1).getJmsOperations();
        long count = jmsAdminOperations.getCountOfMessagesOnQueue(PrepareConstants.OUT_QUEUE_NAME);
        Assert.assertEquals("Mdb shouldn't be able to send any message to outQueue", 0, count);
        container(1).stop();
    }

    public JavaArchive createDeploymentMdbOnQueue1Temp() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "localMdbFromQueue.jar");
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
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