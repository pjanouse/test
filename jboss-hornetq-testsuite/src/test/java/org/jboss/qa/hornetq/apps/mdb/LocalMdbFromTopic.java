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
     * This mdb reads messages from queue "InQueue" and sends to queue "OutQueue". This mdb is used
     * in ClusterTestCase. Don't change it!!!
     *
     * @author <a href="pslavice@jboss.com">Pavel Slavicek</a>
     * @author <a href="mnovak@redhat.com">Miroslav Novak</a>
     * @version $Revision: 1.1 $
     */
    @MessageDriven(name = "mdb",
            activationConfig = {
                    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
                    @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InTopic"),
                    @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = "mySubscription"),
                    @ActivationConfigProperty(propertyName = "clientID", propertyValue = "myClientId"),
                    @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable")
            })
    @TransactionManagement(value = TransactionManagementType.CONTAINER)
    @TransactionAttribute(value = TransactionAttributeType.REQUIRED)
    public class LocalMdbFromTopic implements MessageDrivenBean, MessageListener {

        @Resource(mappedName = "java:/JmsXA")
        private ConnectionFactory cf;

        @Resource(name = "java:/jms/queue/OutQueue")
        private Queue queue;

    //    @Resource(name = "queue/OutQueue")
    //    private static Queue queue;

        private static final long serialVersionUID = 2770941392406343837L;
        private static final Logger log = Logger.getLogger(LocalMdbFromTopic.class.getName());
        private MessageDrivenContext context = null;

        public LocalMdbFromTopic() {
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
                    log.log(Level.ERROR, e.getMessage(), e);
                }
                String messageInfo = message.getJMSMessageID() + ", count:" + counter;
                log.log(Level.INFO, " Start of message:" + messageInfo);

                con = cf.createConnection();

                con.start();

                session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

                String text = message.getJMSMessageID() + " processed by: " + hashCode();
                MessageProducer sender = session.createProducer(queue);
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