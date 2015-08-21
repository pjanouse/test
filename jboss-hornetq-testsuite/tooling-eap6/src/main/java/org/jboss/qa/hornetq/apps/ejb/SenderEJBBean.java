package org.jboss.qa.hornetq.apps.ejb;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.impl.HornetqJMSImplementation;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Created by mnovak on 1/21/14.
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
@TransactionManagement(value = TransactionManagementType.CONTAINER)
public class SenderEJBBean implements SenderEJB {

    private static final Logger log = Logger.getLogger(SenderEJBBean.class.getName());
    private static final JMSImplementation jmsImplementation = HornetqJMSImplementation.getInstance();

    public static String EJB_PROPERTY_FILE = "mdb.properties";
    public static String REMOTE_SERVER_HOSTNAME = "remote-server-hostname";
    public static String REMOTE_SERVER_PORT = "remote-server-port";
    public static String REMOTE_SERVER_TYPE = "remote-server-type";
    public static String OUTQUEUE_JNDI_NAME = "outqeue-jndi-name";

    @Resource(mappedName = "java:/JmsXA")
    private ConnectionFactory cf;

    @Resource
    SessionContext sessionContext;

    private Queue queue = null;

    @Override
    public void sendMessage(String inMessageId, String dupId) {

        long time = System.currentTimeMillis();

        if (queue == null) {
            makeLookups();
        }
        Connection con = null;
        Session session;

        String duplicatedHeader = jmsImplementation.getDuplicatedHeader();

        try {

            con = cf.createConnection("user", "pass");

            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

            con.start();

            if (queue == null) {
                makeLookups();
            }

            MessageProducer sender = session.createProducer(queue);
            TextMessage newMessage = session.createTextMessage();
            if (inMessageId != null) {
                newMessage.setStringProperty("inMessageId", inMessageId);
            }
            if (dupId != null)  {
                newMessage.setStringProperty(duplicatedHeader, dupId);
            }

            sender.send(newMessage);

            String messageInfo = "Sending new message with inMessageId: "
                    + (newMessage.getStringProperty("inMessageId") != null ?  newMessage.getStringProperty("inMessageId") : "")
                    + " and messageId: " + newMessage.getJMSMessageID();

            log.debug("End of " + messageInfo + " in " + (System.currentTimeMillis() - time) + " ms");
        } catch (Exception t) {
            log.error(t.getMessage(), t);
            this.sessionContext.setRollbackOnly();
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

    /**
     * Lookup out queue
     *
     * @throws EJBException
     */
    private void makeLookups() throws EJBException {

        Properties prop = new Properties();

        Context ctxRemote = null;

        try {
            // load mdb.properties - by this classloader
            Thread currentThred = Thread.currentThread();
            ClassLoader cl = currentThred.getContextClassLoader();
            InputStream in = cl.getResourceAsStream(EJB_PROPERTY_FILE);

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

                System.out.println("Property name:" + REMOTE_SERVER_HOSTNAME + " has value: " + hostname);
                System.out.println("Property name:" + REMOTE_SERVER_PORT + " has value: " + port);
                System.out.println("Property name:" + REMOTE_SERVER_TYPE + " has value: " + serverType);
                System.out.println("Property name:" + OUTQUEUE_JNDI_NAME + " has value: " + outQueueJndiName);

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
}
