package org.jboss.qa.hornetq.apps.mdb;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.resource.AdministeredObjectDefinition;
import javax.resource.ConnectionFactoryDefinition;
import org.jboss.ejb3.annotation.ResourceAdapter;

/**
 * A LocalMdbFromQueueAnnotated used for lodh tests.
 *
 * This mdb reads messages from queue "InQueue" and sends to queue "OutQueue"
 * which it creates. This mdb is used in AnnotationsTestCase. Don't change it!!!
 *
 * @author mstyk
 */
/*
@ConnectionFactoryDefinition(
        name="java:jboss/exported/jms/MdbConnectionFactory",
        interfaceName="javax.jms.ConnectionFactory",
        resourceAdapter="activemq-ra.rar")
*/

@JMSConnectionFactoryDefinition(
        name="java:jboss/exported/jms/MdbConnectionFactory",
        resourceAdapter="activemq-ra.rar")

@JMSDestinationDefinition(name = "java:jboss/exported/jms/queue/OutQueue",
        destinationName = "OutQueue",
        interfaceName = "javax.jms.Queue")

@MessageDriven(name = "mdb",
        activationConfig = {
            @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
            @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InQueue"),})
@ResourceAdapter("activemq-ra.rar")
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
public class LocalMdbFromQueueAnnotated implements MessageDrivenBean, MessageListener {

    @Resource(mappedName = "java:jboss/exported/jms/MdbConnectionFactory")
    private ConnectionFactory cf;

    @Resource(mappedName = "java:jboss/exported/jms/queue/OutQueue")
    private Queue queue;

    public static AtomicInteger globalCounter = new AtomicInteger();

    private static final long serialVersionUID = 2770941392406343837L;

    private static final Logger log = Logger.getLogger(LocalMdbFromQueueAnnotated.class.getName());

    private MessageDrivenContext context = null;

    public LocalMdbFromQueueAnnotated() {
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

            sender.send(newMessage);

            Thread.sleep(100);

            log.info("End of message: " + counter + ", message info: " + message.getJMSMessageID() + " in " + (System.currentTimeMillis() - time) + " ms");

        } catch (Exception t) {

            log.log(Level.FATAL, t.getMessage(), t);

            context.setRollbackOnly();

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
