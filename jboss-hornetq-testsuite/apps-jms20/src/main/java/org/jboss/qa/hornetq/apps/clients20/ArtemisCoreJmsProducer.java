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
public class ArtemisCoreJmsProducer extends Thread {

    private static final Logger logger = Logger.getLogger(ArtemisCoreJmsProducer.class);

    private Connection connection = null;
    private Session session = null;

    private final String hostName;
    private final int httpPort;
    private final int httpsPort;
    private final String queueName;
    private final int messageCount;

    private int counter = 0;
    private boolean stopClient = false;
    private boolean isSslEnabled = false;

    public ArtemisCoreJmsProducer(Container container, String queueName, int messageCount) {
        this(container, queueName, messageCount, false);
    }

    public ArtemisCoreJmsProducer(Container container, String queueName, int messageCount, boolean ssl) {
        this.hostName = container.getHostname();
        this.httpPort = container.getHttpPort();
        this.httpsPort = container.getHttpsPort();
        this.queueName = queueName;
        this.messageCount = messageCount;
        this.isSslEnabled = ssl;
    }

    public ArtemisCoreJmsProducer(String hostname, int httpPort, int httpsPort, String queueName, int messageCount, boolean ssl) {
        this.hostName = hostname;
        this.httpPort = httpPort;
        this.httpsPort = httpsPort;
        this.queueName = queueName;
        this.messageCount = messageCount;
        this.isSslEnabled = ssl;
    }

    public void run() {

        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("host", hostName);
        map.put(TransportConstants.HTTP_UPGRADE_ENABLED_PROP_NAME, true);

        if (isSslEnabled) {
            map.put("port", httpsPort);
            map.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
        } else {
            map.put("port", httpPort);
        }
        

        TransportConfiguration transportConfiguration = new TransportConfiguration(NettyConnectorFactory.class.getName(), map);

        try {
            ConnectionFactory cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, transportConfiguration);
            Queue orderQueue = ActiveMQJMSClient.createQueue(queueName);

            connection = cf.createConnection();

            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            MessageProducer producer = session.createProducer(orderQueue);

            connection.start();

            while (!stopClient && counter < messageCount) {
                TextMessage message = session.createTextMessage("This is an order");
                producer.send(message);
                counter++;
                logger.info("msg sent");
            }

        } catch (JMSException e) {
            logger.error(e);
            e.printStackTrace();
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

    public void stopClient() {
        this.stopClient = true;
    }

}
