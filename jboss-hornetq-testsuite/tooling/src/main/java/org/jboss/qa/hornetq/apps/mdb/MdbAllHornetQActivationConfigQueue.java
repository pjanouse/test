package org.jboss.qa.hornetq.apps.mdb;

import org.jboss.logging.Logger;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A MdbAllHornetQActivationConfigQueue used for lodh tests.
 * <p/>
 * This mdb reads messages from queue "InQueue" and sends to queue "OutQueue". This mdb is used
 * in ClusterTestCase. Don't change it!!!
 *
 * @author <a href="pslavice@jboss.com">Pavel Slavicek</a>
 * @author <a href="mnovak@redhat.com">Miroslav Novak</a>
 * @version $Revision: 1.1 $
 */
@MessageDriven(name = "mdb",
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InQueue"),
                @ActivationConfigProperty(propertyName = "AcknowledgeMode", propertyValue = "Auto-acknowledge"),
                @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "5"),
                @ActivationConfigProperty(propertyName = "MessageSelector", propertyValue = "count=3"),
                @ActivationConfigProperty(propertyName = "TransactionTimeout", propertyValue = "0"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InQueue"),
                @ActivationConfigProperty(propertyName = "UseJNDI", propertyValue = "true")
        })
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
public class MdbAllHornetQActivationConfigQueue implements MessageDrivenBean, MessageListener {

    @Resource(mappedName = "java:/JmsXA")
    private  ConnectionFactory cf;

    @Resource(mappedName = "java:/jms/queue/OutQueue")
    private  Queue queue;

    public static AtomicInteger globalCounter = new AtomicInteger();

//    @Resource(name = "queue/OutQueue")
//    private static Queue queue;

    private static final long serialVersionUID = 2770941392406343837L;
    private static final Logger log = Logger.getLogger(MdbAllHornetQActivationConfigQueue.class.getName());
    private MessageDrivenContext context = null;

    public MdbAllHornetQActivationConfigQueue() {
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
        Session session = null;

        try {
            long time = System.currentTimeMillis();
            int counter = 0;
            try {
                counter = message.getIntProperty("count");
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
            String messageInfo = message.getJMSMessageID() + ", count:" + counter;
            log.info("Start of message: " + globalCounter.incrementAndGet() + ", message info:" + messageInfo);

            con = cf.createConnection();

            con.start();

            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

            String text = message.getJMSMessageID() + " processed by: " + hashCode();
            MessageProducer sender = session.createProducer(queue);
            TextMessage newMessage = session.createTextMessage(text);
            newMessage.setStringProperty("inMessageId", message.getJMSMessageID());
            sender.send(newMessage);

            log.info("End of " + messageInfo + " in " + (System.currentTimeMillis() - time) + " ms");

        } catch (Exception t) {
            t.printStackTrace();
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