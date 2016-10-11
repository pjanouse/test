package org.jboss.qa.hornetq.apps.clients20;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;

import javax.jms.*;
import java.util.HashMap;

/**
 * Created by mstyk on 9/22/16.
 */
public class ArtemisCoreJmsReceiver extends Thread {

    private static final Logger logger = Logger.getLogger(ArtemisCoreJmsReceiver.class);

    private Connection connection = null;
    private Session session = null;

    private final Container container;
    private final String queueName;
    private final int receiveTimeout;

    private int messageCount;

    private boolean isSslEnabled = false;


    private int counter = 0;
    private boolean stopClient = false;

    public ArtemisCoreJmsReceiver(Container container, String queueName, int receiveTimeout) {
        this.container = container;
        this.queueName = queueName;
        this.receiveTimeout = receiveTimeout;
    }

    public ArtemisCoreJmsReceiver(Container container, String queueName, int receiveTimeout, boolean ssl) {
        this.container = container;
        this.queueName = queueName;
        this.receiveTimeout = receiveTimeout;
        this.isSslEnabled = ssl;
    }

    public void run() {

        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("host", container.getHostname());
        if (isSslEnabled) {
            map.put("port", container.getHttpsPort());
            map.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
        } else {
            map.put("port", container.getHornetqPort());
        }
        map.put(TransportConstants.HTTP_UPGRADE_ENABLED_PROP_NAME, true);


        TransportConfiguration transportConfiguration = new TransportConfiguration(NettyConnectorFactory.class.getName(), map);

        try {
            ConnectionFactory cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, transportConfiguration);
            Queue orderQueue = ActiveMQJMSClient.createQueue(queueName);

            connection = cf.createConnection();

            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            MessageConsumer consumer = session.createConsumer(orderQueue);

            connection.start();

            TextMessage receivedMessage = null;
            do {
                receivedMessage = (TextMessage) consumer.receive(receiveTimeout);
                if (receivedMessage != null) {
                    messageCount++;
                    logger.info("msg rcvd");
                }
            } while (receivedMessage != null);

        } catch (JMSException e) {
            logger.error(e);
        } finally {
            if (connection != null) try {
                connection.close();
            } catch (JMSException e) {
                logger.error(e);
            }
            if (session != null) try {
                session.close();
            } catch (JMSException e) {
                logger.error(e);
            }
        }

    }

    public int getReceivedMessageCount() {
        return messageCount;
    }

}
