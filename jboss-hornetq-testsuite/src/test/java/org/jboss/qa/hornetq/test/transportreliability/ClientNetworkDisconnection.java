package org.jboss.qa.hornetq.test.transportreliability;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.clients.ProducerAutoAck;
import org.jboss.qa.hornetq.apps.clients.ReceiverClientAck;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * This test case can be run with IPv6 - just replace those environment variables for ipv6 ones:
 * export MYTESTIP_1=$MYTESTIPV6_1
 * export MYTESTIP_2=$MYTESTIPV6_2
 * export MCAST_ADDR=$MCAST_ADDRIPV6
 * <p/>
 * This test also serves
 *
 * @author mnovak@redhat.com
 */
@RunWith(Arquillian.class)
public class ClientNetworkDisconnection extends HornetQTestCase {

    private static final Logger log = Logger.getLogger(ClientNetworkDisconnection.class);

    private static final int NUMBER_OF_DESTINATIONS = 1;
    // this is just maximum limit for producer
    private static final int NUMBER_OF_MESSAGES_PER_PRODUCER = 500;
    private static final int NUMBER_OF_PRODUCERS_PER_DESTINATION = 10;
    private static final int NUMBER_OF_RECEIVERS_PER_DESTINATION = 1;


    private static boolean topologyCreated = false;


    String queueNamePrefix = "testQueue";
    String topicNamePrefix = "testTopic";
    String queueJndiNamePrefix = "jms/queue/testQueue";
    String topicJndiNamePrefix = "jms/topic/testTopic";

    /**
     */
    @Test
    @RunAsClient
    public void trySendDuringClosing() throws Exception {

        controller.start(CONTAINER1);

        ReceiverClientAck r = new ReceiverClientAck(CONTAINER1_IP, PORT_JNDI_EAP6, queueJndiNamePrefix, 300000, 10, 0);
        r.start();
        int j = 0;
        while (j < 100) {
            List<ProducerAutoAck> producers = new ArrayList<ProducerAutoAck>();
            for (int i = 0; i < 5; i++) {
                ProducerAutoAck p = new ProducerAutoAck(CONTAINER1_IP, PORT_JNDI_EAP6, queueJndiNamePrefix, 500000);
                producers.add(p);
                log.info("Start producer: " + i);
                p.start();
            }

            Thread.sleep(20000);

            for (int i = 0; i < producers.size(); i++) {
                if (producers.get(i).getListOfSentMessages().size() < 2)    {
                    log.error("There is stuck producer.");
                    StackTraceElement[] stack = producers.get(i).getStackTrace();
                    for (StackTraceElement e : stack)   {
                        log.error("Print stack: " + e.toString());
                    }
                } else {
                    producers.get(i).interrupt();
                    producers.remove(i);
                }
                log.info("Stop producer: " + i);
            }
            Thread.sleep(5000);
            j++;
        }


        stopServer(CONTAINER1);

    }


    @Before
    public void prepareServers() {
        if (!topologyCreated) {
            prepareServer(CONTAINER1);
            topologyCreated = true;
        }
    }

    /**
     * Deploys destinations to server which is currently running.
     */
    private void deployDestinations(String containerName) {
        deployDestinations(containerName, "default");
    }

    /**
     * Deploys destinations to server which is currently running.
     *
     * @param serverName server name of the hornetq server
     */
    private void deployDestinations(String containerName, String serverName) {

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);
        jmsAdminOperations.createQueue(serverName, queueNamePrefix, queueJndiNamePrefix, true);
        jmsAdminOperations.createTopic(serverName, topicNamePrefix, topicJndiNamePrefix);
        jmsAdminOperations.close();
    }

    /**
     * Prepares server for topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     */
    private void prepareServer(String containerName) {

        String discoveryGroupName = "dg-group1";
        String broadCastGroupName = "bg-group1";
        String clusterGroupName = "my-cluster";
        String connectorName = "netty";
        String connectionFactoryName = "RemoteConnectionFactory";
        String messagingGroupSocketBindingName = "messaging-group";

        controller.start(containerName);

        JMSOperations jmsAdminOperations = this.getJMSOperations(containerName);
        jmsAdminOperations.setClustered(false);

        jmsAdminOperations.setPersistenceEnabled(true);

        jmsAdminOperations.setReconnectAttemptsForConnectionFactory(connectionFactoryName, 0);
        jmsAdminOperations.disableSecurity();

        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("#", "BLOCK", 10 * 1024 * 1024, 0, 0, 1024 * 1024);
        jmsAdminOperations.setConnectionTtlOverride("default", 5000);
        jmsAdminOperations.close();
        deployDestinations(CONTAINER1);
        controller.stop(containerName);

    }

    @Before
    @After
    public void stopAllServers() {

        stopServer(CONTAINER1);

        stopServer(CONTAINER2);

    }

}