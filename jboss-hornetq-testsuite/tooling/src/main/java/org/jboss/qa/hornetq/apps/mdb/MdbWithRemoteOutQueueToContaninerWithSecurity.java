package org.jboss.qa.hornetq.apps.mdb;

        import org.apache.log4j.Level;
        import org.apache.log4j.Logger;

        import javax.annotation.Resource;
        import javax.ejb.*;
        import javax.jms.*;
        import java.util.concurrent.atomic.AtomicInteger;

/**
 * A MdbWithRemoteOutQueueToContaninerWithSecurity used for lodh tests. Used in RemoteJcaTestCase.
 * <p/>
 * This mdb reads messages from queue "InQueue" and sends to queue "OutQueue".
 *
 * @author <a href="pslavice@jboss.com">Pavel Slavicek</a>
 * @author <a href="mnovak@redhat.com">Miroslav Novak</a>
 * @version $Revision: 1.1 $
 */
@MessageDriven(name = "mdb1",
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "60"),
                @ActivationConfigProperty(propertyName = "user", propertyValue = "user"),
                @ActivationConfigProperty(propertyName = "password", propertyValue = "useruser"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InQueue")})
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
public class    MdbWithRemoteOutQueueToContaninerWithSecurity implements MessageListener {

    private static final long serialVersionUID = 2770941392406343837L;
    private static final Logger log = Logger.getLogger(MdbWithRemoteOutQueueToContaninerWithSecurity.class.getName());
    private Queue queue = null;
    public static AtomicInteger numberOfProcessedMessages= new AtomicInteger();

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

            // use different user for each connection
            if (counter % 3 == 0) {
                con = cf.createConnection();
            } else if (counter % 3 == 1) {
                con = cf.createConnection("user", "useruser");
            } else {
                con = cf.createConnection("admin", "adminadmin");
            }

            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

            if (queue == null)  {
                queue = session.createQueue("OutQueue");
            }

            con.start();

            String text = message.getJMSMessageID() + " processed by: " + hashCode();
            MessageProducer sender = session.createProducer(queue);
            TextMessage newMessage = session.createTextMessage(text);
            newMessage.setStringProperty("inMessageId", message.getJMSMessageID());
            newMessage.setStringProperty("_HQ_DUPL_ID", message.getStringProperty("_HQ_DUPL_ID"));
            sender.send(newMessage);

            messageInfo = messageInfo + ". Sending new message with inMessageId: " + newMessage.getStringProperty("inMessageId")
                    + " and messageId: " + newMessage.getJMSMessageID();

            log.info("End of " + messageInfo + " in " + (System.currentTimeMillis() - time) + " ms");

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