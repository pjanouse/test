package org.jboss.qa.hornetq.test.cluster;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.qa.Param;
import org.jboss.qa.Prepare;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.JMSTools;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.qa.hornetq.test.prepares.PrepareConstants;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.*;
import javax.naming.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.jboss.qa.hornetq.constants.Constants.CONNECTION_FACTORY_JNDI_EAP7;
import static org.junit.Assert.assertTrue;

/**
 * @author mstyk@redhat.com
 * @tpChapter Integration testing
 * @tpSubChapter HORNETQ CLUSTER - TEST SCENARIOS
 * @tpTestCaseDetails Test case covers test developed for purpose of RFE tbd.
 * Tests are intended to verify it is possible to disable automatic client topology updates in cluster.
 *
 * Feature not implemented yet. This is just prepared testcase, unignore when feature is ready. See https://issues.jboss.org/browse/EAP7-669
 *
 */
@RunWith(Arquillian.class)
@RestoreConfigBeforeTest
@Prepare(value = "TwoNodes", params = {
        //TODO disable client topology updates when this feature becomes available
        @Param(name = PrepareParams.CLUSTER_TYPE, value = "MULTICAST")
})
public class DisableClientTopologyUpdatesTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(DisableClientTopologyUpdatesTestCase.class);

    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

    final List<Integer> server1Connections = new ArrayList<>();
    final List<Integer> server2Connections = new ArrayList<>();


    /**
     * @tpTestDetails Start two server in cluster with disabled topology updates.
     * Create several connections on server1, and verify no connection is made on
     * other servers in cluster, e.g. client doesn't have topology and is aware only
     * of one server
     * @tpProcedure <ul>
     * <li>start two servers (nodes) in cluster with disabled topology updates</li>
     * <li>lookup connection factory</li>
     * <li>reuse connection factory to create connections on server1</li>
     * <li>verify no connection is made on server2</li>
     * </ul>
     * @tpPassCrit all client connections are made to server1
     * @tpInfo For more information see related test case described in the
     * beginning of this section.
     */
    @Ignore("Feature not implemented yet. This is just prepared testcase, unignore when feature is ready. See https://issues.jboss.org/browse/EAP7-669")
    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void testDisableClientTopologyUpdated() throws Exception {
        container(1).start();
        container(2).start();

        final JMSOperations jmsOpsContainer1 = container(1).getJmsOperations();
        final JMSOperations jmsOpsContainer2 = container(2).getJmsOperations();

        setUpExecutor(jmsOpsContainer1, jmsOpsContainer2);

        //lookup connection factory once, reuse it to create multiple connections on server1
        final Context context = JMSTools.getEAP7Context(container(1).getHostname(), container(1).getJNDIPort(), Constants.JNDI_CONTEXT_TYPE.NORMAL_CONTEXT);
        final ConnectionFactory cf = (ConnectionFactory) context.lookup(CONNECTION_FACTORY_JNDI_EAP7);
        final Queue queue = (Queue) context.lookup(PrepareConstants.QUEUE_JNDI + 0);

        for (int i = 0; i < 100; i++) {
            new Thread() {
                @Override
                public void run() {
                    Connection con = null;
                    Session session = null;
                    try {
                        con = cf.createConnection();
                        session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
                        MessageProducer producer = session.createProducer(queue);
                        Thread.sleep(TimeUnit.SECONDS.toMillis(5));
                    } catch (Exception e) {
                        logger.error(e);
                    } finally {
                        JMSTools.cleanupResources(context, con, session);
                    }
                }
            }.start();
        }

        Thread.sleep(TimeUnit.SECONDS.toMillis(20));

        terminateExecutor();

        jmsOpsContainer1.close();
        jmsOpsContainer2.close();

        logger.info("Number of connections measured on server1 : " + writeValues(server1Connections));
        logger.info("Number of connections measured on server2 : " + writeValues(server2Connections));

        //validate values on server2. There are some connections on server because of cluster.
        //No other should be created. Number should be constant.
        Integer first = null;
        for (Integer i : server1Connections) {
            if (first == null) first = i;
            assertTrue("Number of connections on server2 should be constant", first.equals(i));
            assertTrue("Connections should not be made to server2", i < 10);
        }
    }

    private void setUpExecutor(final JMSOperations jmsOpsContainer1, final JMSOperations jmsOpsContainer2) {
        scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                server1Connections.add(jmsOpsContainer1.countConnections());
                server2Connections.add(jmsOpsContainer2.countConnections());
            }
        }, 2, 2, TimeUnit.SECONDS);
    }

    private void terminateExecutor() {
        try {
            scheduledExecutorService.awaitTermination(15, TimeUnit.SECONDS);
            scheduledExecutorService.shutdown();
        } catch (Exception e) {
            logger.error(e);
            throw new RuntimeException(e);
        }
    }

    private String writeValues(List<Integer> server1Connections) {
        StringBuilder sb = new StringBuilder();
        for (Integer i : server1Connections) {
            sb.append(i).append("; ");
        }
        return sb.toString();
    }

}
