package org.jboss.qa.hornetq.apps.mdb;

import org.jboss.logging.Logger;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.*;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * A LocalMdbFromTopic used for lodh tests.
 * <p>
 * This mdb reads messages from queue "InTopic" and sends to queue "OutQueue".
 * @author mnovak
 */
@MessageDriven(name = "mdb",
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/topic/InTopic"),
                @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge"),
                @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = "mySubscription"),
                @ActivationConfigProperty(propertyName = "clientID", propertyValue = "myClientId"),
                @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable")
        })

public class LocalMdbFromTopicToQueue implements MessageListener {

    @Resource(mappedName = "java:/JmsXA")
    private ConnectionFactory cf;

    private static final long serialVersionUID = 2770941392406343837L;
    private static final Logger log = Logger.getLogger(LocalMdbFromTopicToQueue.class.getName());

    private MessageDrivenContext context;

    public void setContext(MessageDrivenContext context) {
        this.context = context;
    }

    @Override
    public void onMessage(Message message) {
        InitialContext ctx = null;
        InitialContext ctxRemote = null;
        Connection con = null;
        Session session = null;

        try {
            long time = System.currentTimeMillis();
            int counter = 0;
            try {
                counter = message.getIntProperty("counter");
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
            String messageInfo = message.getJMSMessageID() + ", count:" + counter;
            log.info("Start of message:" + messageInfo);

            con = cf.createConnection();

            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
            String text = message.getJMSMessageID() + " processed by: " + hashCode();
            Queue queue = session.createQueue("OutQueue");
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
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException e) {
                    log.fatal(e.getMessage(), e);
                }
            }
            if (ctxRemote != null) {
                try {
                    ctxRemote.close();
                } catch (NamingException e) {
                    log.fatal(e.getMessage(), e);
                }
            }
        }
    }
}