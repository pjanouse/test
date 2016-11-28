package org.jboss.qa.hornetq.apps.perf;

import org.jboss.logging.Logger;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.*;

/**
 * CounterMdb
 * <p/>
 * MDB consumes messages and sends them back into the input queue.
 * Each message is iterated by several times and MDB measures
 * time which is required for whole process with the one message
 *
 * @author <a href="pslavice@jboss.com">Pavel Slavicek</a>
 * @version $Revision: 1.1 $
 */
@MessageDriven(name = "mdb",
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "java:/jms/queue/InQueue")})
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
public class CounterMdb implements MessageListener {
  //  static long ancounter = 0;
    // Logger
    private static final Logger log = Logger.getLogger(CounterMdb.class.getName());

    @Resource(mappedName = "java:/JmsXA")
    private ConnectionFactory cf;

    @Resource(mappedName = "java:/jms/queue/InQueue")
    private Queue inQueue;

    @Resource(mappedName = "java:/jms/queue/OutQueue")
    private Queue outQueue;

    @Resource
    private MessageDrivenContext context;

    @Override
    public void onMessage(Message message) {
        Connection con = null;
        Session session = null;
        try {
            // Get parameters from original message
            Long created = 0L;
            try {
                created = message.getLongProperty(PerformanceConstants.MESSAGE_PARAM_CREATED);
            } catch (NumberFormatException e) {
                // Ignore
            }
            Integer counter = 0;
            try {
                counter = message.getIntProperty(PerformanceConstants.MESSAGE_PARAM_COUNTER);
            } catch (NumberFormatException e) {
                // Ignore
            }
            Integer index = 0;
            try {
                index = message.getIntProperty(PerformanceConstants.MESSAGE_PARAM_INDEX);
            } catch (Exception e) {
                // Ignore it
            }
            Integer maxCycles = 0;
            try {
                maxCycles = message.getIntProperty(PerformanceConstants.MESSAGE_PARAM_CYCLES);
            } catch (Exception e) {
                // Ignore it
            }
            if (created == null || created == 0) {
                created = System.nanoTime();
            }
            if (counter == null) {
                counter = 0;
            }
            if (index == null) {
                index = 0;
            }
            if (maxCycles == null || maxCycles == 0) {
                maxCycles = 1000;
            }

            // Send message into the inQueue or outQueue
            con = cf.createConnection();
            con.start();
            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
            if (counter % 100 == 0) {
                log.info(String.format("Message '%s' has reached %s cycle", message.getJMSMessageID(), counter));
            }
            if (counter < maxCycles) {
                MessageProducer sender = session.createProducer(inQueue);
                Message newMessage = null;
                if (message instanceof TextMessage) {
                    newMessage = session.createTextMessage();
                    ((TextMessage) newMessage).setText(((TextMessage) message).getText());
                } else if (message instanceof BytesMessage) {
                    newMessage = session.createBytesMessage();
                    BytesMessage originalMessage = (BytesMessage) message;
                    byte[] content = new byte[(int) originalMessage.getBodyLength()];
                    originalMessage.readBytes(content);
                    ((BytesMessage) newMessage).writeBytes(content);
                }
                if (newMessage == null) {
                    log.fatal("Unknown message type " + message);
                } else {
                    newMessage.setLongProperty(PerformanceConstants.MESSAGE_PARAM_CREATED, created);
                    newMessage.setIntProperty(PerformanceConstants.MESSAGE_PARAM_COUNTER, counter + 1);
                    newMessage.setIntProperty(PerformanceConstants.MESSAGE_PARAM_INDEX, index);
                    newMessage.setIntProperty(PerformanceConstants.MESSAGE_PARAM_CYCLES, maxCycles);
                    sender.send(newMessage);
                }
            } else {
                long msgLength = 0;
                if (message instanceof TextMessage) {
                    TextMessage originalMessage = (TextMessage) message;
                    msgLength = (originalMessage.getText() != null) ? originalMessage.getText().length() : 0;
                } else if (message instanceof BytesMessage) {
                    BytesMessage originalMessage = (BytesMessage) message;
                    msgLength = originalMessage.getBodyLength();
                }
                MessageProducer sender = session.createProducer(outQueue);
                TextMessage newMessage = session.createTextMessage();
                newMessage.setLongProperty(PerformanceConstants.MESSAGE_PARAM_CREATED, created);
                newMessage.setLongProperty(PerformanceConstants.MESSAGE_PARAM_FINISHED, System.nanoTime());
                newMessage.setIntProperty(PerformanceConstants.MESSAGE_PARAM_COUNTER, counter);
                newMessage.setIntProperty(PerformanceConstants.MESSAGE_PARAM_INDEX, index);
                newMessage.setIntProperty(PerformanceConstants.MESSAGE_PARAM_CYCLES, maxCycles);
                newMessage.setStringProperty(PerformanceConstants.MESSAGE_TYPE, message.getClass().getName());
                newMessage.setLongProperty(PerformanceConstants.MESSAGE_LENGTH, msgLength);
                sender.send(newMessage);
            }
        } catch (Exception t) {
            log.fatal(t.getMessage(), t);
            this.context.setRollbackOnly();
        } finally {
            if (session != null) {
                try {
                    session.close();
                } catch (JMSException e) {
                    log.fatal(e.getMessage(), e);
                }
            }
            if (con != null) {
                try {
                    con.close();
                } catch (JMSException e) {
                    log.fatal(e.getMessage(), e);
                }
            }
        }
    }
}