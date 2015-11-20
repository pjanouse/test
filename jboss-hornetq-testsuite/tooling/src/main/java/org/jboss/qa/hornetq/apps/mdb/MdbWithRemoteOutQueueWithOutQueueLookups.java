package org.jboss.qa.hornetq.apps.mdb;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.impl.MessageUtils;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A MdbWithRemoteOutQueueWithOutQueueLookups used for lodh tests. Used in RemoteJcaTestCase.
 * <p>
 * This MDB expects JNDI params for OutQueue in every message:
 * <p>
 * This mdb reads messages from outQueue "InQueue" and sends to outQueue "OutQueue".
 *
 * @author <a href="pslavice@jboss.com">Pavel Slavicek</a>
 * @author <a href="mnovak@redhat.com">Miroslav Novak</a>
 * @version $Revision: 1.1 $
 */
@MessageDriven(name = "mdb1",
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InQueue")})
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
public class MdbWithRemoteOutQueueWithOutQueueLookups implements MessageListener {

    public static AtomicInteger numberOfProcessedMessages = new AtomicInteger();
    private static final long serialVersionUID = 2770941392406343837L;
    private static final Logger log = Logger.getLogger(MdbWithRemoteOutQueueWithOutQueueLookups.class.getName());

    private String outQueueJndiName = "jms/queue/OutQueue";

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

            con = cf.createConnection();

            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

            for (int i = 0; i < (5 + 5 * Math.random()); i++) {
                try {
                    Thread.sleep((int) (10 + 10 * Math.random()));
                } catch (InterruptedException ex) {
                }
            }
            Queue outQueue = makeLookup(outQueueJndiName, message);

            con.start();

            String text = message.getJMSMessageID() + " processed by: " + hashCode();
            MessageProducer sender = session.createProducer(outQueue);
            TextMessage newMessage = session.createTextMessage(text);
            newMessage.setStringProperty("inMessageId", message.getJMSMessageID());
            newMessage.setStringProperty("_HQ_DUPL_ID", message.getStringProperty("_HQ_DUPL_ID"));
            sender.send(newMessage);

            messageInfo = messageInfo + ". Sending new message with inMessageId: " + newMessage.getStringProperty("inMessageId")
                    + " and messageId: " + newMessage.getJMSMessageID();

            log.debug("End of " + messageInfo + " in " + (System.currentTimeMillis() - time) + " ms");

            if (numberOfProcessedMessages.incrementAndGet() % 100 == 0)
                log.info(messageInfo + " in " + (System.currentTimeMillis() - time) + " ms");

        } catch (Exception t) {
            log.error(t.getMessage(), t);
            throw new RuntimeException(t);
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

    private Queue makeLookup(String outQueueJndiName, Message inMessage) throws Exception {

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