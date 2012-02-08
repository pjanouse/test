/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.qa.hornetq.apps.clients;

import java.util.HashMap;
import javax.jms.*;
import javax.naming.Context;

import org.apache.log4j.Logger;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.jms.HornetQJMSClient;
import org.hornetq.api.jms.JMSFactoryType;

import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory;
import org.hornetq.jms.client.HornetQConnectionFactory;

/**
 * Simple sender with client acknowledge session. Not able to failover.
 *
 * @author mnovak
 */
public class ProducerClientAckNonHA extends Thread {

    private static final Logger logger = Logger.getLogger(ProducerClientAckNonHA.class);
    private static Context remoteContext;
    String hostname = "localhost";
    String queueName = "java://queue/testQueue";
    int numberOfMessages = 1000;
    long waitAfterMessage = 0;

    public ProducerClientAckNonHA(String queueName) {

        this.queueName = queueName;

    }

    public ProducerClientAckNonHA(String hostname, String queueName) {

        this.hostname = hostname;

        this.queueName = queueName;
    }

    public ProducerClientAckNonHA(String hostname, String queueName, int numberOfMessages, long waitAfterMessage) {

        this.hostname = hostname;

        this.queueName = queueName;

        this.numberOfMessages = numberOfMessages;

        this.waitAfterMessage = waitAfterMessage;

    }

    public void run() {

        Connection conn = null;
        Queue queue = null;
        Session session = null;

        try {
            ///////////////////////////////////////////////////////////////////////
            ///////// FIXME REPLACE BY REMOTE JNDI LOOKUP for connection factory
            ////////////////////////////////////////////////////////////////////////
                HashMap<String, Object> map = new HashMap<String, Object>();
                map.put("host", hostname);
                map.put("port", 5445);

                TransportConfiguration transportConfiguration = new TransportConfiguration(NettyConnectorFactory.class.getName(), map);
                HornetQConnectionFactory cf = HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, transportConfiguration);
                cf.setRetryInterval(1000);
                cf.setRetryIntervalMultiplier(1.0);
                cf.setReconnectAttempts(1);
                cf.setBlockOnDurableSend(true);
                cf.setBlockOnNonDurableSend(true);
                cf.setInitialConnectAttempts(-1);

                logger.info("ha: " + cf.getServerLocator().isHA());
                logger.info("client failure check period is : " + cf.getServerLocator().getClientFailureCheckPeriod());
                logger.info("client ttl is : " + cf.getConnectionTTL());
                logger.info("client reconnect attempts is : " + cf.getReconnectAttempts());
                for (String s : transportConfiguration.getParams().keySet()) {
                    logger.info("property: " + s + "  value: " + transportConfiguration.getParams().get(s));
                }
            logger.info(transportConfiguration);
            //////////////////////////////////////////////////////////////////////////////

//            final Properties env = new Properties();
//            env.put(Context.INITIAL_CONTEXT_FACTORY, InitialContextFactory.class.getName());
//            env.put(Context.PROVIDER_URL, "remote://localhost:4447");
//            env.put("jboss.naming.client.ejb.context", true);
//            try {
//                remoteContext = new InitialContext(env);
//            } catch (NamingException ex) {
//                ex.printStackTrace();
//                java.util.logging.Logger.getLogger(ProducerClientAckNonHA.class.getName()).log(Level.SEVERE, null, ex);
//            }
//
//            ConnectionFactory cf = (ConnectionFactory) remoteContext.lookup("java://RemoteConnectionFactory");

            conn = cf.createConnection();

            conn.start();
            // FIXME - replace by jndi lookup
            queue = HornetQJMSClient.createQueue(queueName);
//            queue = (Queue) remoteContext.lookup("java://"+queueName);

            session = conn.createSession(false, QueueSession.CLIENT_ACKNOWLEDGE);

            MessageProducer sender = session.createProducer(queue);

            for (int i = 0; i < numberOfMessages; i++) {

                Message message = session.createTextMessage("This is content of test message.");

                sender.send(message);

                logger.info("Producer for node: " + hostname + ". Sent message - count: "
                        + i + ", messageId:" + message.getJMSMessageID());
            }

        } catch (JMSException ex) {

            logger.error("Exception was thrown during sending messages:", ex);

        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (JMSException ex) {
                    // ignore
                }
            }
        }
    }
}
