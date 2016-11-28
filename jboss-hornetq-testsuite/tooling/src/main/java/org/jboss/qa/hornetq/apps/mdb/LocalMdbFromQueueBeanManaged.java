package org.jboss.qa.hornetq.apps.mdb;

import org.jboss.logging.Logger;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.*;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A LocalMdbFromQueue used for lodh tests.
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
        })
@TransactionManagement(TransactionManagementType.BEAN)
public class LocalMdbFromQueueBeanManaged implements MessageDrivenBean, MessageListener {

    @Resource(mappedName = "java:/JmsXA")
    private  ConnectionFactory cf;

    @Resource(mappedName = "java:/jms/queue/OutQueue")
    private  Queue queue;

    @Resource
    UserTransaction userTransaction;

    public static AtomicInteger globalCounter = new AtomicInteger();

    private static final long serialVersionUID = 2770941392406343837L;
    private static final Logger log = Logger.getLogger(LocalMdbFromQueueBeanManaged.class.getName());
    private MessageDrivenContext context = null;

    public LocalMdbFromQueueBeanManaged() {
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

            userTransaction.begin();

            String text = message.getJMSMessageID() + " processed by: " + hashCode();
            MessageProducer sender = session.createProducer(queue);
            TextMessage newMessage = session.createTextMessage(text);
            newMessage.setStringProperty("inMessageId", message.getJMSMessageID());
            sender.send(newMessage);
            Thread.sleep(100);

            log.debug("End of message: " + counter + ", message info: " + message.getJMSMessageID() + " in " + (System.currentTimeMillis() - time) + " ms");

            userTransaction.commit();

        } catch (Exception t) {
            log.fatal(t.getMessage(), t);
            try {
                userTransaction.rollback();
            } catch (SystemException e) {
                log.error(e);
            }

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