package org.jboss.qa.artemis.test.security;

import org.apache.log4j.Logger;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.mdb.LocalMdbFromQueue;
import org.jboss.qa.hornetq.tools.ContainerUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import java.util.HashMap;


public class PermissionSecurityTestBase extends HornetQTestCase {


    private final Archive mdbOnQueueToQueue = createDeploymentMdbOnQueue1Temp();

    private static final Logger logger = Logger.getLogger(PermissionSecurityTestBase.class);

    String queueNamePrefix = "testQueue";
    String queueJndiNamePrefix = "jms/queue/testQueue";

    // InQueue and OutQueue for mdb
    static String inQueueNameForMdb = "InQueue";
    static String inQueueJndiNameForMdb = "jms/queue/" + inQueueNameForMdb;
    static String outQueueNameForMdb = "OutQueue";
    static String outQueueJndiNameForMdb = "jms/queue/" + outQueueNameForMdb;


    protected void securityWithGuest() throws Exception {

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


    protected void securityWithUser() throws Exception {

        container(1).start();

        SecurityClient user = null;

        try {
            user = new SecurityClient(container(1), queueJndiNamePrefix + "1", 10, "user", "useruser");
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

    protected void securityWithAdmin() throws Exception {

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

    protected void inVmSecurityTestCase() throws Exception {

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
    @Before
    public void stopServerIfAlive() {
        container(1).stop();
    }

    public JavaArchive createDeploymentMdbOnQueue1Temp() {
        final JavaArchive mdbJar = ShrinkWrap.create(JavaArchive.class, "localMdbFromQueue.jar");
        mdbJar.addClass(LocalMdbFromQueue.class);
        JMSImplementation jmsImplementation = ContainerUtils.getJMSImplementation(container(1));
        mdbJar.addClass(JMSImplementation.class);
        mdbJar.addClass(jmsImplementation.getClass());
        mdbJar.addAsServiceProvider(JMSImplementation.class, jmsImplementation.getClass());
        logger.info(mdbJar.toString(true));
//        File target = new File("/tmp/mdbOnQueue1.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        mdbJar.as(ZipExporter.class).exportTo(target, true);
        return mdbJar;
    }

}
