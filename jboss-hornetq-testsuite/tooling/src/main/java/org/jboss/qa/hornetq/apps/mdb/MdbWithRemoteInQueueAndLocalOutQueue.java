package org.jboss.qa.hornetq.apps.mdb;

import org.jboss.logging.Logger;

import javax.ejb.*;
import javax.jms.*;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * A MdbWithRemoteInQueueAndLocalOutQueue used for lodh tests. Used in RemoteJcaTestCase.
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
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InQueue")})
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
public class MdbWithRemoteInQueueAndLocalOutQueue implements MessageDrivenBean, MessageListener {

    private static final long serialVersionUID = 2770941392406343837L;
    private static final Logger log = Logger.getLogger(MdbWithRemoteInQueueAndLocalOutQueue.class.getName());
    private MessageDrivenContext context = null;
    private static InitialContext ctx = null;
    private static Queue queue = null;
    private static ConnectionFactory cf = null;

    static {
        try {

            ctx = new InitialContext();
            // i want connection factory configured here
            cf = (ConnectionFactory) ctx.lookup("java:/jmsXALocal");
            queue = (Queue) ctx.lookup("jms/queue/OutQueue");

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public MdbWithRemoteInQueueAndLocalOutQueue() {
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

        if (ctx != null) {
            try {
                ctx.close();
            } catch (NamingException e) {
                log.error(e.getMessage(), e);
            }
        }
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
//            log.log(Level.INFO, " Start of message:" + messageInfo);

            con = cf.createConnection();

            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

            con.start();

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