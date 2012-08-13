package org.jboss.qa.hornetq.apps.perf;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

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
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "InQueue")})
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
public class CounterMdb implements MessageListener {

    // Logger
    private static final Logger log = Logger.getLogger(CounterMdb.class.getName());

    @Resource(mappedName = "java:/JmsXA")
    private ConnectionFactory cf;

    @Resource(mappedName = "InQueue")
    private Queue inQueue;

    @Resource(mappedName = "OutQueue")
    private Queue outQueue;

    @Resource
    private MessageDrivenContext context;

    @Override
    public void onMessage(Message message) {
        Connection con = null;
        Session session = null;
        String textContent = "";
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
            if (counter == null || counter == 0) {
                counter = 0;
            }
            if (index == null || index == 0) {
                index = 0;
            }
            if (maxCycles == null || maxCycles == 0) {
                maxCycles = 1000;
            }

            // Get content of the original message
            if (message instanceof TextMessage) {
                textContent = ((TextMessage) message).getText();
            }

            // Send message into the inQueue or outQueue
            con = cf.createConnection();
            con.start();
            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
            if (counter < maxCycles) {
                MessageProducer sender = session.createProducer(inQueue);
                TextMessage newMessage = session.createTextMessage();
                newMessage.setText(textContent);
                newMessage.setLongProperty(PerformanceConstants.MESSAGE_PARAM_CREATED, created);
                newMessage.setIntProperty(PerformanceConstants.MESSAGE_PARAM_COUNTER, counter + 1);
                newMessage.setIntProperty(PerformanceConstants.MESSAGE_PARAM_INDEX, index);
                newMessage.setIntProperty(PerformanceConstants.MESSAGE_PARAM_CYCLES, maxCycles);
                sender.send(newMessage);
            } else {
                MessageProducer sender = session.createProducer(outQueue);
                TextMessage newMessage = session.createTextMessage();
                newMessage.setLongProperty(PerformanceConstants.MESSAGE_PARAM_CREATED, created);
                newMessage.setLongProperty(PerformanceConstants.MESSAGE_PARAM_FINISHED, System.nanoTime());
                newMessage.setIntProperty(PerformanceConstants.MESSAGE_PARAM_COUNTER, counter);
                newMessage.setIntProperty(PerformanceConstants.MESSAGE_PARAM_INDEX, index);
                newMessage.setIntProperty(PerformanceConstants.MESSAGE_PARAM_CYCLES, maxCycles);
                sender.send(newMessage);
            }
        } catch (Exception t) {
            log.log(Level.FATAL, t.getMessage(), t);
            this.context.setRollbackOnly();

        } finally {
            if (session != null) {
                try {
                    session.close();
                } catch (JMSException e) {
                    log.log(Level.FATAL, e.getMessage(), e);
                }
            }
            if (con != null) {
                try {
                    con.close();
                } catch (JMSException e) {
                    log.log(Level.FATAL, e.getMessage(), e);
                }
            }
        }
    }
}