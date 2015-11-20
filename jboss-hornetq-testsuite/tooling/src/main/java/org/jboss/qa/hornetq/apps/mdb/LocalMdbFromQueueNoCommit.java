package org.jboss.qa.hornetq.apps.mdb;

        import org.apache.log4j.Level;
        import org.apache.log4j.Logger;

        import javax.annotation.Resource;
        import javax.ejb.*;
        import javax.jms.*;
        import java.util.concurrent.atomic.AtomicInteger;

/**
 * A LocalMdbFromQueueNoCommit used for lodh tests.
 * <p/>
 * This mdb reads messages from queue "InQueue" and sends to queue "OutQueue". This MDB get stuck for more then 60 min for 1st message.
 *
 * @author <a href="pslavice@jboss.com">Pavel Slavicek</a>
 * @author <a href="mnovak@redhat.com">Miroslav Novak</a>
 * @version $Revision: 1.1 $
 */
@MessageDriven(name = "mdb",
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InQueue"),
        })
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
public class LocalMdbFromQueueNoCommit implements MessageListener {

    @Resource(mappedName = "java:/JmsXA")
    private  ConnectionFactory cf;

    @Resource(mappedName = "java:/jms/queue/OutQueue")
    private  Queue queue;

    public static AtomicInteger globalCounter = new AtomicInteger();

    private static final long serialVersionUID = 2770941392406343837L;

    private static final Logger log = Logger.getLogger(LocalMdbFromQueueNoCommit.class.getName());

    @Override
    public void onMessage(Message message) {

        Connection con = null;

        Session session;

        try {

            long time = System.currentTimeMillis();
            int counter = globalCounter.incrementAndGet();
            log.info("Start of message: " + counter + ", message info:" + message.getJMSMessageID());

            con = cf.createConnection();

            con.start();

            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

            String text = message.getJMSMessageID() + " processed by: " + hashCode();

            MessageProducer sender = session.createProducer(queue);

            TextMessage newMessage = session.createTextMessage(text);

            newMessage.setStringProperty("inMessageId", message.getJMSMessageID());

            sender.send(newMessage);

            // sleep for 1 hour for 1st message
            if (counter <= 1) {
                log.info("Sleep for 60 min.");
                Thread.sleep(60 * 1000 * 60);
            }

            log.info("End of message: " + counter + ", message info: " + message.getJMSMessageID() + " in " + (System.currentTimeMillis() - time) + " ms");

        } catch (Exception t) {

            log.log(Level.FATAL, t.getMessage(), t);

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
}
