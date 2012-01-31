package org.jboss.hornetq.apps.clients;

import java.util.HashMap;
import javax.jms.*;
import org.apache.log4j.Logger;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.jms.HornetQJMSClient;
import org.hornetq.api.jms.JMSFactoryType;
import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory;
import org.hornetq.jms.client.HornetQConnectionFactory;


/**
 * Simple receiver with client acknowledge session. Not able to failover.
 *
 * @author mnovak
 */
public class ReceiverClientAckNonHa extends Thread {
        
        private static final Logger logger = Logger.getLogger(ProducerClientAckNonHA.class);
        
        String hostname = "localhost";
        String queueName = "testQueue";
        long waitAfterReceiveMessage = 0;
        long receiveTimeOut = 20000;
        // after how many messages will be sent ack
        int ackAfter = 1;
         
        public ReceiverClientAckNonHa(String queueName) {
            
            this.queueName = queueName;
            
        }
        
        public ReceiverClientAckNonHa(String hostname, String queueName) {
            
            this.hostname = hostname;
            
            this.queueName = queueName;
        }
        
        public ReceiverClientAckNonHa(String hostname, String queueName, long waitAfterMessage,
                long receiveTimeOut) {
            
            this.hostname = hostname;
            
            this.queueName = queueName;
            
            this.waitAfterReceiveMessage = waitAfterMessage;
            
            this.receiveTimeOut = receiveTimeOut;
            
        }
        
        @Override
        public void run() {
            
            QueueConnection conn = null;
            Queue queue = null;
            QueueSession session = null;

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

                conn = cf.createQueueConnection();

                conn.start();
                // FIXME - replace by jndi lookup
                queue = (Queue) HornetQJMSClient.createQueue(queueName);

                session = conn.createQueueSession(false, QueueSession.CLIENT_ACKNOWLEDGE);

                QueueReceiver receiver = session.createReceiver(queue);
                
                Message message = null;
                Message lastMessage = null;
                int count = 0;
                
                while ((message = receiver.receive(receiveTimeOut)) != null)    {
                    
                    count++;
                    
                    if (count % ackAfter == 0)  {
                        
                        message.acknowledge();
                        
                        logger.info("Receiver for node: " + hostname + ". Received message - count: "
                                + count + ", messageId:" + message.getJMSMessageID() + " SENT ACKNOWLEDGE");
                        
                    } else {
                        
                        logger.info("Receiver for node: " + hostname + ". Received message - count: "
                                + count + ", messageId:" + message.getJMSMessageID());
                        
                    }
                    
                    lastMessage = message;
                    
                }
                
                logger.info("Receiver for node: " + hostname + ". Received NULL - number of received messages: "
                                + count);

            } catch (JMSException ex) {
                
                logger.error("Exception was thrown during receiving messages:", ex);
                
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

