package org.jboss.qa.hornetq.apps.mdb;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A LocalCopyMdbFromQueue used for lodh tests.
 * <p/>
 * This mdb reads messages from queue "InQueue" and sends exact same message to queue "OutQueue".
 *
 * @author <a href="pslavice@jboss.com">Pavel Slavicek</a>
 * @author <a href="mnovak@redhat.com">Miroslav Novak</a>
 * @version $Revision: 1.1 $
 */
@MessageDriven(name = "mdb-copy",
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InQueue")
        })
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
public class LocalCopyMdbFromQueue implements MessageDrivenBean, MessageListener {

    @Resource(mappedName = "java:/JmsXA")
    private  ConnectionFactory cf;

    @Resource(mappedName = "java:/jms/queue/OutQueue")
    private  Queue queue;

    public static AtomicInteger globalCounter = new AtomicInteger();

    //private static final long serialVersionUID =
    private static final Logger log = Logger.getLogger(LocalCopyMdbFromQueue.class.getName());
    private MessageDrivenContext context = null;

    public LocalCopyMdbFromQueue() {
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

        if (!(message instanceof BytesMessage)) {
            log.error("MDB is expecting bytes messages, got " + message.getClass().getName());
            return;
        }
        BytesMessage msg = (BytesMessage) message;

        try {
            long time = System.currentTimeMillis();
            int counter = 0;
            try {
                counter = message.getIntProperty("count");
            } catch (Exception e) {
                log.log(Level.ERROR, e.getMessage(), e);
            }
            String messageInfo = message.getJMSMessageID() + ", count:" + counter;
            log.log(Level.DEBUG, " Start of message: " + globalCounter.incrementAndGet() + ", message info:" + messageInfo);

            con = cf.createConnection();

            con.start();

            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

            MessageProducer sender = session.createProducer(queue);
            sender.send(this.copyBytesMessage(msg, session));

            log.log(Level.DEBUG, " End of " + messageInfo + " in " + (System.currentTimeMillis() - time) + " ms");

        } catch (Exception t) {
            t.printStackTrace();
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


    private Message copyBytesMessage(final BytesMessage original, final Session session) throws JMSException {
        BytesMessage message = session.createBytesMessage();
        byte[] body = new byte[(int) original.getBodyLength()];

        original.readBytes(body);
        message.writeBytes(body, 0, body.length);
        return message;
    }
}