package org.jboss.qa.hornetq.apps.mdb;

import org.jboss.logging.Logger;

import javax.ejb.*;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A SoakMdbWithRemoteOutQueueToContaniner2 used for lodh tests. Used in RemoteJcaTestCase.
 * <p/>
 * This mdb reads messages from queue "InQueue" and sends to queue "OutQueue".
 *
 * @author <a href="pslavice@jboss.com">Pavel Slavicek</a>
 * @author <a href="mnovak@redhat.com">Miroslav Novak</a>
 * @version $Revision: 1.1 $
 */
@MessageDriven(name = "mdb",
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InQueue")})
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
public class SoakMdbWithRemoteOutQueueToContaniner2 implements MessageDrivenBean, MessageListener {

    private static final long serialVersionUID = 2770941392406343837L;
    private static final Logger log = Logger.getLogger(SoakMdbWithRemoteOutQueueToContaniner2.class.getName());
    private MessageDrivenContext context = null;
    private static String hostname;
    private static int port;
    private static InitialContext ctx = null;
    private static InitialContext ctxRemote = null;
    private static Queue queue = null;
    private static ConnectionFactory cf = null;

    public static AtomicInteger numberOfProcessedMessages= new AtomicInteger();

    static {
        try {
            Properties prop = new Properties();
            File propFile = new File(System.getProperty("jboss.home.dir") + File.separator + "mdb2.properties");
            log.info("Location of property file (mdb2) is: " + propFile.getAbsolutePath());
            prop.load(new FileInputStream(propFile));
            hostname = prop.getProperty("remote-jms-server");
            port = Integer.valueOf(prop.getProperty("remote-jms-jndi-port"));
            log.info("Hostname of remote jms server is: " + hostname);
            final Properties env = new Properties();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
            env.put(Context.PROVIDER_URL, "remote://" + hostname + ":" + port);
            ctxRemote = new InitialContext(env);
            queue = (Queue) ctxRemote.lookup("jms/queue/OutQueue");
            ctx = new InitialContext();
            // i want connection factory configured here
            cf = (ConnectionFactory) ctx.lookup("java:/JmsXA");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public SoakMdbWithRemoteOutQueueToContaniner2() {
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
        if (ctxRemote != null) {
            try {
                ctxRemote.close();
            } catch (NamingException e) {
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
                log.error(e.getMessage(), e);
            }
            String messageInfo = message.getJMSMessageID() + ", count:" + counter;
//            log.log(Level.INFO, " Start of message:" + messageInfo);

            for (int i = 0; i < (5 + 5 * Math.random()); i++) {
                try {
                    Thread.sleep((int) (10 + 10 * Math.random()));
                } catch (InterruptedException ex) {
                }
            }

            con = cf.createConnection();

            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

            con.start();

            String text = message.getJMSMessageID() + " processed by: " + hashCode();
            MessageProducer sender = session.createProducer(queue);
            TextMessage newMessage = session.createTextMessage(text);
            newMessage.setStringProperty("inMessageId", message.getJMSMessageID());
            sender.send(newMessage);

            if (numberOfProcessedMessages.incrementAndGet() % 1000 == 0) {
                log.info(" End of " + messageInfo + " in " + (System.currentTimeMillis() - time) + " ms");
            }

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


        }
    }
}