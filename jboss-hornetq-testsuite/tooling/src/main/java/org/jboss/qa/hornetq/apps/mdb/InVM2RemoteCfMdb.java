package org.jboss.qa.hornetq.apps.mdb;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.*;
import java.io.IOException;
import java.lang.management.ManagementFactory;

/**
 * A InVM2RemoteCfMdb
 * <p/>
 * This MDB reads messages from queue "InQueue" and sends to queue "OutQueue".
 * MDB reads messages via standard configuration (InVM) and sends messages
 * into the java:/RemoteJmsXA via NettyConnectorFactory to the local HornetQ.
 * MDB kills server with each message
 *
 * @author <a href="pslavice@jboss.com">Pavel Slavicek</a>
 * @version $Revision: 1.1 $
 */
@MessageDriven(name = "mdb",
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "InQueue")})
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
public class InVM2RemoteCfMdb implements MessageListener {

    // Logger
    private static final Logger log = Logger.getLogger(InVM2RemoteCfMdb.class.getName());

    @Resource(mappedName = "java:/RemoteJmsXA")
    private ConnectionFactory cf;

    @Resource(mappedName = "OutQueue")
    private Queue queue;

    @Resource
    private MessageDrivenContext context;

    @Override
    public void onMessage(Message message) {
        Connection con = null;
        Session session = null;
        try {
            if (log.isInfoEnabled()) {
                log.log(Level.INFO, "Start getJMSMessageID: " + message.getJMSMessageID());
            }
            con = cf.createConnection();
            con.start();
            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
            String text = message.getJMSMessageID() + " processed by: " + hashCode();
            MessageProducer sender = session.createProducer(queue);
            TextMessage newMessage = session.createTextMessage(text);
            newMessage.setStringProperty("inMessageId", message.getJMSMessageID());
            sender.send(newMessage);
            if (log.isInfoEnabled()) {
                log.log(Level.INFO, "End getJMSMessageID: " + message.getJMSMessageID());
            }
            killServer();
        } catch (Exception t) {
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
        }
    }

    private void killServer() throws IOException {
        String jvmName = ManagementFactory.getRuntimeMXBean().getName();
        int index = jvmName.indexOf('@');
        if (index < 1) {
            // part before '@' empty (index = 0) / '@' not found (index = -1)
            throw new java.lang.IllegalStateException("Cannot get pid of the process:" + jvmName);
        }
        String pid = null;
        try {
            pid = Long.toString(Long.parseLong(jvmName.substring(0, index)));
        } catch (NumberFormatException e) {
            // ignore
        }
        log.info("pid of the proccess is : " + pid);
        Runtime.getRuntime().exec("kill -9 " + pid);
    }

}