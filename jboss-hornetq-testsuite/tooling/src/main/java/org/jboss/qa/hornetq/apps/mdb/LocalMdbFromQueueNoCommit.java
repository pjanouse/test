package org.jboss.qa.hornetq.apps.mdb;

import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
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
                @ActivationConfigProperty(propertyName = "rebalanceConnections", propertyValue = "true"),
                @ActivationConfigProperty(propertyName = "hA", propertyValue = "true"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InQueue"),
        })
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
public class LocalMdbFromQueueNoCommit implements MessageListener {

    @Resource(mappedName = "java:/JmsXA")
    private ConnectionFactory cf;

    Connection con = null;
    Session session;
    MessageProducer sender;

    @Resource(mappedName = "java:/jms/queue/OutQueue")
    private Queue queue;

    public static AtomicInteger globalCounter = new AtomicInteger();

    private static final long serialVersionUID = 2770941392406343837L;

    private static final Logger log = Logger.getLogger(LocalMdbFromQueueNoCommit.class.getName());

    @Override
    public void onMessage(Message message) {

        try {

            long time = System.currentTimeMillis();
            int counter = globalCounter.incrementAndGet();
            log.info("Start of message: " + counter + ", message info:" + message.getJMSMessageID());

            String text = message.getJMSMessageID() + " processed by: " + hashCode();

            TextMessage newMessage = session.createTextMessage(text);

            newMessage.setStringProperty("inMessageId", message.getJMSMessageID());

            sender.send(newMessage);

            // sleep for 1 hoour
            if (counter <= 1) {
                log.info("Sleep for 60 min.");
                Thread.sleep(60 * 1000 * 60);
            }

            log.info("End of message: " +  counter + ", message info: " + message.getJMSMessageID() + " in " + (System.currentTimeMillis() - time) + " ms");

        } catch (Exception t) {
            log.fatal(t.getMessage(), t);
            throw new RuntimeException(t);
        }
    }

    @PostConstruct
    public void init() {
        try {
            con = cf.createConnection();
            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
            sender = session.createProducer(queue);
        } catch (Exception ex) {
            log.error(ex);
        }
    }
}
