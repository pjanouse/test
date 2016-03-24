//package org.jboss.qa.hornetq.apps.mdb;
//
//import org.apache.log4j.Level;
//import org.apache.log4j.Logger;
//import org.jboss.ejb3.annotation.Depends;
//
//import javax.ejb.*;
//import javax.jms.*;
//import javax.naming.InitialContext;
//import javax.naming.NamingException;
//
//
///**
// * A LocalMdbFromTopicDurable used for lodh tests.
// * <p/>
// *
// * @author <a href="mnovak@redhat.com">Miroslav Novak</a>
// * @version $Revision: 1.1 $
// */
//@MessageDriven(name = "mdb",
//        activationConfig = {
//                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
//                @ActivationConfigProperty(propertyName = "destination", propertyValue = "topic/InTopic"),
//                @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable"),
//                @ActivationConfigProperty(propertyName = "clientId", propertyValue = "topicId1"),
//                @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = "subscriber1"),
//                @ActivationConfigProperty(propertyName = "useDLQ", propertyValue = "false"),
//                @ActivationConfigProperty(propertyName="reconnectAttempts", propertyValue="500"),
//                @ActivationConfigProperty(propertyName="reconnectInterval", propertyValue="10"),
//                @ActivationConfigProperty(propertyName="DLQMaxResent", propertyValue="10")
//        })
//@TransactionManagement(value = TransactionManagementType.CONTAINER)
//@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
//@Depends({"jboss.messaging.destination:service=Queue,name=OutQueue"})
//public class LocalMdbFromTopicDurable implements MessageDrivenBean, MessageListener {
//
////    @Resource(mappedName = "java:/JmsXA")
////    private ConnectionFactory cf;
//
//
////    @Resource(name = "queue/OutQueue")
////    private static Queue queue;
//
//    private static final long serialVersionUID = 2770941392406343837L;
//    private static final Logger log = Logger.getLogger(LocalMdbFromTopicDurable.class.getName());
////    private MessageDrivenContext context = null;
//
//    public LocalMdbFromTopicDurable() {
//        super();
//    }
//
//    @Override
//    public void setMessageDrivenContext(MessageDrivenContext ctx) {
////        this.context = ctx;
//    }
//
//    public void ejbCreate() {
//    }
//
//    @Override
//    public void ejbRemove() {
//    }
//
//    @Override
//    public void onMessage(Message message) {
//        InitialContext ctxRemote = null;
//        Connection con = null;
//        Session session = null;
//        ConnectionFactory cf = null;
//
//        try {
//            long time = System.currentTimeMillis();
//            int counter = 0;
//            try {
//                counter = message.getIntProperty("counter");
//            } catch (Exception e) {
//                log.log(Level.ERROR, e.getMessage(), e);
//            }
//            String messageInfo = message.getJMSMessageID() + ", count:" + counter;
//            log.log(Level.INFO, " Start of message:" + messageInfo);
//
//            ctxRemote = new InitialContext();
//
//            cf = (ConnectionFactory) ctxRemote.lookup("java:/JmsXA");
//
//            con = cf.createConnection();
//
//            con.start();
//
//            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
//
//            String text = message.getJMSMessageID() + " processed by: " + hashCode();
//
//            Queue queue = (Queue) ctxRemote.lookup("queue/OutQueue");
//            MessageProducer sender = session.createProducer(queue);
//            TextMessage newMessage = session.createTextMessage(text);
//            newMessage.setStringProperty("inMessageId", message.getJMSMessageID());
//            sender.send(newMessage);
//
//            log.log(Level.INFO, " End of " + messageInfo + " in " + (System.currentTimeMillis() - time) + " ms");
//
//        } catch (Exception t) {
//            t.printStackTrace();
//            log.log(Level.FATAL, t.getMessage(), t);
////            this.context.setRollbackOnly();
//
//        } finally {
//            if (session != null) {
//                try {
//                    session.close();
//                } catch (JMSException e) {
//                    log.log(Level.FATAL, e.getMessage(), e);
//                }
//            }
//            if (con != null) {
//                try {
//                    con.close();
//                } catch (JMSException e) {
//                    log.log(Level.FATAL, e.getMessage(), e);
//                }
//            }
////            if (ctx != null) {
////                try {
////                    ctx.close();
////                } catch (NamingException e) {
////                    log.log(Level.FATAL, e.getMessage(), e);
////                }
////            }
//            if (ctxRemote != null) {
//                try {
//                    ctxRemote.close();
//                } catch (NamingException e) {
//                    log.log(Level.FATAL, e.getMessage(), e);
//                }
//            }
//        }
//    }
//}