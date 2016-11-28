package org.jboss.qa.hornetq.apps.mdb;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.apps.JMSImplementation;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.*;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A LocalSlowMdbFromTopic used for Slow consumers tests.
 * <p>
 * This mdb reads messages from topic "InTopic" and sends to queue "OutQueue". There is delay 1s in processing messages
 *
 * @author <a href="pslavice@jboss.com">Pavel Slavicek</a>
 * @author <a href="mnovak@redhat.com">Miroslav Novak</a>
 * @author <a href="mstyk@redhat.com">Martin Styk</a>
 * @version $Revision: 1.1 $
 */
@MessageDriven(name = "mdb",
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
                @ActivationConfigProperty(propertyName = "rebalanceConnections", propertyValue = "true"),
                @ActivationConfigProperty(propertyName = "hA", propertyValue = "true"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InTopic"),
        })
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
public class LocalSlowMdbFromTopic implements MessageDrivenBean, MessageListener {

    private static final JMSImplementation jmsImplementation = ServiceLoader.load(JMSImplementation.class).iterator().next();

    @Resource(mappedName = "java:/JmsXA")
    private ConnectionFactory cf;

    @Resource(mappedName = "java:/jms/queue/OutQueue")
    private Queue queue;

    public static AtomicInteger globalCounter = new AtomicInteger();

    private static final long serialVersionUID = 2770941392406343837L;

    private static final Logger log = Logger.getLogger(LocalSlowMdbFromTopic.class.getName());

    private MessageDrivenContext context = null;

    public LocalSlowMdbFromTopic() {
        super();
    }

    @Override
    public void setMessageDrivenContext(MessageDrivenContext ctx) {
        this.context = ctx;
    }

    public void ejbCreate() {
    }

    @Override
    public void ejbRemove() {
    }

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
            newMessage.setStringProperty(jmsImplementation.getDuplicatedHeader(), message.getStringProperty(jmsImplementation.getDuplicatedHeader()));

            sender.send(newMessage);

            Thread.sleep(1000);

            log.info("End of message: " + counter + ", message info: " + message.getJMSMessageID() + " in " + (System.currentTimeMillis() - time) + " ms");

        } catch (Exception t) {

            log.fatal(t.getMessage(), t);

            context.setRollbackOnly();

        } finally {

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