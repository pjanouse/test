package org.jboss.qa.hornetq.apps.mdb;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.apps.ejb.SenderEJB;

import javax.ejb.*;
import javax.jms.Message;
import javax.jms.MessageListener;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This mdb expects mdb.properties in jar file which can be loaded during runtime(deployment).
 * A MdbWithEJBToSendMessageToOutQueueWithJNDI used for example lodh tests. Used in RemoteJcaWithRecoverTestCase in interop test suite.
 * <p/>
 * This mdb reads messages from queue "InQueue" and sends to queue "OutQueue" using MDB.
 *
 * @author <a href="mnovak@redhat.com">Miroslav Novak</a>
 * @version $Revision: 1.1 $
 */
@MessageDriven(name = "MdbWithEJBToSendMessageToOutQueueWithJNDI",
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InQueue"),
                @ActivationConfigProperty(propertyName = "userName", propertyValue = "user"),
                @ActivationConfigProperty(propertyName = "user", propertyValue = "user"),
                @ActivationConfigProperty(propertyName = "password", propertyValue = "pass")})
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
public class MdbWithEJBToSendMessageToOutQueueWithJNDI implements MessageDrivenBean, MessageListener {

    private static final long serialVersionUID = 2770941392406343837L;

    private static final Logger log = Logger.getLogger(MdbWithEJBToSendMessageToOutQueueWithJNDI.class.getName());

    public static AtomicInteger numberOfProcessedMessages = new AtomicInteger();

    private MessageDrivenContext context;

    @EJB
    SenderEJB senderEJB;

    @Override
    public void onMessage(Message message) {

        for (int i = 0; i < (5 + 5 * Math.random()); i++) {
            try {
                Thread.sleep((int) (10 + 10 * Math.random()));
            } catch (InterruptedException ex) {
            }
        }

        try {

            int count = numberOfProcessedMessages.incrementAndGet();

            String messageInfo = message.getJMSMessageID() + ", count:" + count;

            log.debug("Processing message :" + messageInfo);

            senderEJB.sendMessage(message.getJMSMessageID(), message.getStringProperty("_HQ_DUPL_ID"));

            if (count % 100 == 0)
                log.info(messageInfo);

        } catch (Exception t) {
            log.error(t.getMessage(), t);
            this.context.setRollbackOnly();
        }
    }

    @Override
    public void setMessageDrivenContext(MessageDrivenContext ctx) throws EJBException {
        this.context = ctx;
    }

    @Override
    public void ejbRemove() throws EJBException {
    }
}