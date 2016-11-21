package org.jboss.qa.hornetq.apps.mdb;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.JMSImplementation;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.*;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A MdbWithRemoteOutQueueToContaniner1 used thread pool testing.
 * <p>
 * This mdb reads messages from queue "InQueue0" and sends to queue "OutQueue0".
 * It can be used with {@link org.jboss.qa.hornetq.apps.Clients}
 *
 * @author <a href="pslavice@jboss.com">Pavel Slavicek</a>
 * @author <a href="mnovak@redhat.com">Miroslav Novak</a>
 * @version $Revision: 1.1 $
 */
@MessageDriven(name = "mdb1",
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                @ActivationConfigProperty(propertyName = "rebalanceConnections", propertyValue = "true"),
                @ActivationConfigProperty(propertyName = "hA", propertyValue = "true"),
                @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "10"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InQueue0")})
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
public class MdbWithRemoteOutQueue0 implements MessageListener {

    private static final long serialVersionUID = 2770941392406343837L;
    private static final Logger log = Logger.getLogger(MdbWithRemoteOutQueue0.class.getName());
    private static final JMSImplementation jmsImplementation = ServiceLoader.load(JMSImplementation.class).iterator().next();
    private Queue queue = null;
    public static AtomicInteger numberOfProcessedMessages = new AtomicInteger();

    @Resource(mappedName = "java:/JmsXA")
    private ConnectionFactory cf;

    @Resource
    private MessageDrivenContext context;

    @Override
    public void onMessage(Message message) {

        Connection con = null;
        Session session = null;

        try {

            long time = System.currentTimeMillis();

            String messageInfo = message.getJMSMessageID();

            log.debug(" Start of message:" + messageInfo);

            for (int i = 0; i < (5 + 5 * Math.random()); i++) {
                try {
                    Thread.sleep((int) (10 + 10 * Math.random()));
                } catch (InterruptedException ex) {
                }
            }

            con = cf.createConnection();

            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

            if (queue == null) {
                queue = session.createQueue("OutQueue0");
            }

            con.start();

            String text = message.getJMSMessageID() + " processed by: " + hashCode();
            MessageProducer sender = session.createProducer(queue);
            TextMessage newMessage = session.createTextMessage(text);
            newMessage.setStringProperty("inMessageId", message.getJMSMessageID());
            newMessage.setStringProperty(jmsImplementation.getDuplicatedHeader(), message.getStringProperty(jmsImplementation.getDuplicatedHeader()));
            sender.send(newMessage);

            messageInfo = messageInfo + ". Sending new message with inMessageId: " + newMessage.getStringProperty("inMessageId")
                    + " and messageId: " + newMessage.getJMSMessageID();

            log.debug("End of " + messageInfo + " in " + (System.currentTimeMillis() - time) + " ms");

            if (numberOfProcessedMessages.incrementAndGet() % 100 == 0)
                log.info(messageInfo + " in " + (System.currentTimeMillis() - time) + " ms");

        } catch (Exception t) {
            log.error(t.getMessage(), t);
            this.context.setRollbackOnly();
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
}