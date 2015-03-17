package org.jboss.qa.hornetq.test.soak.components;


import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jboss.ejb3.annotation.ResourceAdapter;
import org.jboss.qa.hornetq.test.soak.modules.RemoteJcaSoakModule;

import javax.annotation.PreDestroy;
import javax.ejb.*;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * A SoakMdbWithRemoteOutQueueToContaniner1 used for lodh tests. Used in RemoteJcaTestCase.
 * <p/>
 * This mdb reads messages from queue "InQueue" and sends to queue "OutQueue".
 *
 * @author <a href="pslavice@jboss.com">Pavel Slavicek</a>
 * @author <a href="mnovak@redhat.com">Miroslav Novak</a>
 * @author <a href="msvehla@redhat.com">Martin Svehla</a>
 * @version $Revision: 2.0 $
 */
@MessageDriven(name = "remote-jca-resending-bean", mappedName = "soak/jca/InQueue", activationConfig = {
    @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable"),
    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
    @ActivationConfigProperty(propertyName = "destination",
                              propertyValue = "java:/" + RemoteJcaSoakModule.JCA_IN_QUEUE_JNDI)
})
@TransactionManagement(TransactionManagementType.CONTAINER)
@TransactionAttribute(TransactionAttributeType.REQUIRED)
@ResourceAdapter("hornetq-ra")
public class RemoteJcaResendingBean implements MessageListener {

    private static final long serialVersionUID = 2770941392406343837L;

    private static final Logger LOG = Logger.getLogger(RemoteJcaResendingBean.class);

    private MessageDrivenContext context = null;

    private static String hostname = null;
    private static int port = 0;

    private static InitialContext ctx = null;

    private static InitialContext ctxRemote = null;

    private static Queue queue = null;

    private static ConnectionFactory cf = null;

    public static AtomicInteger numberOfProcessedMessages = new AtomicInteger();


    static {
        try {
            Properties prop = new Properties();
            prop.load(RemoteJcaResendingBean.class.getResourceAsStream("/remote-jca-resending-bean.properties"));

            hostname = prop.getProperty("remote-jms-server");
            port = Integer.valueOf(prop.getProperty("remote-jms-jndi-port"));
            LOG.info("Hostname of remote jms server is: " + hostname + ", port: " + port);

            final Properties env = new Properties();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
            env.put(Context.PROVIDER_URL, "remote://" + hostname + ":" + port);

            ctxRemote = new InitialContext(env);
            queue = (Queue) ctxRemote.lookup(RemoteJcaSoakModule.JCA_OUT_QUEUE_JNDI);
            ctx = new InitialContext();
            // i want connection factory configured here
            cf = (ConnectionFactory) ctx.lookup("java:/JmsXA");
        } catch (Exception ex) {
            LOG.error("MDB initialisation failed", ex);
            ex.printStackTrace();
        }
    }


    @PreDestroy
    public void closeContexts() {
        if (ctxRemote != null) {
            try {
                ctxRemote.close();
            } catch (NamingException e) {
                LOG.log(Level.FATAL, e.getMessage(), e);
            }
        }
        if (ctx != null) {
            try {
                ctx.close();
            } catch (NamingException e) {
                LOG.log(Level.FATAL, e.getMessage(), e);
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
                LOG.log(Level.ERROR, e.getMessage(), e);
            }

            String messageInfo = message.getJMSMessageID() + ", count:" + counter;

            con = cf.createConnection();

            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

            con.start();

            String text = message.getJMSMessageID() + " processed by: " + hashCode();
            MessageProducer sender = session.createProducer(queue);
            TextMessage newMessage = session.createTextMessage(text);
            newMessage.setStringProperty("inMessageId", message.getJMSMessageID());
            sender.send(newMessage);

            if (numberOfProcessedMessages.incrementAndGet() % 1000 == 0) {
                LOG.info(" End of " + messageInfo + " in " + (System.currentTimeMillis() - time) + " ms");
            }

        } catch (Exception e) {

            e.printStackTrace();
            LOG.log(Level.FATAL, e.getMessage(), e);

            this.context.setRollbackOnly();
        } finally {
            if (session != null) {
                try {
                    session.close();
                } catch (JMSException e) {
                    LOG.log(Level.FATAL, e.getMessage(), e);
                }
            }
            if (con != null) {
                try {
                    con.close();
                } catch (JMSException e) {
                    LOG.log(Level.FATAL, e.getMessage(), e);
                }
            }


        }
    }

}