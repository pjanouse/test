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
public class SimpleSendEJBStatelessBean implements SimpleSendEJB {

    private static final Logger log = Logger.getLogger(SimpleSendEJBStatelessBean.class.getName());

    private String outQueueName = "OutQueue";

    @Resource(mappedName = "java:/JmsXA")
    private ConnectionFactory cf;

    @Resource
    SessionContext sessionContext;

    private Queue queue = null;
    private Connection con = null;

    @Override
    public void createConnection() {

        log.info("Create connection called - doing nothing here. Call send message instead.");

    }

    @Override
    public void closeConnection() {
        log.info("Close connection called - doing nothing here. Call send message instead.");
    }

    @Override
    public void sendMessage() {

        long time = System.currentTimeMillis();

        Connection con = null;
        Session session;

        try {
            con = cf.createConnection();
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
        } finally {
            if (con != null)    {
                try {
                    con.close();
                } catch (JMSException e) {
                    // ignore
                }
            }
        }
    }
    
    @Override
    public int sendCount(){
        return -1;
    }
}


