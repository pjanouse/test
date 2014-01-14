package org.jboss.qa.hornetq.apps.mdb;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.HornetQTestCaseConstants;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.InputStream;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This mdb expects mdb.properties in jar file which can be loaded during runtime(deployment).
 * A MdbWithRemoteOutQueueWithJNDI used for example lodh tests. Used in RemoteJcaWithRecoverTestCase in interop test suite.
 * <p/>
 * This mdb reads messages from queue "InQueue" and sends to queue "OutQueue".
 *
 * @author <a href="mnovak@redhat.com">Miroslav Novak</a>
 * @version $Revision: 1.1 $
 */
@MessageDriven(name = "MdbWithRemoteOutQueueWithJNDI",
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InQueue"),
                @ActivationConfigProperty(propertyName = "userName", propertyValue = "user"),
                @ActivationConfigProperty(propertyName = "user", propertyValue = "user"),
                @ActivationConfigProperty(propertyName = "password", propertyValue = "pass")})
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
public class MdbWithRemoteOutQueueWithJNDI implements MessageDrivenBean, MessageListener {

    public static String MDB_PROPERTY_FILE = "mdb.properties";
    public static String REMOTE_SERVER_HOSTNAME = "remote-server-hostname";
    public static String REMOTE_SERVER_PORT = "remote-server-port";
    public static String REMOTE_SERVER_TYPE = "remote-server-type";
    public static String OUTQUEUE_JNDI_NAME = "outqeue-jndi-name";

    private static final long serialVersionUID = 2770941392406343837L;
    private static final Logger log = Logger.getLogger(MdbWithRemoteOutQueueWithJNDI.class.getName());
    private Queue queue = null;
    public static AtomicInteger numberOfProcessedMessages = new AtomicInteger();

    @Resource(mappedName = "java:/JmsXA")
    private ConnectionFactory cf;

    @Resource
    private MessageDrivenContext context;

    @Override
    public void onMessage(Message message) {

        Connection con = null;
        Session session;

        try {

            long time = System.currentTimeMillis();
            int counter = 0;
            try {
                counter = message.getIntProperty("count");
            } catch (Exception e) {
                log.log(Level.ERROR, e.getMessage(), e);
            }

            String messageInfo = message.getJMSMessageID() + ", count:" + counter;

            log.debug(" Start of message:" + messageInfo);

            for (int i = 0; i < (5 + 5 * Math.random()); i++) {
                try {
                    Thread.sleep((int) (10 + 10 * Math.random()));
                } catch (InterruptedException ex) {
                }
            }

            con = cf.createConnection("user", "pass");

            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

            con.start();

            String text = message.getJMSMessageID() + " processed by: " + hashCode();
            MessageProducer sender = session.createProducer(queue);
            TextMessage newMessage = session.createTextMessage(text);
            newMessage.setStringProperty("inMessageId", message.getJMSMessageID());
            newMessage.setStringProperty("_HQ_DUPL_ID", message.getStringProperty("_HQ_DUPL_ID"));
            sender.send(newMessage);

            messageInfo = messageInfo + ". Sending new message with inMessageId: " + newMessage.getStringProperty("inMessageId")
                    + " and messageId: " + newMessage.getJMSMessageID();

            log.debug("End of " + messageInfo + " in " + (System.currentTimeMillis() - time) + " ms");

            if (numberOfProcessedMessages.incrementAndGet() % 100 == 0)
                log.info(messageInfo + " in " + (System.currentTimeMillis() - time) + " ms");

        } catch (Exception t) {
            log.error(t.getMessage(), t);
            this.context.setRollbackOnly();
        } finally {
            if (con != null) {
                try {
                    con.close();
                } catch (JMSException e) {
                    log.log(Level.FATAL, e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public void setMessageDrivenContext(MessageDrivenContext ctx) throws EJBException {

        Properties prop = new Properties();

        Context ctxRemote = null;

        try {
            // load mdb.properties - by this classloader

            Thread currentThred = Thread.currentThread();
            ClassLoader cl = currentThred.getContextClassLoader();
            InputStream in = cl.getResourceAsStream(MDB_PROPERTY_FILE);

            if (in == null) {
                System.out.println("No resource found. InputStream is null.!!!!");
                log.info("No resource found. InputStream is null.!!!!");
            } else {
                prop.load(in);

                String hostname = prop.getProperty(REMOTE_SERVER_HOSTNAME);
                String port = prop.getProperty(REMOTE_SERVER_PORT);
                String serverType = prop.getProperty(REMOTE_SERVER_TYPE); // EAP 5, 6, ...
                String outQueueJndiName = prop.getProperty(OUTQUEUE_JNDI_NAME);

                log.info("Property name:" + REMOTE_SERVER_HOSTNAME + " has value: " + hostname);
                log.info("Property name:" + REMOTE_SERVER_PORT + " has value: " + port);
                log.info("Property name:" + REMOTE_SERVER_TYPE + " has value: " + serverType);
                log.info("Property name:" + OUTQUEUE_JNDI_NAME + " has value: " + outQueueJndiName);

                final Properties env = new Properties();

//                if (HornetQTestCaseConstants.EAP5_CONTAINER.equals(serverType)) { // it's eap 5
                env.setProperty("java.naming.factory.initial", "org.jnp.interfaces.NamingContextFactory");
                env.setProperty("java.naming.provider.url", "jnp://" + hostname + ":" + port);
                env.setProperty("java.naming.factory.url.pkgs", "org.jnp.interfaces.NamingContextFactory");
//                } else { // it's EAP 6
//                    env.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
//                    env.put(Context.PROVIDER_URL, "remote://" + hostname + ":" + port);
//                }
                ctxRemote = new InitialContext(env);
                queue = (Queue) ctxRemote.lookup(outQueueJndiName);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (ctxRemote != null) {
                try {
                    ctxRemote.close();
                } catch (NamingException e) {
                    //ignore
                }
            }
        }
    }

    @Override
    public void ejbRemove() throws EJBException {
    }
}