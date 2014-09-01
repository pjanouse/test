package org.jboss.qa.hornetq.test.failover;
//todo add to test plan to mojo

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.apps.clients.XAConsumerTransAck;
import org.jboss.qa.hornetq.apps.impl.ClientMixMessageBuilder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mnovak on 2/24/14.
 */
public class XAFailoverTestCase extends DedicatedFailoverTestCase {


    private static final Logger logger = Logger.getLogger(XAFailoverTestCase.class);

    @Test
    public void testFailover() throws Exception {
        boolean shutdown = false;

        prepareSimpleDedicatedTopology();

        controller.start(CONTAINER1);

        controller.start(CONTAINER2);

        Thread.sleep(10000);

        ProducerTransAck p = new ProducerTransAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1),queueJndiNamePrefix+"0", NUMBER_OF_MESSAGES_PER_PRODUCER);
        p.setMessageBuilder(new ClientMixMessageBuilder(10,110));
        p.setCommitAfter(100);
        p.start();
        List<Client> listWithProucer = new ArrayList<Client>();
        listWithProucer.add(p);

        XAConsumerTransAck c = new XAConsumerTransAck(getHostname(CONTAINER1), getJNDIPort(CONTAINER1), queueJndiNamePrefix+"0");
        c.setCommitAfter(10);
        c.start();

        waitForProducersUntil(listWithProucer, 500, 60000);

        if (!shutdown) {
            logger.warn("########################################");
            logger.warn("Kill live server");
            logger.warn("########################################");
            killServer(CONTAINER1);
            controller.kill(CONTAINER1);
        } else {
            logger.warn("########################################");
            logger.warn("Shutdown live server");
            logger.warn("########################################");
            stopServer(CONTAINER1);
        }

        logger.warn("Wait some time to give chance backup to come alive and clients to failover");
        Assert.assertTrue("Backup did not start after failover - failover failed.", waitHornetQToAlive(getHostname(CONTAINER2), getHornetqPort(CONTAINER2), 300000));

        Thread.sleep(10000);
        p.stopSending();
        c.join();

        stopServer(CONTAINER1);

        stopServer(CONTAINER2);
    }



}
