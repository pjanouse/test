package org.jboss.qa.hornetq.apps.mdb;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.*;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * A LocalMdbFromTopic used for lodh tests.
 * <p/>
 * This mdb reads messages from queue "InTopic" and sends to queue "OutTopic". This mdb is used
 * in ClusterTestCase. Don't change it!!!
 *
 * @author okalman
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

public class LocalMdbFromTopicToTopic implements MessageListener {

    @Resource(mappedName = "java:/JmsXA")
    private TopicConnectionFactory cf;

    @Resource(mappedName = "java:/jms/topic/OutTopic")
    private Topic topic;


    //    @Resource(name = "queue/OutQueue")
    //    private static Queue queue;

    private static final long serialVersionUID = 2770941392406343837L;
    private static final Logger log = Logger.getLogger(LocalMdbFromTopicToTopic.class.getName());
    private MessageDrivenContext context = null;

    public LocalMdbFromTopicToTopic() {
        super();
    }

//    @Override
//    public void setMessageDrivenContext(MessageDrivenContext ctx) {
//        this.context = ctx;
//    }
//
//    public void ejbCreate() {
//    }
//
//    @Override
//    public void ejbRemove() {
//    }

    @Override
    public void onMessage(Message message) {
        InitialContext ctx = null;
        InitialContext ctxRemote = null;
        TopicConnection con = null;
        TopicSession session = null;

        try {
            long time = System.currentTimeMillis();
            int counter = 0;
            try {
                counter = message.getIntProperty("counter");
            } catch (Exception e) {
                log.log(Level.ERROR, e.getMessage(), e);
            }
            String messageInfo = message.getJMSMessageID() + ", count:" + counter;
            log.log(Level.INFO, " Start of message:" + messageInfo);

            con = cf.createTopicConnection();

            session = con.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
            String text = message.getJMSMessageID() + " processed by: " + hashCode();
            MessageProducer sender = session.createPublisher(topic);
            TextMessage newMessage = session.createTextMessage(text);
            newMessage.setStringProperty("inMessageId", message.getJMSMessageID());
            sender.send(newMessage);

            log.log(Level.INFO, " End of " + messageInfo + " in " + (System.currentTimeMillis() - time) + " ms");

        } catch (Exception t) {
            t.printStackTrace();
            log.log(Level.FATAL, t.getMessage(), t);
            this.context.setRollbackOnly();

        } finally {
            if (session != null) {
                try {
                    session.close();
                } catch (JMSException e) {
                    log.log(Level.FATAL, e.getMessage(), e);
                }
            }
            if (con != null) {
                try {
                    con.close();
                } catch (JMSException e) {
                    log.log(Level.FATAL, e.getMessage(), e);
                }
            }
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException e) {
                    log.log(Level.FATAL, e.getMessage(), e);
                }
            }
            if (ctxRemote != null) {
                try {
                    ctxRemote.close();
                } catch (NamingException e) {
                    log.log(Level.FATAL, e.getMessage(), e);
                }
            }
        }
    }
}