package org.jboss.qa.hornetq.apps.ejb;

import org.apache.log4j.Logger;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.*;

/**
 * Created by mnovak on 1/21/14.
 */
@Stateful
@Remote(SimpleSendEJB.class)
@TransactionAttribute(TransactionAttributeType.REQUIRED)
@TransactionManagement(value = TransactionManagementType.CONTAINER)
public class SimpleSendEJBStatefulBean implements SimpleSendEJB {

    private static final Logger log = Logger.getLogger(SimpleSendEJBStatefulBean.class.getName());

    private String outQueueName = "OutQueue";

    @Resource(mappedName = "java:/JmsXA")
    private ConnectionFactory cf;

    @Resource
    SessionContext sessionContext;

    private Queue queue = null;
    private Connection con = null;

    @Override
    public void createConnection() {
        try {
            if (con == null) {
                con = cf.createConnection();
            }
        } catch (Exception ex) {
            log.error("Connection could not be created:" + ex.getMessage(), ex);
        }
    }

    @Override
    public void closeConnection() {
        try {
            if (con != null) {
                con.close();
            }
        } catch (Exception ex) {
            log.error("Connection could not be created:" + ex.getMessage(), ex);
        }
    }

    @Override
    public void sendMessage() {

        long time = System.currentTimeMillis();
        Session session;

        try {
            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
            con.start();
            queue = session.createQueue(outQueueName);
            MessageProducer sender = session.createProducer(queue);
            TextMessage message = session.createTextMessage("Message creation time is: " + System.currentTimeMillis());
            sender.send(message);
            String messageInfo = "Sending new message with messageId: " + message.getJMSMessageID();
            log.info("End of " + messageInfo + " in " + (System.currentTimeMillis() - time) + " ms");
        } catch (Exception t) {
            log.error(t.getMessage(), t);
            this.sessionContext.setRollbackOnly();
        }
    }
}

