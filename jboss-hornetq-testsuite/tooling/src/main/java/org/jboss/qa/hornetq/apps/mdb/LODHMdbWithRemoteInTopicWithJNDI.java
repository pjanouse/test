package org.jboss.qa.hornetq.apps.mdb;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.impl.MessageUtils;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This mdb expects mdb.properties in jar file which can be loaded during runtime(deployment).
 * A LODHMdbWithRemoteInTopicWithJNDI used for example lodh tests. Used in RemoteJcaWithRecoverTestCase in interop test suite.
 * <p>
 * This mdb reads messages from queue "InTopic" and sends to queue "OutQueue". Yes, really OutQueue.
 *
 * @author <a href="mnovak@redhat.com">Miroslav Novak</a>
 * @version $Revision: 1.1 $
 */
@MessageDriven(name = "LODHMdbWithRemoteInTopicWithJNDI",
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/topic/InTopic"),
                @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = "mySubscription"),
                @ActivationConfigProperty(propertyName = "clientID", propertyValue = "myClientId"),
                @ActivationConfigProperty(propertyName = "rebalanceConnections", propertyValue = "true"),
                @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable")
        })
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
public class LODHMdbWithRemoteInTopicWithJNDI implements MessageListener {

    private static final long serialVersionUID = 2770941392406343837L;
    private static final Logger log = Logger.getLogger(LODHMdbWithRemoteInTopicWithJNDI.class.getName());
    private String outQueueName = "OutQueue";
    private String outQueueJndiName = "jms/queue/" + outQueueName;
    public static AtomicInteger numberOfProcessedMessages = new AtomicInteger();

    @Resource(mappedName = "java:/JmsXA")
    private ConnectionFactory cf;

    @Resource
    private MessageDrivenContext context;

    @Override
    public void onMessage(Message message) {

        Connection con = null;
        Session session;

        try {

            long time = System.currentTimeMillis();
            int counter = 0;
            try {
                counter = message.getIntProperty("count");
            } catch (Exception e) {
                log.log(Level.ERROR, e.getMessage(), e);
            }

            String messageInfo = message.getJMSMessageID() + ", count:" + counter;

            log.debug(" Start of message:" + messageInfo);

            con = cf.createConnection("user", "pass");

            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

            con.start();

            String text = message.getJMSMessageID() + " processed by: " + hashCode();

            Queue outQueue;
            if (MessageUtils.getPropertiesFromMessage(message).get(Context.PROVIDER_URL) != null) {
                outQueue = makeLookup(outQueueJndiName, message);
            } else {
                outQueue = session.createQueue(outQueueName);
            }

            MessageProducer sender = session.createProducer(outQueue);
            TextMessage newMessage = session.createTextMessage(text);
            newMessage.setStringProperty("inMessageId", message.getJMSMessageID());
            sender.send(newMessage);

            messageInfo = messageInfo + ". Sending new message with inMessageId: " + newMessage.getStringProperty("inMessageId")
                    + " and messageId: " + newMessage.getJMSMessageID();

            log.debug("End of " + messageInfo + " in " + (System.currentTimeMillis() - time) + " ms");

            if (numberOfProcessedMessages.incrementAndGet() % 100 == 0)
                log.info(messageInfo + " in " + (System.currentTimeMillis() - time) + " ms");

        } catch (JMSException t) {
            log.error(t.getMessage(), t);
            throw new RuntimeException(t);
        } catch (NamingException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            if (con != null) {
                try {
                    con.close();
                } catch (JMSException e) {
                    log.log(Level.FATAL, e.getMessage(), e);
                }
            }
        }
    }

    private Queue makeLookup(String outQueueJndiName, Message inMessage) throws JMSException, NamingException {

        Map<String, String> messageProperties = MessageUtils.getPropertiesFromMessage(inMessage);

        Properties props = new Properties();
        // there are hard ways to configure different jndi properites in MDB for EAP 6/7, this appears to be the the best way
        props.put(Context.PROVIDER_URL, messageProperties.get(Context.PROVIDER_URL));
        props.put(Context.INITIAL_CONTEXT_FACTORY, messageProperties.get(Context.INITIAL_CONTEXT_FACTORY));
        InitialContext remoteContext = new InitialContext(props);
        Queue queue = null;
        try {
            queue = (Queue) remoteContext.lookup(outQueueJndiName);
        } finally {
            remoteContext.close();
        }
        return queue;
    }


}
