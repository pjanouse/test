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
 * A MdbWithRemoteOutQueueToContaniner2 used for lodh tests. Used in RemoteJcaTestCase.
 * <p/>
 * This mdb reads messages from queue "InQueue" and sends to queue "OutQueue".
 *
 * @author <a href="pslavice@jboss.com">Pavel Slavicek</a>
 * @author <a href="mnovak@redhat.com">Miroslav Novak</a>
 * @version $Revision: 1.1 $
 */
@MessageDriven(name = "mdb2",
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                @ActivationConfigProperty(propertyName = "rebalanceConnections", propertyValue = "true"),
                @ActivationConfigProperty(propertyName = "hA", propertyValue = "true"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InQueue")})
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
public class MdbWithRemoteOutQueueToContaniner2 implements MessageListener {

    private static final long serialVersionUID = 2770941392406343837L;
    private static final Logger log = Logger.getLogger(MdbWithRemoteOutQueueToContaniner2.class.getName());
    private static final JMSImplementation jmsImplementation = ServiceLoader.load(JMSImplementation.class).iterator().next();
    public static AtomicInteger numberOfProcessedMessages= new AtomicInteger();
    private Queue queue = null;

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
            int counter = 0;
            try {
                counter = message.getIntProperty("count");
            } catch (Exception e) {
                log.log(Level.ERROR, e.getMessage(), e);
            }

            String messageInfo = message.getJMSMessageID() + ", count:" + counter;

            log.debug(" Start of message:" + messageInfo);

            for (int i = 0; i < (5 + 5 * Math.random()); i++) {
                try {
                    Thread.sleep((int) (10 + 10 * Math.random()));
                } catch (InterruptedException ex) {
                }
            }

            con = cf.createConnection();

            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

            if (queue == null)  {
                queue = session.createQueue("OutQueue");
            }

            con.start();

            String text = message.getJMSMessageID() + " processed by: " + hashCode();
            MessageProducer sender = session.createProducer(queue);
            TextMessage newMessage = session.createTextMessage(text);
            newMessage.setStringProperty("inMessageId", message.getJMSMessageID());
            newMessage.setStringProperty(jmsImplementation.getDuplicatedHeader(), message.getStringProperty(jmsImplementation.getDuplicatedHeader()));
            sender.send(newMessage);

            log.debug(" End of " + messageInfo + " in " + (System.currentTimeMillis() - time) + " ms");
            if (numberOfProcessedMessages.incrementAndGet() % 100 == 0) log.info(messageInfo + " in " + (System.currentTimeMillis() - time) + " ms");
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