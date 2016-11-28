package org.jboss.qa.hornetq.apps.mdb;

import org.jboss.logging.Logger;

import javax.ejb.*;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A MdbFromQueueNotToRemoteQueue used for lodh tests.
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
                @ActivationConfigProperty(propertyName = "rebalanceConnections", propertyValue = "true"),
                @ActivationConfigProperty(propertyName = "hA", propertyValue = "true"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InQueue"),
        })
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
public class MdbFromQueueNotToRemoteQueue implements MessageDrivenBean, MessageListener {

    public static AtomicInteger globalCounter = new AtomicInteger();

    private static final long serialVersionUID = 2770941392406343837L;

    private static final Logger log = Logger.getLogger(MdbFromQueueNotToRemoteQueue.class.getName());

    private MessageDrivenContext context = null;

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

        long time = System.currentTimeMillis();
        int counter = globalCounter.incrementAndGet();

        for (int i = 0; i < (5 + 5 * Math.random()); i++) {
            try {
                Thread.sleep((int) (10 + 10 * Math.random()));
            } catch (InterruptedException ex) {
            }
        }

        try {
            log.info("End of message: " + counter + ", message info: " + message.getJMSMessageID() + " in " + (System.currentTimeMillis() - time) + " ms");
        } catch (JMSException e) {
            context.setRollbackOnly();
            e.printStackTrace();
        }

    }
}
